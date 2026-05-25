# WiiGames — Documentación Completa del Juego

Plataforma multijugador 2D donde **móviles actúan como mandos** conectados vía WebSocket a una escena de Unity. Servidor Node.js enrutador, sin lógica de juego.

---

## 1. Arquitectura

```
[Móvil J1 ──┐                        ┌── Unity (receptor)
             ├── ws://host:3000 ──── ─┤
[Móvil J2 ──┘   (Node.js server)     └── (envía eventos de vuelta)
```

| Componente | Rol |
|---|---|
| `server.js` | HTTP + WebSocket. Asigna rol a cada cliente (`playerId` 1/2 o `unitySocket`). Inyecta `playerId` y reenvía mensajes |
| `index.html` | Mando web táctil con d-pad, botones A/B, giroscopio, micrófono |
| `WiiGames/` | Proyecto Unity 2D con personaje Berie, monedas, destructibles, cámara multijugador |

---

## 2. Servidor Node.js — `server.js`

### Estado

```js
let players = { 1: null, 2: null }   // WebSocket de cada móvil
let unitySocket = null                // WebSocket de Unity
```

### Tipos que Unity envía hacia los móviles

```js
const UNITY_TO_PLAYER_TYPES = new Set([
  "collision", "death", "pickup", "ui_update",
  "screen_effect", "puzzle_start", "custom",
])
```

### Flujo de mensajes

```
Conexión entrante
  ↓
¿Es unitySocket?
  ├── Sí → ¿Type conocido + playerId?
  │         ├── Sí → sendToPlayer(data.playerId, data)
  │         └── No → Warning
  └── No → ¿type === "join_unity"?
          ├── Sí → unitySocket = ws, responde "unity_ready"
          └── No → ¿type === "join"?
                   ├── Sí → Asigna playerId (1 o 2), responde "assignRole"
                   ├── No → ¿playerId asignado?
                   │         ├── Sí → ¿input? → sendToUnity({...data, playerId})
                   │         │       ¿puzzle_action? → sendToUnity({...data, playerId})
                   │         └── No → ignorar
                   └── No → ignorar
```

---

## 3. Cliente Móvil — `index.html`

### Input state

```js
const currentInput = {
  gamma: 0,     // inclinación izq/der (giroscopio, -1..1)
  beta: 0,      // inclinación adelante/atrás (-1..1)
  dpadX: 0,     // d-pad horizontal (-1,0,1)
  dpadY: 0,     // d-pad vertical (-1,0,1)
  btnA: false,  // salto
  btnB: false,  // ataque
}
```

`isYelling` se lee del micrófono en tiempo real (umbral ajustable `umbralGrito`).

### Funciones principales

| Función | Qué hace |
|---|---|
| `connect()` | WebSocket, reconexión cada 2s si cae |
| `sendCurrentInput()` | Envía `currentInput + isYelling` |
| `sendPuzzleAction(id, status)` | Envía acción de puzzle (sin uso actual) |
| `iniciarSensores()` | `deviceorientation` → normaliza gamma/beta a 20fps |
| `iniciarMicrofono()` | `getUserMedia` → analizador de frecuencia |
| `medirVolumen()` | RAF loop, actualiza `isYelling` y barra visual |
| `flashScreen(color, ms)` | Feedback visual |
| `vibrate(pattern)` | `navigator.vibrate` con mínimo 1001ms |

### Handlers de eventos entrantes (Unity → Móvil)

| type | Qué muestra en el móvil |
|---|---|
| `assignRole` | "🟢 Eres el Jugador 1/2" + borde de color |
| `collision` | "💥 Choque con X" + flash rojo 150ms + vibrar 1001ms |
| `death` | "💀 ¡Has muerto!" + flash rojo 400ms + vibrar [500,100,401] |
| `pickup` | "✨ Recogiste: coin" + flash verde 150ms + vibrar 1001ms |
| `ui_update` | "🖥️ {data}" |
| `custom` | "📨 {data}" + vibrar 1001ms |
| `screen_effect` | Flash de color + vibrar opcional |
| `puzzle_start` | "🧩 Puzzle: {puzzleId}" + vibrar 1001ms |

---

## 4. Scripts Unity

### 4.1 `WebScoketClient.cs` — Capa de transporte WebSocket

**Inspector:** `Server Url` (default `"ws://localhost:3000"`)

**Flujo:**

```
Start()
  → new WebSocket(serverUrl)
  → Registra callbacks (OnOpen, OnError, OnClose, OnMessage)
  → await Connect()  [NUNCA RETORNA — bucle Receive interno]
  → OnOpen dispara → espera State=Open → SendText("join_unity")

Update()
  → DispatchMessageQueue()  [procesa eventos asíncronos]
```

**Evento público:** `OnJsonMessageReceived(string json)`

**Métodos públicos:** `SendRawJson(string json)`

**Logs coloreados:** Azul (conexión), Verde (éxito), Rojo (error), Naranja (desconexión)

---

### 4.2 `GameEventManager.cs` — Router central de eventos

**Singleton** (`GameEventManager.Instance`). `DontDestroyOnLoad`.

**Inspector:**

| Campo | Default |
|---|---|
| `Socket Client` | (ref) WebScoketClient |
| `Player Bindings` | Lista vacía |
| `Target Coins` | 10 |
| `Teleport Points` | Lista vacía |

**Player Bindings:**

```
[0] PlayerId: 1 → Player1 (PlayerMovements)
[1] PlayerId: 2 → Player2 (PlayerMovements)
```

**Mensajes entrantes:**

```
OnJsonMessageReceived → HandleIncomingJson()
  → "unity_ready" → log
  → "input" → HandleInput()
  → "puzzle_action" → HandlePuzzleAction()
```

**HandleInput:**

| Campo | Acción |
|---|---|
| `gamma` | `player.SetExternalInput(new Vector2(gamma, 0))` |
| `btnA` | `player.RequestExternalJump()` |
| `isYelling` | `player.RequestExternalSuperJump()` |
| `btnB` | `player.PlayAttack1()` + `OnPlayerSecondaryAction` |
| `beta` (>0.01) | `OnPlayerContextInput(playerId, beta)` |

**Eventos públicos:**

```csharp
OnPlayerYelling(int playerId)
OnPlayerSecondaryAction(int playerId)
OnPlayerContextInput(int playerId, float beta)
OnPlayerVictory(int playerId)
```

**Métodos de reporte al móvil:**

| Método | type |
|---|---|
| `ReportCollision(obj, other)` | `collision` |
| `ReportDeath(obj, reason)` | `death` |
| `ReportPickup(obj, type)` | `pickup` |
| `ReportUIUpdate(obj, updateType, data)` | `ui_update` |
| `ReportScreenEffect(obj, effectType, data)` | `screen_effect` |
| `ReportPuzzleStart(obj, puzzleId)` | `puzzle_start` |
| `ReportCustomEvent(obj, customType, data)` | `custom` |

**Score y victoria:**

```csharp
GetScore(playerId) → int
AddScore(playerId, inc) → void + chequea Teleport + OnPlayerVictory
```

`CountTotalCoins()` en `Awake()` cuenta objetos `Coin` y auto-asigna `TargetCoins` si está en 0.

**Teleport system:**

```csharp
[Serializable] class TeleportConfig {
    int coinsRequired;
    Vector2 targetPosition;
    string message;
}
```

`AddScore()` → `CheckTeleport()` → si score coincide con `coinsRequired` y no triggeado → `TeleportAllPlayers()` + `ReportCustomEvent()` a todos.

---

### 4.3 `PlayerMovements.cs` — Física, input y animación

**Requiere:** `Rigidbody2D`, `Collider2D`.

**Inspector:**

| Campo | Default |
|---|---|
| `Speed` | 5 |
| `Jump Height` | 1.5 |
| `Super Jump Height` | 5 |
| `Ground Check Distance` | 0.08 |
| `Ground Layer` | Everything |
| `Animator` | auto (busca en hijos) |
| `Sprite Renderer` | auto (busca en hijos) |
| `Attack Radius` | 1.5 |
| `Use External Input` | false |

**Flujo de movimiento (`useExternalInput = true`):**

```
Update():
  moveInput = externalInput
  jumpRequested = externalJump || externalSuperJump
  superJumpRequested = externalSuperJump

FixedUpdate():
  velocity.x = moveInput.x * speed
  if (jumpRequested && IsGrounded()):
    velocity.y = √(height * -2g * gravityScale)  // jumpHeight o superJumpHeight
    justJumped = true
  rb.linearVelocity = velocity
  UpdateAnimation(velocity, grounded, justJumped)
```

**Sistema de animación (sin parámetros, `animator.Play()`):**

| Estado | Tipo | Cuándo |
|---|---|---|
| `Idle` | loop | Suelo sin movimiento |
| `Run` | loop | Suelo moviéndose |
| `Jump` | one-shot | Al saltar |
| `Fall` | one-shot | Al caer |
| `Hit` | one-shot | Colisión Enemy/Trap |
| `Death` | one-shot | `PlayDeath()` |
| `Attack 1` | one-shot | btnB |
| `Attack 2` | one-shot | `PlayAttack2()` |
| `Show Off` | one-shot | `PlayShowOff()` |

**Volteo:** `spriteRenderer.flipX = velocity.x < -0.01f`

**Ataque destructibles:** `PlayAttack1()` → `PlayOneShot("Attack 1")` + `AttackDestructibles()` → `Physics2D.OverlapCircleAll()` → `Destruible.Explode()`

**Métodos públicos:**

```csharp
SetExternalInput(Vector2)
RequestExternalJump()
RequestExternalSuperJump()
PlayHit() / PlayDeath()
PlayAttack1() / PlayAttack2()
PlayShowOff()
```

---

### 4.4 `PlayerCollisionReporter.cs`

**Requiere:** `Collider2D`. Tags default: `["Enemy", "Trap"]`.

```
OnCollisionEnter2D / OnTriggerEnter2D
  → ReportIfMatches(collider)
    → PlayerMovements.PlayHit()
    → GameEventManager.ReportCollision()
```

---

### 4.5 `Coin.cs`

**Requiere:** `Collider2D` isTrigger, tag `Coin`.

```
OnTriggerEnter2D(other)
  → ¿tiene PlayerMovements?
  → AddScore(playerId, points)
  → ReportPickup("coin")
  → ReportUIUpdate("Monedas: {score}/{target}")
  → Destroy(gameObject)
```

---

### 4.6 `Destruible.cs`

**Componentes:** `Animator`, `Collider2D`, tag `Destruible`.

```
Start() → animator.Play("bl_idle")

Explode(attacker):
  collider.enabled = false
  animator.Play("bl_explode")
  ReportCustomEvent(attacker, "destruction", "Boom! Barril destruido")
  yield return new WaitForSeconds(destroyDelay)
  Destroy(gameObject)
```

---

### 4.7 `MultiplayerCamera.cs`

**Colocar en:** Main Camera (Orthographic).

| Campo | Default |
|---|---|
| `Padding` | 2 |
| `Min Size` | 5 |
| `Max Size` | 20 |
| `Smooth Speed` | 4 |

```
LateUpdate():
  → FindObjectsByType<PlayerMovements>()
  → Bounds: encapsula todos
  → Centro = posición objetivo
  → requiredSize = max(ancho/aspecto, alto/2) + padding
  → Lerp posición + orthographicSize
```

---

## 5. Diagramas de secuencia

### 5.1 Handshake

```
Móvil J1            Servidor                Unity
  │                    │                      │
  │── WS connect ───── │                      │
  │── {"type":"join"} ─│                      │
  │                    │── WS connect ────────│
  │                    │── {"type":"join_unity"}
  │<── {"assignRole", │<── {"type":"unity_ready"}
  │     "playerId":1}  │                      │
```

### 5.2 Input

```
Móvil J1            Servidor                  Unity
  │── {"input",      │                          │
  │   gamma:0.5,     │                          │
  │   btnA:true}     │                          │
  │                  │── {"input", playerId:1,  │
  │                  │    gamma:0.5, btnA:true}  │
  │                  │                          ├ HandleInput()
  │                  │                          ├ SetExternalInput(gamma)
  │                  │                          ├ RequestExternalJump()
  │                  │                          └ UpdateAnimation()
```

### 5.3 Colisión

```
Unity                        Servidor              Móvil
  ├ PlayHit()                │                      │
  ├ ReportCollision()        │                      │
  │── {"collision",         ─│                      │
  │    playerId:1,          │                      │
  │    colliderTag:"Enemy"}  │                      │
  │                          │── (reenvía) ──────── │
  │                          │                      ├ flash + vibrar + log
```

### 5.4 Moneda

```
Unity                        Servidor              Móvil
  ├ AddScore()               │                      │
  ├ CheckTeleport()          │                      │
  ├ ReportPickup("coin")    │                      │
  ├ ReportUIUpdate("Monedas: 1/10")               │
  │── {"pickup",...}       ─│                      │
  │── {"ui_update",...}    ─│                      │
  │                          │── (reenvía) ──────── │
  └ Destroy(gameObject)      │                      ├ "✨ + flash verde + vibrar"
  │                          │                      └ "🖥️ Monedas: 1/10"
```

### 5.5 Destructible

```
Móvil → btnB → Servidor → Unity → PlayAttack1()
  → AttackDestructibles() → OverlapCircle → Destruible.Explode()
    → animator.Play("bl_explode")
    → ReportCustomEvent("destruction", "Boom! Barril destruido")
    → Servidor → Móvil: "📨 Boom!" + vibrar
    → wait → Destroy
```

### 5.6 Teleport

```
Coin recolectada → AddScore() → CheckTeleport()
  → TeleportAllPlayers(targetPosition)
    → player.transform.position = position
    → rb.linearVelocity = Vector2.zero
  → ReportCustomEvent(cada player, "teleport", mensaje)
  → Servidor → cada Móvil: "📨 mensaje" + vibrar
```

---

## 6. Diagrama de clases Unity

```
GameEventManager
├── WebSocketClient
│   ├── → Connect() + OnOpen → join_unity
│   └── → OnJsonMessageReceived
├── PlayerBindings [playerId → PlayerMovements]
│   ├── PlayerCollisionReporter → PlayHit() + ReportCollision()
│   └── PlayerMovements
│       ├── SetExternalInput() / RequestExternalJump/SuperJump()
│       ├── PlayHit/Death/Attack1/2/ShowOff()
│       └── AttackDestructibles() → Destruible.Explode()
├── TeleportPoints [coinsRequired, position, message]
├── playerScores [playerId → int]
└── ReportPickup / ReportCustomEvent / ReportUIUpdate / ...
    └── SendEvent<T>() → WebSocketClient.SendRawJson()
```

---

## 7. Setup de escena

### Tags: `Coin`, `Destruible`, `Enemy`, `Trap`, `Ground`

### Jerarquía

```
NetworkManager (WebSocketClient)
GameEventManager (GameEventManager)
  → Player Bindings: [0] 1→Player1, [1] 2→Player2
  → TargetCoins: 10
Player1 (Rigidbody2D, Collider2D, SpriteRenderer, Animator)
  → PlayerMovements (useExternalInput ✓)
  → PlayerCollisionReporter (triggerTags: Enemy, Trap)
Player2 (same)
MainCamera (MultiplayerCamera)
Coin prefab (tag Coin, isTrigger ✓)
Barril prefab (tag Destruible, Animator bl_idle/bl_explode)
```

---

## 8. Resumen de mensajes

| type | Dirección | En uso |
|---|---|---|
| `join` | Móvil→Server | ✅ |
| `assignRole` | Server→Móvil | ✅ |
| `error` | Server→Móvil | ⚠️ sin handler |
| `join_unity` | Unity→Server | ✅ |
| `unity_ready` | Server→Unity | ✅ |
| `input` | Móvil→Server→Unity | ✅ |
| `puzzle_action` | Móvil→Server→Unity | ⚠️ sin uso |
| `collision` | Unity→Server→Móvil | ✅ |
| `death` | Unity→Server→Móvil | ⚠️ sin llamador |
| `pickup` | Unity→Server→Móvil | ✅ |
| `ui_update` | Unity→Server→Móvil | ✅ |
| `screen_effect` | Unity→Server→Móvil | ⚠️ sin uso |
| `puzzle_start` | Unity→Server→Móvil | ⚠️ sin uso |
| `custom` | Unity→Server→Móvil | ✅ |

---

## 9. Cómo extender

### Nuevo evento Unity→Móvil
1. Clase `OutgoingXxxEvent` en `GameEventManager`
2. Añadir `"nuevo_tipo"` a `UNITY_TO_PLAYER_TYPES` en `server.js`
3. Handler en `index.html` dentro de `ws.onmessage`
4. Método `ReportXxx()` en `GameEventManager`

### Nuevo input Móvil→Unity
1. Campo en `sendCurrentInput()` de `index.html`
2. Campo en `IncomingPlayerInput` en `GameEventManager`
3. Procesar en `HandleInput()`

### Nueva animación en Berie
1. Estado en Animator Controller
2. `PlayOneShot("Nombre")` desde `PlayerMovements`
3. Si es loop, añadir en `UpdateAnimation`
