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
