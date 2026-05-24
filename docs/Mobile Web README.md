# WiiCell Web Controller - Análisis y Propuesta de Modernización

## 📋 Estado Actual (index.html)

La web actual es un **monolito de 631 líneas** con toda la lógica mezclada:

- **HTML, CSS y JavaScript en un único archivo**
- Estilos embebidos en `<style>` (~200 líneas)
- Lógica de negocio, UI, networking y sensores en `<script>` (~365 líneas)
- Sin modularidad, tests ni escalabilidad

### Funcionalidades Actuales

| Feature | Descripción |
|---------|-------------|
| 🌐 **WebSocket Client** | Conexión bidireccional con reconexión automática (2s) |
| 🎮 **D-Pad Virtual** | Controles direccionales táctiles (W/A/S/D) |
| 🔘 **Botones A/B** | Acciones de salto y validación |
| 📱 **Sensores de Movimiento** | DeviceOrientation (gamma/beta), muestreo a 20 FPS, rango ±40° |
| 🎙️ **Micrófono** | Detección de volumen RMS, umbral de grito configurable (~20/255) |
| 📳 **Vibración** | Patrones para eventos del servidor (colisión, muerte, pickup) |
| ✨ **Efectos Visuales** | Flash de pantalla en eventos del servidor |
| 🧩 **Puzzles** | Sistema de puzzle_action para interacciones del juego |
| 🎨 **UI Responsiva** | Layout flexible con CSS Grid/Flexbox |

### Flujo de Conexión

```
[Configuración IP] → [Conectar WS] → [assignRole (P1/P2)] → [Pantalla de Control]
                                        ↓
                              [Reconexión automática cada 2s]
```

### Mensajes del Servidor Soportados

```
assignRole    → Asigna playerId (1=Azul, 2=Rojo), oculta config
collision     → Flash rojo 150ms + vibración 1s
death         → Flash rojo 400ms + vibración [500,100,400]ms
pickup        → Flash verde 150ms + vibración 1s
ui_update     → Muestra datos en log
screen_effect → Flash color + vibración opcional
puzzle_start  → Muestra puzzleId + vibración 1s
custom        → Mensaje genérico + vibración 1s
```

### Mensajes al Servidor

```javascript
// Input del jugador (20 FPS cuando hay cambios)
{
  type: "input",
  gamma: float,    // [-1.0, 1.0] inclinación lateral
  beta: float,     // [-1.0, 1.0] inclinación adelante/atrás
  dpadX: int,      // {-1, 0, 1}
  dpadY: int,      // {-1, 0, 1}
  btnA: boolean,
  btnB: boolean,
  isYelling: boolean
}

// Acciones de puzzle
{
  type: "puzzle_action",
  puzzleId: string,
  status: string
}
```

---

## 🎯 Objetivo: Paridad con mobile-app/

La app móvil (`mobile-app/`) implementa exactamente las mismas funcionalidades pero con una **arquitectura moderna y modular**:

- **Clean Architecture** (Presentation / Domain / Data)
- **MVVM** con ViewModels y StateFlows
- **Inyección de Dependencias** (Hilt)
- **Navegación** por pantallas (Connection → Controller → Settings)
- **Persistencia** de configuración (DataStore)
- **UI Declarativa** (Jetpack Compose)
- **Módulos separados**: WebSocket, Sensores, Audio, Vibration, Settings

### Arquitectura mobile-app

```
mobile-app/
├── di/              # Inyección de dependencias (Hilt)
├── domain/
│   ├── model/       # Modelos puros (ConnectionState, GameSettings...)
│   └── repository/  # Interfaces/Abstracciones
├── data/
│   ├── network/     # WebSocketClient, Mensajes
│   ├── sensor/      # SensorDataSource, SensorData
│   ├── audio/       # AudioRecorder, BlowDetector
│   └── local/       # SettingsDataStore, VibrationManager
└── presentation/
    ├── connection/  # ConnectionScreen + ConnectionViewModel
    ├── controller/  # ControllerScreen + ControllerViewModel
    └── settings/    # SettingsScreen + SettingsViewModel
```

---

## 🏗️ Propuesta de Estructura para mobile-web/

Migrar de monolito HTML a una **SPA moderna** con arquitectura modular equivalente:

```
mobile-web/
├── public/
│   ├── index.html              # Entry point mínimo (<div id="root">)
│   └── manifest.json           # PWA manifest
├── src/
│   ├── main.tsx / main.js      # Entry point de la aplicación
│   ├── App.tsx / App.js        # Router + Theme provider
│   │
│   ├── domain/
│   │   ├── models/
│   │   │   ├── ConnectionState.ts   # ConnectionStatus, SocketState
│   │   │   ├── GameSettings.ts      # SensitivityLevel, darkMode
│   │   │   └── SensorValues.ts      # gamma, beta calibrados
│   │   └── repositories/
│   │       └── GameRepository.ts    # Interfaz abstracta
│   │
│   ├── data/
│   │   ├── network/
│   │   │   ├── WebSocketClient.ts   # OkHttp equivalent (WebSocket nativo)
│   │   │   └── messages.ts          # Tipos de mensajes (Join, Input, Server...)
│   │   ├── sensors/
│   │   │   ├── SensorDataSource.ts  # DeviceOrientation wrapper
│   │   │   └── calibration.ts       # Lógica de calibración (30 frames)
│   │   ├── audio/
│   │   │   ├── AudioRecorder.ts     # Web Audio API (getUserMedia + Analyser)
│   │   │   └── BlowDetector.ts      # Detección de soplido por RMS
│   │   └── local/
│   │       ├── SettingsStore.ts     # localStorage wrapper (DataStore equiv.)
│   │       └── VibrationManager.ts  # navigator.vibrate wrapper
│   │
│   ├── presentation/
│   │   ├── connection/
│   │   │   ├── ConnectionScreen.tsx     # UI de conexión (IP, estado, logo)
│   │   │   ├── ConnectionViewModel.ts   # Estado + lógica de conexión
│   │   │   └── components/
│   │   │       └── ServerConfig.tsx     # Input IP + botón conectar
│   │   ├── controller/
│   │   │   ├── ControllerScreen.tsx     # UI del mando (landscape)
│   │   │   ├── ControllerViewModel.ts   # Estado + lógica del gamepad
│   │   │   └── components/
│   │   │       ├── DPad.tsx             # Botones direccionales
│   │   │       ├── ActionButtons.tsx    # Botones A (SALTAR) / B (VALIDAR)
│   │   │       ├── SensorPanel.tsx      # Visualización gamma/beta
│   │   │       ├── MicPanel.tsx         # Barra de volumen + controles
│   │   │       ├── StatusBar.tsx        # Estado WS, Player ID, reconexión
│   │   │       └── GameEventLog.tsx     # Log de eventos del servidor
│   │   └── settings/
│   │       ├── SettingsScreen.tsx       # Configuración (tema, sensibilidad)
│   │       ├── SettingsViewModel.ts     # Estado de settings
│   │       └── components/
│   │           ├── ThemeToggle.tsx      # Dark/Light mode
│   │           └── SensitivityGrid.tsx  # Low/Medium/High/Custom
│   │
│   ├── ui/
│   │   ├── theme/
│   │   │   ├── colors.ts          # Paleta: #1A1A2E, #E94560, #007ACC...
│   │   │   ├── ThemeProvider.tsx  # Context + CSS variables / Theme
│   │   │   └── typography.ts      # Escala de tipografías
│   │   └── components/
│   │       └── Button.tsx         # Botón reutilizable con variantes
│   │
│   └── utils/
│       ├── constants.ts           # SENSOR_TICK_RATE (50ms), umbral grito...
│       └── helpers.ts             # flashScreen, clamp, format...
│
├── package.json
├── tsconfig.json
├── vite.config.ts / webpack.config.js
└── README.md (este archivo)
```

---

## 🛠️ Stack Tecnológico Recomendado

### Opción A: React + TypeScript (Recomendada)

| Capa | Tecnología | Equivalente mobile-app |
|------|-----------|----------------------|
| **Framework** | React 18+ | Jetpack Compose |
| **Lenguaje** | TypeScript | Kotlin |
| **Build** | Vite | Gradle |
| **Estado** | Zustand / React Query | ViewModel + StateFlow |
| **Routing** | React Router | Navigation Compose |
| **Estilos** | Tailwind CSS / Styled Components | Compose Theme |
| **Persistencia** | localStorage / IndexedDB | DataStore |
| **WebSocket** | Nativo WebSocket API | OkHttp WebSocket |
| **Audio** | Web Audio API | AudioRecord |
| **Sensores** | DeviceOrientationEvent | SensorManager |
| **PWA** | vite-plugin-pwa | Android APK |

### Opción B: Vanilla TypeScript (Más ligero)

Si se prefiere evitar dependencias pesadas, se puede usar:
- **Vanilla TS** con módulos ES6
- **Custom Store** (Patrón Observable/PubSub como StateFlow)
- **CSS Modules** o **Tailwind CDN**
- **Vite** para bundling

---

## 📊 Comparativa Feature por Feature

| Feature | mobile-app (Android) | mobile-web (Actual) | mobile-web (Objetivo) |
|---------|---------------------|---------------------|----------------------|
| **Arquitectura** | Clean + MVVM | Monolito HTML | Clean + MVVM web |
| **UI** | Jetpack Compose | CSS inline | React / Web Components |
| **Estado** | StateFlow | Variables globales | Zustand / Redux |
| **Navegación** | Compose Navigation | N/A (1 pantalla) | React Router |
| **Pantallas** | 3 (Connection, Controller, Settings) | 1 (todo junto) | 3 pantallas |
| **Sensores** | SensorManager + calibración | Directo, sin calibrar | Módulo con calibración |
| **Audio** | AudioRecord + BlowDetector | AnalyserNode simple | BlowDetector completo |
| **Vibración** | VibratorManager | navigator.vibrate | VibrationManager |
| **Persistencia** | DataStore | localStorage (solo IP) | SettingsStore completo |
| **Tema** | Material3 Dark/Light | Solo dark | Toggle Dark/Light |
| **PWA** | APK nativo | N/A | Service Worker + Manifest |
| **Tests** | JUnit + Espresso | Ninguno | Jest + React Testing Library |

---

## 🚀 Plan de Migración

### Fase 1: Estructura Base
1. Inicializar proyecto con Vite + React + TS
2. Configurar Tailwind CSS
3. Crear sistema de rutas (React Router)
4. Implementar ThemeProvider (dark/light)

### Fase 2: Capa de Dominio
1. Definir modelos TypeScript (ConnectionState, GameSettings, etc.)
2. Crear interfaz GameRepository

### Fase 3: Capa de Datos
1. Implementar WebSocketClient con reconexión
2. Crear SensorDataSource con calibración
3. Implementar AudioRecorder + BlowDetector
4. Crear SettingsStore (localStorage wrapper)
5. Implementar VibrationManager

### Fase 4: Capa de Presentación
1. **ConnectionScreen**: Logo, input IP, botón conectar, estado
2. **ControllerScreen**: Landscape, D-Pad, botones A/B, sensores, micrófono
3. **SettingsScreen**: Tema, sensibilidad, calibración, debug

### Fase 5: PWA
1. Generar manifest.json e iconos
2. Configurar Service Worker (offline page)
3. Implementar Add to Home Screen

### Fase 6: Testing
1. Tests unitarios (Jest) para ViewModels
2. Tests de integración para WebSocket
3. Tests E2E (Playwright/Cypress) para flujos completos

---

## 🎨 Paleta de Colores (Consistente con mobile-app)

```css
/* Colores WiiCell */
--background: #1A1A2E;        /* Fondo principal */
--surface: #16213E;           /* Tarjetas/paneles */
--primary: #E94560;           /* Acentos, botón A */
--primary-dark: #0F3460;      /* Botón B, elementos secundarios */
--accent: #007ACC;            /* Jugador 1 (Azul) */
--accent-p2: #E84118;         /* Jugador 2 (Rojo) */
--success: #4CAF50;           /* Conectado, pickup */
--error: #F44336;             /* Desconectado, muerte */
--text-primary: #FFFFFF;
--text-secondary: #AAAAAA;
--border: #444444;
```

---

## 📱 Consideraciones Mobile Web

### iOS Safari (Limitaciones críticas)
- **DeviceOrientation**: Requiere `requestPermission()` en iOS 13+ (ya implementado)
- **AudioContext**: Requiere interacción del usuario para iniciar
- **Vibration**: No soportado en iOS Safari (`navigator.vibrate` undefined)
- **Fullscreen**: No soporta Fullscreen API, usar `standalone` mode PWA
- **WebSocket**: Sin problemas, funciona perfecto

### Android Chrome
- Todo funciona correctamente
- PWA puede instalarse como app nativa (Trusted Web Activity)
- Vibration y sensores sin restricciones (con permisos HTTPS)

### Requisitos HTTPS
- DeviceOrientation y Microphone requieren contexto seguro (HTTPS o localhost)
- Para desarrollo local, usar `vite --host` + certificado auto-firmado o ngrok

---

## 🔗 Referencias

- **mobile-app/**: Ver implementación nativa Android completa
- **Servidor**: El servidor WebSocket espera los mismos mensajes JSON en ambas plataformas
- **Protocolo**: Ver `data/network/messages.kt` en mobile-app para estructura exacta

---

## ✅ Checklist de Paridad

- [ ] Navegación: 3 pantallas separadas (Connection / Controller / Settings)
- [ ] Calibración de sensores (30 frames offset)
- [ ] BlowDetector completo (threshold, cooldown, scale)
- [ ] Persistencia: tema, sensibilidad, IP, custom force
- [ ] Tema Dark/Light toggle
- [ ] PWA: manifest, service worker, offline page
- [ ] Tests unitarios
- [ ] CI/CD para deploy automático
