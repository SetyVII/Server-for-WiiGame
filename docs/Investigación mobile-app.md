# Investigación mobile-app/ - Conclusiones y Requisitos para mobile-web

## 📱 Análisis de la App Móvil (mobile-app/)

### Arquitectura General

```
mobile-app/
├── Presentation Layer (UI + ViewModels)
│   ├── connection/     → ConnectionScreen (portrait)
│   ├── controller/     → ControllerScreen (landscape) 
│   └── settings/       → SettingsScreen (portrait)
├── Domain Layer
│   ├── model/          → ConnectionState, GameSettings, SensorValues...
│   └── repository/     → GameRepository (abstracción WS)
└── Data Layer
    ├── network/        → WebSocketClient, mensajes
    ├── sensor/         → SensorDataSource (con calibración)
    ├── audio/          → AudioRecorder + BlowDetector
    └── local/          → SettingsDataStore + VibrationManager
```

### Flujo de Navegación

```
[ConnectionScreen] --conecta--> [ControllerScreen] --settings--> [SettingsScreen]
      ↑ (portrait)                  ↑ (landscape)                    ↑ (portrait)
      └--desconecta/desconexión-----┘                              └--back-----┘
```

**Orientación forzada**:
- Connection: Portrait (vertical)
- Controller: Landscape (horizontal)
- Settings: Portrait (vertical)

---

## 🎮 Pantalla Controller (ControllerScreen.kt)

### Layout Completo (Landscape)

```
┌───────────────────────────────────────────────────────────────────────────────┐
│ Socket: conectado     [Activar/Desactivar sensores]  🎙️ 📳 ⚙️ [Desconectar]  │
│ Jugador 1                                                                     │
├───────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│   ┌─────────────────────────────────────┐         ┌─────────────────────┐     │
│   │                                     │         │                     │     │
│   │         D-Pad (Touchpad)            │         │    ┌─────────┐      │     │
│   │      ┌──────────────────┐           │         │    │    A    │      │     │
│   │      │    ○ (bolita)    │           │         │    │  SALTAR │      │     │
│   │      │                  │           │         │    └─────────┘      │     │
│   │      └──────────────────┘           │         │    ┌─────────┐      │     │
│   │           Área ovalada              │         │    │    B    │      │     │
│   │                                     │         │    │ VALIDAR │      │     │
│   └─────────────────────────────────────┘         │    └─────────┘      │     │
│                                                   └─────────────────────┘     │
│                                                                               │
│   [Barra de calibración]                                                      │
│   [Debug info: tiltX: 0.45 | tiltY: -0.12]                                    │
│   [Panel de micrófono expandible]                                             │
│   [Mensaje de error/log]                                                      │
└───────────────────────────────────────────────────────────────────────────────┘
```

### Elementos Detallados

#### 1. TopBar
- **Izquierda**: Estado del socket (color verde si conectado) + "Jugador X" (color azul/rojo)
- **Centro**: Botón "Activar sensores" / "Desactivar sensores" (cambia color según estado)
- **Derecha**: 
  - Icono Micrófono (🎙️) - rojo si activo, blanco si inactivo
  - Icono Vibración (📳) - test de vibración
  - Icono Settings (⚙️) - ir a configuración
  - Botón "Desconectar" (rojo)

#### 2. D-Pad / Touchpad (¡Esto es clave!)

**La app NO tiene botones direccionales W/A/S/D como la web actual.**

En su lugar tiene un **área ovalada tipo touchpad**:
- **Forma**: Ovalada con fondo morado (`#0F3460`) y borde rojo que se ilumina según intensidad
- **Bolita**: Círculo de 48dp de color rojo (`#E94560`) que se mueve dentro del área
- **Cuando sensores activos**: La bolita se mueve automáticamente reflejando la inclinación del teléfono. El área se atenúa (alpha 0.5). La bolita se vuelve gris.
- **Cuando sensores inactivos**: El usuario puede arrastrar el dedo sobre el área (como un touchpad/joystick) para controlar manualmente gamma/beta.
- **Mapeo**: 
  - X horizontal → gamma (roll)
  - Y vertical → beta (pitch, invertido)
  - Valores normalizados entre -1.0 y 1.0

#### 3. Botones de Acción
- **Botón A**: Círculo morado (`#9333EA`), texto "A" grande + "SALTAR" debajo
- **Botón B**: Círculo naranja (`#EA580C`), texto "B" grande + "VALIDAR" debajo
- **Tamaño**: 120dp cada uno
- **Disposición**: Apilados verticalmente

#### 4. Panel de Micrófono (Expandible)
- Se muestra/oculta con animación cuando se activa/desactiva el mic
- Barra de volumen con color según intensidad:
  - Verde (bajo) → Amarillo (medio) → Rojo (alto)
- Texto "SOPLO!" cuando se detecta soplado
- 3 sliders:
  - **Sensibilidad**: 0.05 - 0.50 (threshold)
  - **Cooldown**: 200ms - 2000ms
  - **Escala**: 1x - 10x (para la barra visual)

#### 5. Calibración
- Cuando se activan sensores: 30 frames (~0.5s) calculando offset promedio
- Barra de progreso visible durante calibración
- Mensaje: "Calibrando sensores... X% - Manten el movil quieto"
- Durante calibración: valores neutros (0,0), bolita centrada

---

## ⚙️ Pantalla Settings (SettingsScreen.kt)

### Opciones Actuales

#### 1. Apariencia
- **Toggle Dark/Light mode**: Dos botones con iconos (☀️/🌙)

#### 2. Sensibilidad del Control
- **Grid de 4 opciones** (2x2):
  - **LOW** (`>`): fuerza 0.8
  - **MEDIUM** (`>>`): fuerza 4.5 (default)
  - **HIGH** (`>>>`): fuerza 10.0
  - **CUSTOM** (`⚙`): personalizado
- Si CUSTOM: input numérico 1-100 para "fuerza personalizada"
- Card informativa mostrando nivel y fuerza actuales

---

## 🔧 Funcionamiento Interno (ControllerViewModel.kt)

### Estados Clave
```kotlin
data class ControllerUiState(
    val connectionStatus: ConnectionStatus,
    val playerId: Int?,              // 1 o 2
    val sensorValues: SensorValues,   // gamma, beta, tiltX, tiltY...
    val sensorsActive: Boolean,       // sensores encendidos/apagados
    val isCalibrating: Boolean,       // en fase de calibración
    val calibrationProgress: Float,   // 0.0 - 1.0
    val microphoneState: MicrophoneState,
    val errorMessage: String?,
    val logMessage: String?,
    val settings: GameSettings,
    val reconnectCountdown: Int?,     // null o 0 (volver al menu)
    val snackbarMessage: String?
)
```

### Input Message (lo que se envía al servidor)
```kotlin
data class InputMessage(
    val type: String = "input",
    val gamma: Float = 0f,      // [-1.0, 1.0] inclinación lateral
    val beta: Float = 0f,       // [-1.0, 1.0] inclinación adelante/atrás
    val dpadX: Int = 0,         // {-1, 0, 1} (NO SE USA en app actual)
    val dpadY: Int = 0,         // {-1, 0, 1} (NO SE USA en app actual)
    val btnA: Boolean = false,
    val btnB: Boolean = false,
    val isYelling: Boolean = false
)
```

### Lógica de Sensores
1. **startSensors()**:
   - Activa `sensorsActive = true`, `isCalibrating = true`
   - Inicia flow de SensorDataSource con sample(50ms) = 20 FPS
   - Recibe datos con calibración automática (30 frames)
   - Mapeo: gamma = (rawGamma / 40°).coerceIn(-1,1), beta = (rawBeta / 40°).coerceIn(-1,1)
   - Actualiza `currentInput` y envía al servidor

2. **stopSensors()**:
   - Cancela el job de sensores
   - Resetea `sensorValues`, `sensorsActive = false`
   - Pone gamma=0, beta=0 en currentInput y envía

### Botones y D-Pad Manual
- **setButtonA/B()**: Actualiza currentInput y envía inmediatamente
- **setManualTilt()**: Cuando el usuario arrastra el D-Pad, actualiza gamma/beta y envía
- **resetManualTilt()**: Suelta el dedo → gamma=0, beta=0

### Reconexión
- Si se pierde conexión después de haber estado conectado:
  - Muestra Snackbar "Reconectando..."
  - Espera 3 segundos
  - Navega de vuelta a ConnectionScreen (`reconnectCountdown = 0`)
- No hay reconexión automática real, vuelve al menú

---

## 🎨 Paleta de Colores Exacta (Color.kt)

```kotlin
// Material Theme
Primary:           #E94560 (rosa/rojo)
PrimaryContainer:  #C73E54
Secondary:         #0F3460 (azul oscuro)
SecondaryContainer:#16213E
Tertiary:          #9333EA (morado)
Background:        #1A1A2E (fondo principal)
Surface:           #16213E
SurfaceVariant:    #0B1320
Error:             #EF4444
Success:           #22C55E

// UI específica
ButtonA:           #9333EA (morado)
ButtonB:           #EA580C (naranja)
DPadBorder:        #E94560 (rojo)
DPadDot:           #E94560 (rojo)
DPadBackground:    #0F3460 (azul oscuro)
TopBarBackground:  #16213E

// Barra de volumen
VolumeStart:       #22C55E (verde)
VolumeMid:         #EAB308 (amarillo)
VolumeEnd:         #EF4444 (rojo)

// Jugadores
Player 1:          #00A8FF (azul)
Player 2:          #E84118 (rojo)
```

---

## 🎯 Requisitos Clarificados para mobile-web

Basado en la investigación y las indicaciones del usuario:

### 1. Modos de Control (Settings)

**NUEVO**: Selección en ajustes para cambiar entre dos modos:

#### Modo "Touchpad" (App Nativa)
- D-Pad ovalado tipo touchpad (como la app)
- Arrastrar dedo para controlar gamma/beta manualmente
- Cuando sensores activos: bolita se mueve automáticamente, touchpad deshabilitado

#### Modo "Botones" (Web Actual)
- D-Pad con botones W/A/S/D (como la web actual)
- Botones direccionales táctiles
- Cuando sensores activos: botones se deshabilitan visualmente

### 2. Desactivación de Controles con Sensores Activos

**REGLA**: Cuando `sensorsActive = true`:
- Touchpad: Se atenúa (alpha 0.5), bolita se vuelve gris, no responde a touch
- Botones D-Pad (modo botones): Se deshabilitan visualmente (grises, no responden a clicks)
- **Botones A/B: SIEMPRE funcionan** (igual que en la app nativa)

### 3. Orientación del Control (Settings)

**NUEVO**: Opción en ajustes:

#### Horizontal (Landscape) - Default
- Layout: Touchpad a la izquierda, botones a la derecha
- gamma = inclinación lateral (roll)
- beta = inclinación adelante/atrás (pitch, invertido)

#### Vertical (Portrait)
- Layout: Touchpad arriba, botones abajo (o similar)
- **Adaptación de sensores**: gamma y beta varían según la orientación del dispositivo
  - Al girar el móvil de horizontal a vertical, la interpretación de los sensores debe adaptarse
  - "Ladear el móvil" debe seguir funcionando igual independientemente de la orientación elegida
  - Es decir: si en horizontal gamma=roll, en vertical gamma debe seguir siendo roll (la referencia cambia con la rotación física del dispositivo)

#### Detección de Orientación del Dispositivo
- **NO se fuerza la orientación** (la app nativa sí la fuerza)
- Se detecta si el usuario no ha girado la pantalla físicamente
- Si el dispositivo está en portrait pero el modo es landscape (o viceversa): 
  - Mostrar mensaje: **"Gire el dispositivo"**
  - Bloquear el uso del controller hasta que se gire

### 4. Calibración de Sensores

**REGLA**: Al activar sensores:
- Tomar la **posición actual del dispositivo como "centro" (offset)**
- No hacer calibración de 30 frames como la app nativa (más simple)
- Offset se calcula una sola vez al activar y se aplica a todos los valores posteriores
- Si se desactivan y reactivan: se recalcula el centro

### 5. Prevenir Scroll

**REGLA**: En la pantalla Controller:
```css
body.controller-screen {
  overflow: hidden;
  position: fixed;
  width: 100%;
  height: 100%;
  touch-action: none; /* Previene scroll y zoom en móviles */
}
```

### 6. Responsive Design

**REGLA**: Si no hay suficiente espacio:
- Botones A/B se reducen proporcionalmente
- Touchpad/D-Pad se reduce proporcionalmente
- Usar `clamp()`, `min()`, `max()` en CSS
- Media queries para diferentes tamaños:
  - **Pequeño** (< 360px ancho): Layout compacto
  - **Mediano** (360-600px): Layout estándar
  - **Grande** (> 600px): Layout expandido
- Prevenir que los botones se solapen con safe areas (notch, barra de navegación)

---

## ✅ Respuestas Definitivas (Aclaraciones del Usuario)

### 1. Botones A/B
- **SIEMPRE funcionan**, independientemente de si los sensores están activos o no

### 2. Modo Botones vs Touchpad
- **Modo Botones (WASD)**: Usa `dpadX`/`dpadY` en el mensaje input
- **Modo Touchpad**: Usa `gamma`/`beta` en el mensaje input
- **IMPORTANTE**: La app nativa solo usa gamma/beta. En la web, el modo botones es una adición nueva que usa dpadX/Y del protocolo.

### 3. Orientación y Sensores
- gamma y beta **varían según la orientación del dispositivo**
- No se intercambian, sino que la referencia de los sensores cambia con la rotación física
- "Ladear el móvil" debe funcionar igual en cualquier orientación

### 4. Calibración
- **Simple**: Usar posición actual como centro al activar sensores
- Sin calibración de 30 frames (más ligero que la app nativa)

### 5. Orientación Forzada
- **NO forzar orientación**
- Detectar si el dispositivo no está en la orientación correcta
- Mostrar mensaje: "Gire el dispositivo"
- Bloquear controller hasta que se gire
