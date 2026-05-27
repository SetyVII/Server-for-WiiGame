# Contratos JSON — WiiGames WebSocket Bridge

Documento de referencia con todos los mensajes JSON que fluyen por el servidor WebSocket.
Cada entrada incluye el JSON exacto, quién lo envía, quién lo recibe y qué hace con él.

---

## Handshake — Conexión

### `join` — Mobile pide slot de jugador

**Mobile → Server**

```json
{"type":"join"}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string | `"join"` |

- **Cuándo**: El móvil envía esto inmediatamente al abrirse el WebSocket.
- **Server**: Asigna el primer `playerId` libre (1 o 2). Si ambos slots están ocupados, responde `error`.
- **Respuesta del server**: `assignRole` (éxito) o `error` (sala llena).

---

### `assignRole` — Server asigna rol al móvil

**Server → Mobile**

```json
{"type":"assignRole","playerId":1}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string | `"assignRole"` |
| `playerId` | int | 1 o 2 |

- **Cuándo**: El server responde a un `join` exitoso.
- **Mobile**: Guarda `myPlayerId`, cambia el borde a azul (P1) o rojo (P2).

---

### `error` — Sala llena

**Server → Mobile**

```json
{"type":"error","message":"Sala llena"}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string | `"error"` |
| `message` | string | Motivo del error |

- **Cuándo**: Un móvil envía `join` pero los slots 1 y 2 ya están ocupados.
- **Mobile**: Actualmente no tiene handler para este tipo (se ignora silenciosamente).

---

### `join_unity` — Unity se registra

**Unity → Server**

```json
{"type":"join_unity"}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string | `"join_unity"` |

- **Cuándo**: `WebScoketClient.cs` lo envía automáticamente al conectarse (en el callback `OnOpen`).
- **Server**: Marca esta conexión como `unitySocket` y responde `unity_ready`.

---

### `unity_ready` — Server confirma registro de Unity

**Server → Unity**

```json
{"type":"unity_ready"}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string | `"unity_ready"` |

- **Cuándo**: El server responde a `join_unity`.
- **Unity (`GameEventManager`)**: Loggea confirmación en consola.

---

## Mobile → Server → Unity

### `input` — Sensores, botones y micrófono

**Mobile → Server → Unity** (el server inyecta `playerId`)

Lo que manda el móvil:

```json
{
  "type": "input",
  "gamma": -0.75,
  "beta": 0.30,
  "dpadX": 1,
  "dpadY": 0,
  "btnA": false,
  "btnB": false,
  "isYelling": false
}
```

Lo que el server reenvía a Unity (añade `playerId`):

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
| `type` | string | — | `"input"` |
| `playerId` | int | 1-2 | Inyectado por el server |
| `gamma` | float | -1.0 a 1.0 | Inclinación izq/der (giroscopio), normalizado desde ±40° |
| `beta` | float | -1.0 a 1.0 | Inclinación adelante/atrás, normalizado desde ±40° |
| `dpadX` | int | -1, 0, 1 | D-pad horizontal (botones A/D del móvil). **Nota**: Se duplica en `gamma` para compatibilidad con Unity. |
| `dpadY` | int | -1, 0, 1 | D-pad vertical (botones W/S del móvil). |
| `btnA` | bool | — | Botón A (salto) |
| `btnB` | bool | — | Botón B (ataque) |
| `isYelling` | bool | — | Micrófono supera umbral de 100/255 |

- **Cuándo**: El móvil lo envía a ~20 FPS desde los sensores, y en cada evento de botón.
- **Unity (`GameEventManager.HandleInput`)**:
  - `gamma` → `PlayerMovements.SetExternalInput()` — movimiento horizontal (recibe tanto el giroscopio como el D-Pad mapeado).
  - `btnA` → `PlayerMovements.RequestExternalJump()` — salto normal
  - `isYelling` → `PlayerMovements.RequestExternalSuperJump()` — super salto automático
  - `btnB` → `PlayerMovements.PlayAttack1()` + dispara `OnPlayerSecondaryAction`
  - `beta` → dispara `OnPlayerContextInput(playerId, beta)`
  - `dpadX` / `dpadY` → **No se usan directamente en Unity**, pero se envían por si el servidor o futuros scripts los necesitan.

---

### `puzzle_action` — Acción de puzzle

**Mobile → Server → Unity**

```json
{
  "type": "puzzle_action",
  "playerId": 1,
  "puzzleId": "door_002",
  "status": "solved"
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string | `"puzzle_action"` |
| `playerId` | int | Inyectado por el server |
| `puzzleId` | string | ID del puzzle |
| `status` | string | Estado (`"solved"`, etc.) |

- **Cuándo**: Función `sendPuzzleAction()` en el móvil, pero no está conectada a ningún UI.
- **Unity**: Solo loggea en consola, sin acción de gameplay.

---

## Unity → Server → Mobile

### `collision` — Choque con enemigo/trampa

**Unity → Server → Mobile**

```json
{
  "type": "collision",
  "playerId": 1,
  "colliderTag": "Enemy",
  "objectName": "Spider_01",
  "timestamp": 1716380000123
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string | `"collision"` |
| `playerId` | int | Jugador que chocó |
| `colliderTag` | string | Tag del objeto colisionado (`Enemy`, `Trap`) |
| `objectName` | string | Nombre del GameObject colisionado |
| `timestamp` | long | Unix timestamp en ms |

- **Cuándo**: `PlayerCollisionReporter` detecta colisión con tags `Enemy` o `Trap`.
- **Mobile**:
  - Log: `💥 Choque con Spider_01`
  - Flash: rojo 150ms
  - Vibrar: 1001ms
  - Unity también ejecuta `PlayHit()` (animación)

---

### `death` — Muerte del jugador

**Unity → Server → Mobile**

```json
{
  "type": "death",
  "playerId": 1,
  "reason": "trap",
  "timestamp": 1716380000123
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string | `"death"` |
| `playerId` | int | Jugador que murió |
| `reason` | string | Causa de muerte (opcional) |
| `timestamp` | long | Unix timestamp en ms |

- **Cuándo**: Llamando a `GameEventManager.ReportDeath()`. Actualmente ningún script lo invoca.
- **Mobile**:
  - Log: `💀 ¡Has muerto!`
  - Flash: rojo 400ms
  - Vibrar: `[500, 100, 401]` (1001ms total)

---

### `pickup` — Recolección de item

**Unity → Server → Mobile**

```json
{
  "type": "pickup",
  "playerId": 1,
  "pickupType": "coin",
  "timestamp": 1716380000123
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string | `"pickup"` |
| `playerId` | int | Jugador que recogió |
| `pickupType` | string | Tipo de item (`"coin"`) |
| `timestamp` | long | Unix timestamp en ms |

- **Cuándo**: `Coin.OnTriggerEnter2D()` al tocar una moneda.
- **Mobile**:
  - Log: `✨ Recogiste: coin`
  - Flash: verde 150ms
  - Vibrar: 1001ms

---

### `ui_update` — Actualización de UI

**Unity → Server → Mobile**

```json
{
  "type": "ui_update",
  "playerId": 1,
  "updateType": "coin",
  "data": "Monedas: 3/10",
  "timestamp": 1716380000123
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string | `"ui_update"` |
| `playerId` | int | Jugador destinatario |
| `updateType` | string | Tipo de actualización (`"coin"`, `"score"`) |
| `data` | string | Texto a mostrar |
| `timestamp` | long | Unix timestamp en ms |

- **Cuándo**: `Coin.OnTriggerEnter2D()` envía el progreso de monedas.
- **Mobile**: Muestra `🖥️ Monedas: 3/10` en el log.

---

### `screen_effect` — Efecto de pantalla

**Unity → Server → Mobile**

```json
{
  "type": "screen_effect",
  "playerId": 1,
  "effectType": "damage_flash",
  "data": "#ff0000",
  "timestamp": 1716380000123
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string | `"screen_effect"` |
| `playerId` | int | Jugador destinatario |
| `effectType` | string | Tipo de efecto |
| `data` | string | Payload (color, etc.) |
| `timestamp` | long | Unix timestamp en ms |

- **Cuándo**: Llamando a `GameEventManager.ReportScreenEffect()`. Sin uso actual.
- **Mobile**: Flash de color + vibración opcional. Usa defaults si los campos no coinciden.

---

### `puzzle_start` — Inicio de puzzle

**Unity → Server → Mobile**

```json
{
  "type": "puzzle_start",
  "playerId": 1,
  "puzzleId": "door_002",
  "timestamp": 1716380000123
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string | `"puzzle_start"` |
| `playerId` | int | Jugador destinatario |
| `puzzleId` | string | ID del puzzle |
| `timestamp` | long | Unix timestamp en ms |

- **Cuándo**: Llamando a `GameEventManager.ReportPuzzleStart()`. Sin uso actual.
- **Mobile**: Log `🧩 Puzzle: door_002` + vibrar 1001ms.

---

### `custom` — Evento personalizado

**Unity → Server → Mobile**

```json
{
  "type": "custom",
  "playerId": 1,
  "customType": "destruction",
  "data": "Boom! Barril destruido",
  "timestamp": 1716380000123
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string | `"custom"` |
| `playerId` | int | Jugador destinatario |
| `customType` | string | Subtipo del evento (`"destruction"`, `"teleport"`) |
| `data` | string | Texto a mostrar en el móvil |
| `timestamp` | long | Unix timestamp en ms |

- **Cuándo**:
  - `Destruible.Explode()` → `customType: "destruction"`, `data: "Boom! Barril destruido"`
  - `GameEventManager.CheckTeleport()` → `customType: "teleport"`, `data: "Pasaje secreto abierto!"`
- **Mobile**: Log `📨 Boom! Barril destruido` + vibrar 1001ms.

---

## Tabla resumen

| # | `type` | Dirección | Envía | Recibe | Estado |
|---|---|---|---|---|---|
| 1 | `join` | Mobile → Server | Mobile al conectar | Server asigna slot | ✅ |
| 2 | `assignRole` | Server → Mobile | Server tras `join` | Mobile guarda `playerId` | ✅ |
| 3 | `error` | Server → Mobile | Server si sala llena | Mobile muestra mensaje | ✅ |
| 4 | `join_unity` | Unity → Server | Unity al conectar | Server registra `unitySocket` | ✅ |
| 5 | `unity_ready` | Server → Unity | Server tras `join_unity` | Unity log confirmación | ✅ |
| 6 | `input` | Mobile → Server → Unity | Mobile ~20fps | Unity mueve/salta/ataca | ✅ |
| 7 | `puzzle_action` | Mobile → Server → Unity | Mobile (sin uso) | Unity log | ⚠️ |
| 8 | `collision` | Unity → Server → Mobile | `PlayerCollisionReporter` | Mobile flash+vibrar | ✅ |
| 9 | `death` | Unity → Server → Mobile | `ReportDeath()` (sin uso) | Mobile flash+vibrar | ⚠️ |
| 10 | `pickup` | Unity → Server → Mobile | `Coin.OnTriggerEnter2D` | Mobile flash+vibrar | ✅ |
| 11 | `ui_update` | Unity → Server → Mobile | `Coin.OnTriggerEnter2D` | Mobile muestra texto | ✅ |
| 12 | `screen_effect` | Unity → Server → Mobile | `ReportScreenEffect()` (sin uso) | Mobile flash+vibrar | ⚠️ |
| 13 | `puzzle_start` | Unity → Server → Mobile | `ReportPuzzleStart()` (sin uso) | Mobile log+vibrar | ⚠️ |
| 14 | `custom` | Unity → Server → Mobile | `Destruible` / `CheckTeleport` | Mobile log+vibrar | ✅ |

---

## Problemas conocidos

1. **`dpadX`/`dpadY` no se usan directamente en Unity** — El móvil los envía en cada `input`, pero el sistema de movimiento de Unity solo lee `gamma`. **Solución aplicada**: Los clientes (Mobile/Web) ahora duplican el valor de `dpadX` en el campo `gamma` cuando se usan los botones WASD, asegurando compatibilidad sin cambiar el código de Unity.

2. **`puzzle_action` con campos incorrectos** — El móvil envía `puzzleId` + `status`, pero la clase C# de Unity espera `actionId` + `data`. Aunque parsea, los campos no se mapean bien.

3. **`screen_effect` con campos incorrectos** — Unity envía `effectType` + `data` (strings), pero el móvil espera `color`, `duration` y `vibrate` como campos separados. Usa defaults `#ffffff` y `200ms`.

4. **`death`, `screen_effect`, `puzzle_start` nunca se llaman** — Los métodos existen en `GameEventManager` pero ningún script los invoca. Están disponibles para uso futuro.
