# WiiCell Web Controller - Documentación Técnica

## 📋 Estado Actual

La web ha sido modernizada desde un monolito HTML de 631 líneas a una **SPA modular** con arquitectura separada y diseño responsive.

### Estructura de Archivos

```
mobile-web/
├── index.html              # Estructura HTML con 2 pantallas
├── style.css               # Estilos completos (~800 líneas)
├── script.js               # Lógica de la aplicación (~1000 líneas)
└── resources/
    ├── logo_wiicell.png    # Logo de la app
    └── logo_wiicell.svg    # Logo vectorial
```

### Funcionalidades Implementadas

| Feature | Descripción |
|---------|-------------|
| 🌐 **WebSocket Client** | Conexión bidireccional con reconexión automática (2s) |
| 🎮 **D-Pad Virtual** | Controles direccionales táctiles (W/A/S/D) con bolita visual |
| 🔘 **Botones A/B** | Acciones de salto y validación, independientes de sensores |
| 📱 **Sensores de Movimiento** | DeviceOrientation (gamma/beta), calibración simple, muestreo a 20 FPS |
| 🎙️ **Micrófono** | Detección de volumen RMS con panel de ajustes (threshold, cooldown, scale) |
| 📳 **Vibración** | Patrones para eventos del servidor (colisión, muerte, pickup) |
| ✨ **Efectos Visuales** | Flash de pantalla en eventos del servidor (sin romper el tema) |
| 🎨 **UI Responsiva** | Layout adaptable a móviles, tablets, landscape y portrait |
| 🔄 **Modo Botones/Touchpad** | Alternancia entre D-Pad (int) y Touchpad ovalado (float) |
| 🪙 **Contador de Pickups** | Badge flotante con icono de moneda, incremento automático |
| ⚙️ **Settings** | Pantalla de configuración con persistencia en localStorage |

### Arquitectura

```
mobile-web/
├── index.html
│   ├── screen-controller      # Pantalla principal del mando
│   │   ├── top-bar            # Estado WS, Player ID, acciones
│   │   ├── touchpad-area      # Touchpad ovalado (modo touchpad)
│   │   ├── dpad-area          # D-Pad direccional (modo botones)
│   │   ├── action-buttons     # Botones A/B
│   │   ├── bottom-bar-mobile  # Acciones rápidas (portrait)
│   │   └── event-log          # Log de eventos del servidor
│   └── screen-settings        # Pantalla de configuración
│       ├── connection-status  # Estado de conexión
│       ├── control-mode       # Botones vs Touchpad
│       ├── sensitivity        # Low/Medium/High/Custom
│       └── mic-settings       # Threshold, cooldown, scale
├── style.css
│   ├── Variables CSS          # Paleta de colores WiiCell
│   ├── Layout base            # Flexbox/Grid responsive
│   ├── Componentes            # Botones, paneles, barras
│   ├── Modos de control       # Touchpad vs D-Pad
│   ├── Orientaciones          # Landscape y portrait
│   └── Media queries          # Breakpoints 1024/768/480px
└── script.js
    ├── AppState               # Estado global de la aplicación
    ├── Navegación             # showScreen() entre pantallas
    ├── controllerScreen       # Lógica del mando
    │   ├── WebSocket          # Conexión, mensajes, reconexión
    │   ├── Sensores           # DeviceOrientation, calibración
    │   ├── Touchpad           # Eventos táctiles, bolita
    │   ├── D-Pad              # Botones W/A/S/D
    │   ├── Botones A/B        | Eventos táctiles
    │   ├── Micrófono          | Web Audio API, detección RMS
    │   └── Eventos servidor   | Flash, vibración, log
    └── settingsScreen         | Lógica de configuración
        ├── Persistencia       | localStorage
        └── UI                 | Sliders, toggles, selectores
```

---

## 🎮 Pantallas

### 1. Controller (Pantalla Principal)

Layout adaptativo según orientación:

**Landscape (horizontal):**
- Barra superior simplificada (estado + acciones desktop)
- Touchpad ovalado a la izquierda (modo touchpad)
- D-Pad grande a la izquierda (modo botones)
- Botones A/B a la derecha
- Log de eventos abajo

**Portrait (vertical):**
- Barra superior mínima (solo estado)
- Touchpad/D-Pad centrado arriba
- Botones A/B apilados (B desplazado a la derecha)
- Barra inferior con acciones rápidas:
  - Activar/Desactivar sensores
  - Micrófono
  - Vibración de test
  - Desconectar

### 2. Settings (Configuración)

- **Estado de conexión**: Muestra URL actual y estado del WebSocket
- **Modo de control**: Toggle entre Botones y Touchpad
- **Sensibilidad**: Low / Medium / High / Custom (con slider)
- **Micrófono**: Sliders para threshold, cooldown y scale
- **Debug**: Información de sensores calibrados

---

## 🕹️ Controles

### Modo Botones (D-Pad)
- Envía `dpadX` y `dpadY` como enteros `{-1, 0, 1}`
- Botones táctiles W/A/S/D con bolita visual que se mueve
- Compatible con sensores activos (la bolita refleja la inclinación)

### Modo Touchpad
- Envía `gamma` y `beta` como floats `[-1.0, 1.0]`
- Área ovalada táctil con bolita que sigue el dedo
- Centro automático al soltar
- Calibración: posición actual se convierte en centro al activar

### Botones A/B
- **A (Saltar)**: Rojo, independiente del modo de control
- **B (Validar)**: Azul, independiente del modo de control
- En portrait: apilados verticalmente con B desplazado 30px a la derecha

---

## 📡 Comunicación WebSocket

### Mensajes al Servidor

```javascript
// Input del jugador (20 FPS cuando hay cambios)
{
  type: "input",
  gamma: float,    // [-1.0, 1.0] inclinación lateral (touchpad)
  beta: float,     // [-1.0, 1.0] inclinación adelante/atrás (touchpad)
  dpadX: int,      // {-1, 0, 1} (modo botones)
  dpadY: int,      // {-1, 0, 1} (modo botones)
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

### Mensajes del Servidor

```
assignRole    → Asigna playerId (1=Azul, 2=Rojo), oculta config
collision     → Flash rojo 150ms + vibración 1001ms
death         → Flash rojo 400ms + vibración [0, 1001]ms
pickup        → Flash verde 150ms + vibración 1001ms + contador+1
ui_update     → Muestra datos en log
screen_effect → Flash color + vibración 1001ms
puzzle_start  → Muestra puzzleId + vibración 1s
custom        → Mensaje genérico + vibración 1s
```

---

## 📱 Responsive Design

### Breakpoints

| Dispositivo | Ancho | Touchpad | D-Pad | Botones A/B |
|-------------|-------|----------|-------|-------------|
| Desktop/Tablet (landscape) | >1024px | 256px | 196px | 90px |
| Tablet (landscape) | 768-1024px | - | 156px | 80px |
| Móvil (landscape) | <768px | 180px | - | 70px |
| Móvil (portrait) | <768px | 260px ancho | 275x275px / 226x226px | 90px / 80px |

### Orientación
- **Detección automática**: No se fuerza landscape
- **Mensaje de giro**: Aparece cuando la orientación no coincide con el diseño óptimo
- **Adaptación CSS**: Las media queries `@media (orientation: landscape/portrait)` ajustan el layout

---

## 🎨 Paleta de Colores

```css
/* Colores WiiCell */
--background: #1A1A2E;        /* Fondo principal */
--surface: #16213E;           /* Tarjetas/paneles */
--primary: #9333EA;           /* Púrpura (acentos) */
--accent-orange: #EA580C;     /* Naranja (botón A) */
--accent-red: #E94560;        /* Rojo (muerte, error) */
--accent-blue: #007ACC;       /* Azul (jugador 1, botón B) */
--accent-p2: #E84118;         /* Rojo (jugador 2) */
--success: #4CAF50;           /* Verde (conectado, pickup) */
--error: #F44336;             /* Desconectado */
--text-primary: #FFFFFF;
--text-secondary: #AAAAAA;
--border: #444444;
```

---

## 🔧 Configuración y Persistencia

### localStorage

Clave: `wiicell_settings`

```json
{
  "darkMode": true,
  "controlMode": "touchpad",
  "sensitivity": "medium",
  "customForce": 45,
  "wsUrl": "ws://localhost:8080"
}
```

### Modo de Control
- Se guarda automáticamente al cambiar
- Se carga antes de inicializar el controller
- Al cambiar entre modos, se resetean los valores cruzados (gamma/beta → 0 o dpadX/Y → 0)

---

## 📱 Consideraciones Mobile Web

### iOS Safari (Limitaciones)
- **DeviceOrientation**: Requiere `requestPermission()` en iOS 13+ (ya implementado)
- **AudioContext**: Requiere interacción del usuario para iniciar
- **Vibration**: No soportado en iOS Safari (`navigator.vibrate` undefined)
- **Fullscreen**: No soporta Fullscreen API nativa
- **WebSocket**: Funciona correctamente

### Android Chrome
- Todo funciona correctamente
- PWA puede instalarse como app nativa
- Vibration y sensores sin restricciones (con permisos HTTPS)

### Requisitos HTTPS
- DeviceOrientation y Microphone requieren contexto seguro (HTTPS o localhost)
- Para desarrollo local, usar `localhost` o certificado auto-firmado

---

## 🚀 Flujo de Uso

1. **Servidor**: El servidor debe estar ejecutándose (`node server.js`)
2. **Conexión**: La web se conecta automáticamente al WebSocket del servidor
3. **Rol**: El servidor asigna P1 (Azul) o P2 (Rojo)
4. **Juego**: Usar touchpad/d-pad + botones A/B para controlar
5. **Ajustes**: Abrir ⚙️ para cambiar modo de control, sensibilidad, etc.

---

## ✅ Checklist de Paridad con mobile-app

- [x] Navegación: 2 pantallas separadas (Controller + Settings)
- [x] D-Pad con bolita visual
- [x] Touchpad ovalado con bolita móvil
- [x] Botones A/B independientes
- [x] Calibración de sensores (offset simple)
- [x] Detección automática de orientación
- [x] Layout responsive (landscape/portrait)
- [x] Panel de micrófono con ajustes
- [x] Vibración en eventos del servidor
- [x] Flash de pantalla en eventos (sin romper tema)
- [x] Persistencia de settings (localStorage)
- [x] Paleta de colores consistente
- [x] Contador de pickups flotante
- [x] Tests unitarios

---

## 🔗 Referencias

- **mobile-app/**: Ver implementación nativa Android completa
- **Servidor**: `server.js` - Servidor HTTP + WebSocket
- **Protocolo**: Los mensajes JSON son idénticos en ambas plataformas
