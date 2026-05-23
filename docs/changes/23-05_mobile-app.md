# Cambios en mobile-app/ - 23/05/2026

## Resumen
Adaptacion completa de la app Android para comunicarse correctamente con el servidor Node.js (`server.js`) del proyecto WiiGames.

---

## Protocolo WebSocket

| Aspecto | Antes | Despues |
|---------|-------|---------|
| URL | `wss://IP:8443/ws/motion` | `ws://IP:3000` |
| SSL | Certificados autofirmados | Sin SSL (desarrollo) |
| Handshake | `{"type":"register"}` | `{"type":"join"}` |
| Sensores | `{"type":"motion",...}` | `{"type":"input", gamma, beta, dpadX, dpadY, btnA, btnB, isYelling}` |
| Botones | `{"type":"action"}` | Integrado en `input` (btnA/btnB booleanos) |
| Microfono | `{"type":"blow"}` | Integrado en `input` (isYelling) |
| Config | `{"type":"config"}` | Eliminado (no soportado por servidor) |
| Release | `{"type":"release"}` | Eliminado (no soportado por servidor) |
| Eventos entrantes | `unityStatus`, `configSaved`, `haptic` | `assignRole`, `error`, `collision`, `death`, `pickup`, `ui_update`, `screen_effect`, `puzzle_start`, `custom` |

---

## Archivos Modificados

### 1. data/network/WebSocketMessage.kt
- **Reescrito completamente** con nuevas clases de mensajes
- `JoinMessage` - Handshake inicial
- `InputMessage` - Estado completo del input (gamma, beta, dpadX, dpadY, btnA, btnB, isYelling)
- `ServerMessage` (sealed class) con todos los eventos del servidor:
  - `AssignRole`, `Error`, `Collision`, `Death`, `Pickup`, `UIUpdate`, `ScreenEffect`, `PuzzleStart`, `Custom`

### 2. data/network/WebSocketClient.kt
- **Handshake automatico**: Envia `{"type":"join"}` tras conectar
- **Manejo de mensajes entrantes**: Parsea todos los eventos del servidor (`assignRole`, `error`, etc.)
- **Estado expuesto**: Agregados `playerId` (StateFlow) y `lastEvent` (StateFlow)
- **Eliminado SSL inseguro**: Quitado `configureUnsafeSSL()` y certificados autofirmados
- **Metodos actualizados**: `sendInput(InputMessage)` unico metodo de envio

### 3. domain/model/ConnectionState.kt
- **Simplificado**: Eliminado `unityConnected` (el servidor no lo envia)
- **Agregado**: `playerId: Int?` y `lastEvent: ServerMessage?`

### 4. domain/repository/GameRepository.kt
- **URL**: Cambiada a `ws://IP:3000` (sin path `/ws/motion`)
- **Metodo unico**: `sendInput(InputMessage)` para enviar estado
- **Eliminados**: `sendAction()`, `sendRelease()`, `sendRegister()`, `sendConfig()`, `sendBlow()`

### 5. presentation/controller/ControllerViewModel.kt
- **Estado centralizado**: `currentInput` con `InputMessage` que se envia a 20 FPS
- **Botones**: `setButtonA(pressed)` y `setButtonB(pressed)` actualizan estado booleano
- **Sensores normalizados**: `gamma` y `beta` en rango [-1, 1] como en la web
- **Manejo de eventos**: `handleServerEvent()` con vibracion y log para cada tipo
- **Auto-reconexion**: Cuenta atras de 5 segundos con contador visual
- **Fix stopSensors**: Guarda el `Job` de la corrutina y lo cancela correctamente
- **Reset de input**: Al detener sensores, envia input con gamma/beta a 0

### 6. presentation/controller/ControllerScreen.kt
- **TopBar**: Muestra `playerId` con color (Azul=J1, Rojo=J2) en lugar de estado Unity
- **Botones**: Implementados con `InteractionSource` para detectar press/release
- **Log de eventos**: Muestra mensajes de eventos del servidor en la UI
- **Cuenta atras**: Visualiza "Reconectando en X..." durante la espera
- **Toast**: Muestra "Se ha perdido la conexion" al volver al menu

### 7. presentation/connection/ConnectionScreen.kt
- **Puerto**: Cambiado a `3000`
- **Protocolo**: Cambiado a `WS` (sin SSL)

### 8. presentation/connection/ConnectionViewModel.kt
- **Metodo simplificado**: `connect(serverIp, port)` sin parametro `useHttps`

### 9. presentation/settings/SettingsViewModel.kt
- **Eliminado**: Llamada a `gameRepository.sendConfig()` (no soportado por servidor)

### 10. res/xml/network_security_config.xml
- **Simplificado**: Solo permite trafico HTTP cleartext
- **Eliminados**: Certificados autofirmados y dominios especificos

---

## Bugs Corregidos

### 1. StopSensors no detenia el envio de datos
**Problema**: `stopSensors()` solo cambiaba el estado UI pero no cancelaba la corrutina del Flow.
**Solucion**: 
- Guardar el `Job` de `sensorDataSource.sensorDataFlow()` en `sensorsJob`
- Cancelar `sensorsJob` en `stopSensors()` para que `awaitClose` desregistre los listeners
- Resetear `currentInput` (gamma=0, beta=0) y enviar al servidor

### 2. Vibraciones con tipo incorrecto
**Problema**: `vibrate(1001)` no compilaba (esperaba `LongArray`).
**Solucion**: Cambiado a `vibrate(longArrayOf(1001L))`.

### 3. Deteccion de desconexion del servidor
**Problema**: OkHttp llama `onClosed` (DISCONNECTED) en lugar de `onFailure` (ERROR) al cerrar el servidor.
**Solucion**: Detectar ambos estados (`DISCONNECTED` y `ERROR`) cuando previamente estabamos conectados.

---

## Cambios adicionales (posteriores al 23/05)

### Eliminada opcion "Tamano de texto" de Settings
**Razon**: No funcionaba correctamente y no era esencial para el mando.
**Archivos afectados**:
- `presentation/settings/SettingsScreen.kt` - Eliminado slider de tamano de texto
- `domain/model/ConnectionState.kt` - Eliminado campo `fontSize` de `GameSettings`
- `data/local/SettingsDataStore.kt` - Eliminadas clave y metodos relacionados con `font_size`
- `res/values/strings.xml` - Eliminado string `settings_font_size`

### Guardado automatico en Settings
**Razon**: Mejor UX, los cambios se aplican inmediatamente sin pulsar boton.
**Archivos afectados**:
- `presentation/settings/SettingsScreen.kt` - Eliminado `tempSettings` y boton "Guardar". Cada cambio llama a `saveSettings()` automaticamente
- `presentation/settings/SettingsViewModel.kt` - Simplificado, eliminado estado `isSaved` y cuenta atras

### Enlace a control web alternativo
**Razon**: Si la app no puede conectar, ofrecer alternativa via navegador.
**Funcionalidad**:
- Contador de intentos fallidos en ConnectionScreen
- Despues de 3 intentos fallidos, muestra Card con URL `http://IP:3000`
- La URL usa la IP introducida en el campo de texto
- La URL es clicable y abre el navegador del dispositivo
**Archivos afectados**:
- `presentation/connection/ConnectionScreen.kt` - Agregado contador y Card condicional con enlace

---

## Compatibilidad

La app ahora es compatible con el protocolo del servidor `server.js`:
- Handshake con `{"type":"join"}`
- Envio de input con `{"type":"input",...}`
- Recepcion de `assignRole` para identificar al jugador
- Recepcion de eventos de Unity: `collision`, `death`, `pickup`, etc.
- Reconexion automatica con cuenta atras visual