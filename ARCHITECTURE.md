# Architecture — WiiGames WebSocket Bridge

## Visión general

Puente WebSocket entre **mandos móviles** (navegador web) y **Unity**. El servidor Node.js actúa como enrutador neutral: asigna identidades, inyecta `playerId` y reenvía mensajes sin interpretar lógica de juego.

```
Móvil J1 ──┐                        ┌── Unity (receptor)
            ├── ws://host:3000 ──── ─┤
Móvil J2 ──┘   (Node.js server)     └── (envía eventos de vuelta)
```

---

## Archivos

| Archivo | Rol |
|---|---|
| `server.js` | Servidor HTTP + WebSocket. Router de mensajes, dueño de `playerId` |
| `index.html` | Mando web. D-pad, botones A/B, giroscopio, micrófono |
| `package.json` | Configuración del proyecto. `"type": "module"` (ES Modules) |

> **Nota:** `package.json` no declara script `start`. `npm start` funciona porque npm usa `node server.js` como fallback cuando el archivo existe. Si se renombra el servidor, añadir `"start": "node server.js"` en `scripts`.

> **Nota:** Las dependencias `express` y `socket.io` están instaladas pero no se usan. Se puede limpiar con `pnpm remove express socket.io`.

---

## Cómo ejecutar

```bash
cd testing
npm start          # node server.js
```

El servidor escucha en `0.0.0.0:3000`:
- HTTP → sirve `index.html` al móvil
- WebSocket → `ws://<ip-local>:3000`

---

## Topología de conexiones

```
[Móvil J1]  --join-->          [Servidor Node]  <--join_unity--  [Unity]
            <--assignRole--            |
                                       |
[Móvil J2]  --join-->                  |
            <--assignRole--            |
                                       |
[Móvil J1]  --type:input-->  inyecta playerId  ---> [Unity]
[Móvil J2]  --type:input-->  inyecta playerId  ---> [Unity]

[Unity] --collision/death/pickup...+playerId--> enruta --> [Móvil correcto]
```

---

## Estado del servidor

```js
let players      = { 1: null, 2: null }  // WebSocket de cada móvil
let unitySocket  = null                  // WebSocket de Unity
```

El servidor es el único dueño de `playerId`. Los clientes móviles **nunca** envían su propio `playerId`; el servidor lo inyecta al reenviar.

---

## Contratos JSON

### Handshake de conexión

| Dirección | Mensaje | Descripción |
|---|---|---|
| Móvil → Servidor | `{ "type": "join" }` | Pide slot de jugador |
| Servidor → Móvil | `{ "type": "assignRole", "playerId": 1 }` | Confirma rol |
| Servidor → Móvil | `{ "type": "error", "message": "Sala llena" }` | Sin slots libres |
| Unity → Servidor | `{ "type": "join_unity" }` | Se registra como receptor |
| Servidor → Unity | `{ "type": "unity_ready" }` | Confirma registro |

### Móvil → Servidor → Unity

#### `type: "input"` — enviado a ~20 fps por los sensores, y en cada evento de botón/dpad

```json
{
  "type": "input",
  "playerId": 1,
  "gamma": -0.75,
  "beta": 0.30,
  "dpadX": 1,
  "dpadY": 0,
  "btnA": false,
  "btnB": false,
  "isYelling": false
}
```

| Campo | Tipo | Rango | Descripción |
|---|---|---|---|
| `gamma` | float | -1.0 a 1.0 | Inclinación izq/der (eje X). Normalizado desde ±40° |
| `beta` | float | -1.0 a 1.0 | Inclinación adelante/atrás. Normalizado desde ±40° |
| `dpadX` | int | -1, 0, 1 | D-pad horizontal |
| `dpadY` | int | -1, 0, 1 | D-pad vertical |
| `btnA` | bool | — | Salto / acción principal |
| `btnB` | bool | — | Acción secundaria / agacharse |
| `isYelling` | bool | — | Micrófono supera umbral de 100/255 |

> `playerId` es **inyectado por el servidor**, nunca enviado por el móvil.

#### `type: "puzzle_action"` — enviado al resolver un puzzle

```json
{
  "type": "puzzle_action",
  "playerId": 1,
  "puzzleId": "door_002",
  "status": "solved"
}
```

### Unity → Servidor → Móvil

Unity siempre incluye `playerId` para que el servidor enrute al móvil correcto.

#### `type: "collision"`
```json
{ "type": "collision", "playerId": 1, "objectName": "Spike" }
```
Feedback: flash rojo 150ms + vibración 200ms.

#### `type: "death"`
```json
{ "type": "death", "playerId": 1 }
```
Feedback: flash rojo 400ms + vibración `[200, 100, 200]`.

#### `type: "pickup"`
```json
{ "type": "pickup", "playerId": 1, "item": "Coin" }
```
Feedback: flash verde 150ms + vibración 80ms.

#### `type: "ui_update"`
```json
{ "type": "ui_update", "playerId": 1, "text": "Vidas: 2" }
```
Muestra `text` en el log del mando.

#### `type: "screen_effect"`
```json
{ "type": "screen_effect", "playerId": 1, "color": "#0000ff", "duration": 300, "vibrate": true }
```
Flash del color indicado. `vibrate` opcional.

#### `type: "puzzle_start"`
```json
{ "type": "puzzle_start", "playerId": 1, "puzzleId": "door_002" }
```
Muestra ID del puzzle en el log + vibración corta.

#### `type: "custom"`
```json
{ "type": "custom", "playerId": 1, "message": "Texto libre" }
```
Muestra `message` en el log. Para eventos no catalogados.

---

## Cliente móvil — `index.html`

### Estado centralizado de input

```js
const currentInput = {
  gamma: 0, beta: 0,     // sensores (gyroscopio)
  dpadX: 0, dpadY: 0,    // d-pad físico
  btnA: false,           // botón A — salto
  btnB: false,           // botón B — acción secundaria
}
// isYelling viene del micrófono, se lee en el momento de enviar
```

Cualquier evento (sensor, dpad, botón) actualiza `currentInput` y llama a `sendCurrentInput()`, que construye el JSON `type: "input"` completo.

### Funciones principales

| Función | Descripción |
|---|---|
| `sendCurrentInput()` | Serializa `currentInput + isYelling` y lo envía |
| `sendPuzzleAction(puzzleId, status)` | Envía `type: "puzzle_action"` |
| `iniciarSensores()` | Registra `deviceorientation`, normaliza gamma/beta |
| `iniciarMicrofono()` | Inicia `AudioContext`, bucle `medirVolumen()` |
| `medirVolumen()` | RAF loop — actualiza `isYelling` y barra visual |
| `flashScreen(color, ms)` | Feedback visual de eventos de Unity |
| `vibrate(pattern)` | Wrapper de `navigator.vibrate` |
| `connect()` | Conecta WebSocket, reconecta cada 2s si cae |

### Flujo de activación de sensores

Los navegadores modernos (especialmente iOS 13+) requieren un gesto del usuario para acceder a `DeviceOrientationEvent` y `getUserMedia`. El botón **"🎮 Activar Sensores para Jugar"** cubre ambos permisos a la vez.

> En iOS el servidor debe servir por **HTTPS** para que `DeviceOrientationEvent.requestPermission()` funcione. En HTTP solo funciona en Android y PC.

---

## Unity — integración esperada

1. Conectar al WebSocket: `ws://<ip-servidor>:3000`
2. Al abrir conexión, enviar inmediatamente:
   ```json
   { "type": "join_unity" }
   ```
3. Esperar respuesta `{ "type": "unity_ready" }` antes de procesar mensajes.
4. Recibir mensajes `type: "input"` y `type: "puzzle_action"` con `playerId` ya inyectado.
5. Para enviar feedback al móvil, incluir siempre `playerId` en el JSON de salida.

---

## Decisiones de diseño

- **El servidor es dueño de `playerId`** — el móvil nunca lo declara, evitando suplantación entre mandos.
- **`isYelling` se lee en el cliente en el momento de enviar** — no es un estado persistente que Unity gestione.
- **`btnA` = salto, `btnB` = acción secundaria** — la semántica concreta la define Unity, no el mando.
- **`beta` es contextual** — puede usarse para salto alternativo, agacharse u otro evento según el juego.
- **Un solo WebSocket compartido con HTTP** — evita problemas de CORS y simplifica la conexión desde móviles en red local.
- **Reconexión automática cada 2s** — si el servidor cae, el móvil reintenta sin intervención del usuario.

---

## Features implementadas

### Conexión Unity → Servidor

`WebScoketClient.cs` envía `{ "type": "join_unity" }` automáticamente tras conectarse. El servidor responde `{ "type": "unity_ready" }` para confirmar. Sin este handshake el servidor ignora a Unity y descarta los inputs del móvil.

```csharp
// WebScoketClient.cs — flujo Start()
1. Crear WebSocket
2. await Connect()   // nunca retorna (bucle Receive interno)
3. OnOpen → enviar join_unity via await websocket.SendText()
```

> 📌 `Connect()` no retorna porque internamente lanza `Receive()` (bucle infinito).  
> 📌 Por eso `join_unity` se envía desde `OnOpen`, no después de `Connect()`.

### Super salto (yelling)

Cuando el móvil activa el micrófono y grita (`isYelling: true`), el jugador en Unity ejecuta un super salto automático sin necesidad de presionar btnA.

| Archivo | Cambio |
|---|---|
| `PlayerMovements.cs` | Campo `superJumpHeight`, flag `externalSuperJump` |
| `PlayerMovements.cs` | `RequestExternalSuperJump()` — llamado desde `GameEventManager` |
| `PlayerMovements.cs` | `jumpRequested = externalJump \|\| externalSuperJump` — el grito solo dispara el salto |
| `GameEventManager.cs` | `HandleInput()` llama `player.RequestExternalSuperJump()` en vez del evento vacío |

**En el Inspector**: `Super Jump Height` (default 5) independiente de `Jump Height` (default 1.5).

### Sistema de monedas

`Coin.cs` se coloca en objetos con `Collider2D` (isTrigger) y tag `Coin`. Al colisionar con un jugador:

```csharp
Coin.OnTriggerEnter2D()
  → GameEventManager.AddScore(playerId, points)
  → ReportPickup(playerObj, "coin")       // móvil: ✨ + flash verde + vibrar
  → ReportUIUpdate(playerObj, "coin",      // móvil: 🖥️ "Conseguiste una moneda! Te faltan X"
      $"Conseguiste una moneda! Te faltan {missing}")
  → Destroy(gameObject)
```

| Elemento | Dónde |
|---|---|
| Score por jugador | `GameEventManager.playerScores` (`Dictionary<int,int>`) |
| Meta de monedas | `GameEventManager.TargetCoins` (default 10, ajustable en Inspector) |
| Puntos por moneda | `Coin.points` (default 1, ajustable en Inspector) |

### Animaciones (Berie)

`PlayerMovements.cs` controla el Animator sin parámetros — usa `animator.Play("NombreEstado")` directamente:

| Estado | Tipo | Cuándo se activa |
|---|---|---|
| `Idle` | loop | En suelo sin movimiento horizontal |
| `Run` | loop | En suelo con movimiento horizontal |
| `Jump` | one-shot | Al saltar (cualquier tipo) |
| `Fall` | one-shot | Al caer (velocity.y < -0.1, no en suelo) |
| `Hit` | one-shot | Colisión con Enemy/Trap |
| `Death` | one-shot | Llamando a `PlayDeath()` externamente |
| `Attack 1` | one-shot | Botón B en el móvil |
| `Attack 2` | one-shot | Llamando a `PlayAttack2()` externamente |
| `Show Off` | one-shot | Llamando a `PlayShowOff()` externamente |

Los one-shots se reproducen completos (normalizedTime < 1.0) antes de volver a Idle/Run. El Animator se busca automáticamente con `GetComponentInChildren<Animator>()`.

### Acciones conectadas

| Evento | Acción en Unity |
|---|---|
| `btnA` | `PlayerMovements.RequestExternalJump()` |
| `btnB` | `PlayerMovements.PlayAttack1()` + mantiene evento `OnPlayerSecondaryAction` |
| `isYelling` | `PlayerMovements.RequestExternalSuperJump()` |
| Colisión con Enemy/Trap | `PlayerMovements.PlayHit()` + `ReportCollision()` al móvil |

### Vibración mínima en móvil (Chrome)

En `index.html`, todas las llamadas a `navigator.vibrate()` usan mínimo **1001ms** porque Chrome en móvil ignora vibraciones cortas:

| Evento | Patrón |
|---|---|
| collision | 1001 |
| death | [500, 100, 401] |
| pickup | 1001 |
| puzzle_start | 1001 |
| screen_effect | Math.max(data.duration, 1001) |

### Logs coloreados en Unity

`WebScoketClient.cs` usa etiquetas `<color=#...>` HTML para facilitar la depuración:

| Color | Propósito |
|---|---|
| <span style="color:#00aaff">Azul</span> | Inicio de conexión |
| <span style="color:#00ff88">Verde</span> | Conexión exitosa / mensaje enviado |
| <span style="color:#ffff00">Amarillo</span> | Acción en progreso |
| <span style="color:#ff8844">Naranja</span> | Desconexión |
| <span style="color:#ff4444">Rojo</span> | Error |

### Archivos Unity

| Archivo | Rol |
|---|---|
| `Assets/Scripts/WebScoketClient.cs` | Conexión WebSocket + envío de `join_unity` |
| `Assets/Scripts/GameEventManager.cs` | Enrutamiento de input, score, eventos salientes |
| `Assets/Scripts/Coin.cs` | Detección de pickup, puntuación, reporte al móvil |
| `Assets/Scripts/Player/PlayerMovements.cs` | Física 2D, input remoto + local, Animator |
| `Assets/Scripts/PlayerCollisionReporter.cs` | Reporte de colisiones por tag + animación Hit |
| `Assets/Scripts/Destruible.cs` | Objetos destructibles con animación + reporte al móvil |

---

### Destructibles

`Destruible.cs` se coloca en objetos con tag `Destruible`, `Collider2D` y `Animator`. Al empezar reproduce `bl_idle`, y al ser atacado con btnB:

```
Player presiona B
  → PlayerMovements.PlayAttack1()
    → PlayOneShot("Attack 1")
    → AttackDestructibles()
      → Physics2D.OverlapCircleAll(radio attackRadius)
      → Encuentra Destruible
        → Destruible.Explode(attacker)
          → Desactiva collider
          → animator.Play("bl_explode")
          → ReportCustomEvent("destruction", "Boom! Barril destruido")
          → wait destroyDelay → Destroy(gameObject)
```

En el móvil: `📨 Boom! Barril destruido` + vibrar 1001ms.

| Campo | Default | Script |
|---|---|---|
| `attackRadius` | 1.5 | `PlayerMovements.cs` |
| `destroyDelay` | 0.5 | `Destruible.cs` |

### Volteo del personaje

`PlayerMovements.UpdateAnimation()` usa `spriteRenderer.flipX` para voltear horizontalmente el sprite según la dirección del movimiento:

```csharp
if (velocity.x > 0.01f) spriteRenderer.flipX = false;
else if (velocity.x < -0.01f) spriteRenderer.flipX = true;
```

### Eventos custom

Los eventos `custom` que Unity envía al móvil ahora usan la estructura correcta:

```json
{"type":"custom","playerId":1,"customType":"destruction","data":"Boom! Barril destruido","timestamp":...}
```

- `type` siempre es `"custom"` (el servidor solo rutea tipos conocidos).
- `customType` es el subtipo (ej. `"destruction"`) para diferenciar en el móvil.
- `data` es el mensaje que se muestra en el log del móvil.

### Sistema de monedas — conteo automático y victoria

`GameEventManager` cuenta automáticamente todas las monedas en la escena en `Awake()` y lo usa como `TargetCoins` si no se ha asignado manualmente.

Cuando un jugador alcanza `TargetCoins`, se dispara `OnPlayerVictory(playerId)`:

```csharp
GameEventManager.Instance.OnPlayerVictory += (playerId) =>
{
    Debug.Log($"Jugador {playerId} ganó!");
    // Mostrar canvas UI, cambiar escena, etc.
};
```

El mensaje en el móvil cambió a formato progreso:
```
🖥️ Monedas: 3/7
```
