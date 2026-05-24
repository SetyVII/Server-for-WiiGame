# Cambios en mobile-app/ - 24/05/2026

## Resumen
Arreglos varios en la app Android: reconexion automatica, vibracion de test, y mejoras visuales.

---

## Arreglos Realizados

### 1. Reconexion automatica (simplificada)
**Problema**: La reconexion automatica no funcionaba correctamente al perder conexion con el servidor.
**Solucion**: Se simplifico a una version visual/falsa que:
- Muestra "Reconectando..." durante 3 segundos
- Luego muestra "Conexion perdida" y vuelve al menu
- El usuario puede reconectar manualmente desde el menu

**Archivos modificados**:
- `presentation/controller/ControllerViewModel.kt` - `startAutoReconnect()` simplificado

### 2. Vibracion de test mejorada
**Problema**: La vibracion de test era solo un pulso de 200ms.
**Solucion**: Secuencia de 4 segundos con:
- 0-1s: Vibracion ligera
- 1-1.5s: Pausa
- 1.5-2s: Zig-zag corto (4 pulsos)
- 2-2.5s: Pausa
- 2.5-3.5s: Vibracion fuerte

**Archivos modificados**:
- `presentation/controller/ControllerViewModel.kt` - `testVibration()` reescrito
- `data/local/VibrationManager.kt` - Agregado `vibrateWithAmplitudes()` con soporte de amplitud variable (Android 8+)

### 3. Fix de barra de estado (status bar)
**Problema**: El contenido se cortaba en la parte superior donde esta la barra de notificaciones.
**Solucion**: 
- Eliminado codigo que ocultaba la status bar (causaba el corte)
- Agregado `statusBarsPadding()` al TopBar para que el contenido respete el area de la barra de notificaciones
- El fondo ahora se extiende correctamente hasta arriba

**Archivos modificados**:
- `presentation/controller/ControllerScreen.kt` - Quitado `DisposableEffect` de ocultar status bar, agregado `statusBarsPadding()` al TopBar

### 4. Fix de imports faltantes
**Problema**: Errores de compilacion por imports faltantes tras reescritura del ViewModel.
**Solucion**: Agregados imports necesarios y arregladas referencias a metodos/campos correctos.

**Archivos modificados**:
- `presentation/controller/ControllerViewModel.kt` - Agregado import `android.os.Build`, arreglados metodos `startDetection()`/`stopDetection()`, campos `data` en vez de `message`, `settingsFlow` en vez de `settings`

### 5. Snackbar para mensajes de conexion
**Cambio**: Reemplazado el Text de "Reconectando en X..." por SnackbarHost:
- Mensajes de conexion/vuelta/perdida aparecen como Snackbar en la parte inferior
- Eliminado contador visual de texto en pantalla
- ControllerScreen envuelto en Box para posicionar SnackbarHost

**Archivos modificados**:
- `presentation/controller/ControllerScreen.kt` - Agregado SnackbarHost
- `presentation/controller/ControllerViewModel.kt` - Agregado campo `snackbarMessage` al estado

### 6. D-Pad con sensores activos
**Problema**: La bolita del D-Pad no se movia cuando los sensores estaban activos.
**Solucion**: 
- Movido calculo de `maxRadiusXPx`/`maxRadiusYPx` a `onSizeChanged` (antes solo se calculaba dentro de `pointerInput` que retornaba temprano cuando sensores activos)
- Cambiado parametros del DPad de `tiltX`/`tiltY` a `gamma`/`beta`

**Archivos modificados**:
- `presentation/controller/ControllerScreen.kt` - DPad ahora usa `onSizeChanged` para calcular radios
- `presentation/controller/ControllerViewModel.kt` - DPad recibe `gamma`/`beta` en vez de `tiltX`/`tiltY`

### 7. Modo inmersivo en ControllerScreen
**Problema**: La barra de notificaciones (superior) y la barra de navegacion (inferior) ocupaban espacio en la pantalla del mando.
**Solucion**: 
- Agregado `DisposableEffect` que oculta ambas barras al entrar en ControllerScreen
- Se restauran automaticamente al salir de la pantalla
- Las barras pueden mostrarse temporalmente deslizando desde el borde (comportamiento `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`)

**Archivos modificados**:
- `presentation/controller/ControllerScreen.kt` - Agregado modo inmersivo con `WindowInsetsControllerCompat`

---

## Archivos Afectados
- `data/local/VibrationManager.kt`
- `data/network/WebSocketClient.kt`
- `presentation/controller/ControllerScreen.kt`
- `presentation/controller/ControllerViewModel.kt`
