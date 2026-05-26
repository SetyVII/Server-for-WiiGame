# Cambios en mobile-app/ - 26/05/2026

## Resumen
Implementacion del contador de pickups (monedas) flotante en la pantalla del controller, con paridad a la version mobile-web.

---

## Cambios Realizados

### 1. Contador de pickups (monedas) flotante
**Implementacion**:
- Badge flotante en la esquina superior derecha del area de juego con icono estrella y contador numerico
- Se incrementa automaticamente al recibir mensajes `Pickup` del servidor
- Muestra el tipo de objeto recogido en el log (`event.pickupType`)
- Se reinicia a 0 automaticamente al salir y volver a entrar (sin persistencia)
- Estilo: fondo oscuro, borde dorado `#FFD700`, texto dorado, esquinas redondeadas

**Archivos modificados**:
- `ControllerViewModel.kt` - Campo `pickupCount` en `ControllerUiState`, incremento en `handleServerEvent(Pickup)`
- `ControllerScreen.kt` - Composable `PickupCounter` con icono `AttachMoney`, posicion flotante dentro del area de juego
- `ControllerScreen.kt` - Import de `androidx.compose.foundation.border`
- `ControllerScreen.kt` - Espaciado de 80dp entre botones A y D en `ButtonsPad`

**Paridad con mobile-web**:
- Mismo estilo visual (borde dorado, texto dorado)
- Mismo comportamiento (incremento en pickup, reset al reiniciar)
- Mismo uso de `pickupType` desde el servidor

### 2. Modo de Control: Touchpad / Botones
**Implementacion**:
- Selector en Settings para cambiar entre `Touchpad` (ovalado tactil) y `Botones` (D-Pad W/A/S/D)
- Persistencia del modo en `GameSettings` via `SettingsDataStore`
- Al cambiar de modo se resetean los valores de entrada (gamma/beta/dpad) para evitar valores "atascados"
- **Modo Touchpad**: D-Pad ovalado tactil existente, envia `gamma/beta` (float)
- **Modo Botones**: Grid 3x3 con botones W/A/S/D, envia `dpadX/dpadY` (int)
- Botones con estilo consistente: fondo `DPadBackground`, borde `DPadBorder`, texto blanco
- Tamaño de botones: 80dp, esquinas redondeadas 12dp

**Archivos modificados**:
- `ConnectionState.kt` - Nuevo enum `ControlMode` (TOUCHPAD, BUTTONS), campo `controlMode` en `GameSettings`
- `ControllerViewModel.kt` - Metodos `setControlMode()`, `setDPadButton()`, `resetDPadButton()`, reset de valores al cambiar modo
- `ControllerScreen.kt` - Composables `ButtonsPad` y `DPadButton`, logica condicional para mostrar Touchpad o Botones
- `SettingsScreen.kt` - Card "Modo de Control" con dos botones toggle
- `SettingsDataStore.kt` - Fix: añadido `CONTROL_MODE` preference key, lectura/escritura en `settingsFlow` y `saveSettings()`

**Paridad con mobile-web**:
- Mismos dos modos (touchpad y botones)
- Mismo envio de datos: touchpad envia gamma/beta, botones envian dpadX/dpadY
- Reset automatico al cambiar de modo

---

## Archivos Afectados

### Modificados
- `mobile-app/app/src/main/java/com/tfg/motioncontroller/domain/model/ConnectionState.kt`
- `mobile-app/app/src/main/java/com/tfg/motioncontroller/presentation/controller/ControllerViewModel.kt`
- `mobile-app/app/src/main/java/com/tfg/motioncontroller/presentation/controller/ControllerScreen.kt`
- `mobile-app/app/src/main/java/com/tfg/motioncontroller/presentation/settings/SettingsScreen.kt`

---

## Estado Actual
- Contador de pickups disponible en mobile-app y mobile-web
- Modo de control (Touchpad/Botones) disponible en mobile-app y mobile-web
- Fix: `SettingsDataStore` ahora persiste correctamente `controlMode`
- Paridad completa entre plataformas
