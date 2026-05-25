# Cambios en mobile-web/ - 25/05/2026

## Resumen
Modernización completa de la web desde un monolito HTML de 631 líneas a una SPA modular con arquitectura separada, diseño responsive y paridad con mobile-app/.

---

## Cambios Realizados

### 1. Estructura de archivos modular
**Antes**: Un único archivo `index.html` con HTML, CSS y JavaScript mezclados (631 líneas)
**Después**: 
- `index.html` - Estructura limpia con 2 pantallas (controller + settings)
- `style.css` - Estilos completos con paleta de colores WiiCell (~800 líneas)
- `script.js` - Lógica modular separada por funcionalidades (~1000 líneas)
- `resources/logo_wiicell.png` - Logo copiado desde mobile-app/

**Archivos modificados/creados**:
- `mobile-web/index.html` - Reescrito completamente
- `mobile-web/style.css` - Creado nuevo
- `mobile-web/script.js` - Creado nuevo
- `mobile-web/resources/logo_wiicell.png` - Copiado desde mobile-app/

### 2. Paleta de colores consistente con mobile-app
**Cambio**: Reemplazada paleta antigua por colores de la app nativa:
- Fondo: `#1A1A2E` (azul oscuro)
- Acentos: `#9333EA` (púrpura), `#EA580C` (naranja)
- Jugador 1: `#007ACC` (azul)
- Jugador 2: `#E84118` (rojo)
- Éxito: `#4CAF50` (verde)
- Error: `#F44336` (rojo)

**Archivos modificados**:
- `mobile-web/style.css` - Variables CSS actualizadas

### 3. Navegación entre 2 pantallas
**Implementación**:
- **Controller**: Pantalla principal del mando con controles
- **Settings**: Configuración de modo de control, sensibilidad, micrófono
- Función `showScreen(screenName)` para cambiar entre pantallas
- Transiciones suaves con CSS

**Archivos modificados**:
- `mobile-web/index.html` - Estructura de 2 pantallas
- `mobile-web/script.js` - Lógica de navegación
- `mobile-web/style.css` - Estilos de pantallas

### 4. Layout responsive completo
**Implementación**:
- **Landscape**: Controles laterales (touchpad/d-pad izquierda, botones A/B derecha)
- **Portrait**: Controles centrados, barra inferior con acciones rápidas
- **Breakpoints**: 1024px, 768px, 480px para tablets y móviles
- Detección automática de orientación sin forzar landscape
- Mensaje "Gire el dispositivo" cuando es necesario

**Archivos modificados**:
- `mobile-web/style.css` - Media queries y layouts orientación
- `mobile-web/script.js` - Detección de orientación

### 5. Modo Botones vs Touchpad
**Implementación**:
- **Botones**: D-Pad W/A/S/D con bolita visual, envía `dpadX/Y` (int)
- **Touchpad**: Área ovalada táctil con bolita, envía `gamma/beta` (float)
- Toggle en settings para cambiar entre modos
- Reset automático de valores cruzados al cambiar
- Persistencia del modo en localStorage

**Archivos modificados**:
- `mobile-web/index.html` - Estructura de ambos modos
- `mobile-web/script.js` - Lógica de modos y persistencia
- `mobile-web/style.css` - Estilos de touchpad y d-pad

### 6. Touchpad ovalado con bolita móvil
**Implementación**:
- Área táctil ovalada con borde púrpura
- Bolita que sigue el movimiento del dedo
- Retorno al centro al soltar
- Cálculo de coordenadas relativas al centro
- Visualización de coordenadas en settings

**Archivos modificados**:
- `mobile-web/index.html` - Estructura del touchpad
- `mobile-web/script.js` - Eventos táctiles y cálculo de posición
- `mobile-web/style.css` - Estilos del touchpad y bolita

### 7. D-Pad con bolita visual
**Implementación**:
- Botones direccionales W/A/S/D con estados activos
- Bolita que se muestra en la dirección presionada
- Compatible con sensores activos (refleja inclinación)
- Tamaño adaptable según breakpoint (196px, 156px, 275px, 226px)

**Archivos modificados**:
- `mobile-web/index.html` - Estructura del D-Pad
- `mobile-web/script.js` - Lógica de botones y bolita
- `mobile-web/style.css` - Estilos del D-Pad

### 8. Botones A/B independientes
**Implementación**:
- Botón A (Saltar): Rojo, posición izquierda/derecha según orientación
- Botón B (Validar): Azul, desplazado 30px a la derecha en portrait
- Funcionan independientemente del modo de control
- Eventos táctiles con `preventDefault()`
- En portrait: apilados verticalmente con B desplazado

**Archivos modificados**:
- `mobile-web/index.html` - Estructura de botones
- `mobile-web/script.js` - Eventos de botones A/B
- `mobile-web/style.css` - Posicionamiento responsive de botones

### 9. Barra superior simplificada + barra inferior
**Implementación**:
- **TopBar**: Estado WS, Player ID, acciones desktop (sensores, mic, vibración, settings, desconectar)
- **BottomBar (portrait)**: Acciones rápidas con botones grandes
  - Activar/Desactivar sensores (izquierda)
  - Micrófono, Vibración (centro)
  - Desconectar (derecha)
- Botones móviles en top bar (solo portrait)

**Archivos modificados**:
- `mobile-web/index.html` - Estructura de barras
- `mobile-web/style.css` - Estilos de top-bar y bottom-bar

### 10. Panel de micrófono con ajustes
**Implementación**:
- Sliders para: Threshold (0-100), Cooldown (0-1000ms), Scale (0-100)
- Visualización de nivel de volumen en tiempo real
- Activación/desactivación del micrófono
- Detección de grito por umbral RMS
- Eventos de micrófono independientes del modo de control

**Archivos modificados**:
- `mobile-web/index.html` - Panel de micrófono en settings
- `mobile-web/script.js` - Web Audio API, AnalyserNode, detección RMS
- `mobile-web/style.css` - Estilos de sliders y panel

### 11. Detección automática de orientación
**Implementación**:
- No se fuerza landscape con JavaScript
- Detección con `window.innerWidth/Height`
- CSS `@media (orientation: landscape/portrait)` para layouts
- Mensaje "Gire el dispositivo" cuando la orientación no es óptima
- Adaptación automática de tamaños de controles

**Archivos modificados**:
- `mobile-web/script.js` - Detección de orientación
- `mobile-web/style.css` - Media queries de orientación

### 12. Calibración de sensores
**Implementación**:
- Offset simple: posición actual del dispositivo = centro
- Se aplica al activar los sensores
- Visualización de valores crudos y calibrados en settings
- Reset de offset al desactivar
- Compatible con ambos modos de control

**Archivos modificados**:
- `mobile-web/script.js` - Calibración con DeviceOrientationEvent

### 13. Persistencia de configuración
**Implementación**:
- localStorage con clave `wiicell_settings`
- Guarda: darkMode, controlMode, sensitivity, customForce, wsUrl
- Carga automática al iniciar la aplicación
- Modo de control se aplica antes de inicializar controller
- Cambios en tiempo real sin recargar

**Archivos modificados**:
- `mobile-web/script.js` - Guardar/cargar settings

### 14. Fix de alineación de botones A/B
**Problema**: Ambos botones se desplazaban a la derecha porque `.action-buttons .btn-action` aplicaba a ambos
**Solución**: 
- Botón A: `left: 0` (centrado)
- Botón B: `left: 30px` (desplazado desde el centro)
- Ahora ambos parten del centro, solo B se desplaza

**Archivos modificados**:
- `mobile-web/style.css` - Reglas `.btn-a` y `.btn-b` específicas

### 15. Touchpad deshabilitado con sensores activos
**Implementación**:
- Cuando se activan los sensores, el touchpad se pone grisáceo e inutilizable
- Fondo cambia a `#2a2a3e`, borde a `#555`, opacidad `0.4`
- Bolita se vuelve gris con opacidad `0.6`
- Transiciones suaves de `0.3s` para el cambio visual
- El touchpad ignora eventos táctiles si `sensorsActive = true`
- La bolita se mueve automáticamente reflejando `gamma` y `beta`

**Archivos modificados**:
- `mobile-web/style.css` - Estilos `.touchpad.disabled` y `.touchpad.disabled .touchpad-dot`
- `mobile-web/script.js` - Lógica de desactivación en `updateUI()`

### 16. Movimiento de la bolita del touchpad
**Implementación**:
- La bolita se mueve usando píxeles absolutos (no porcentajes CSS)
- Cálculo: `offset = gamma/beta * maxRadius` donde `maxRadius = (dimension/2) * 0.75`
- En sensores activos: bolita se mueve automáticamente reflejando inclinación
- En modo manual: bolita sigue el dedo con clamp a 75% del radio
- Alineación con app nativa: misma fórmula que `ControllerScreen.kt`

**Archivos modificados**:
- `mobile-web/script.js` - Función `updateTouchpadVisual()`

### 17. Botón Fullscreen
**Implementación**:
- Botón ⛶ añadido en `top-actions-desktop` y `top-actions-mobile`
- Función `toggleFullscreen()` para entrar/salir de pantalla completa
- Icono cambia a 🗗 cuando está en fullscreen
- Escucha evento `fullscreenchange` para actualizar icono
- API: `document.documentElement.requestFullscreen()`
- Fallback silencioso si el navegador no soporta fullscreen

**Archivos modificados**:
- `mobile-web/index.html` - Botones `#fullscreen-btn` y `#fullscreen-btn-mobile`
- `mobile-web/script.js` - Funciones `toggleFullscreen()` y `updateFullscreenIcon()`

### 18. Vibración mejorada
**Implementación**:
- Patrón complejo restaurado: `[0, 1000, 500, 100, 50, 100, 50, 100, 50, 100, 500, 1000]`
- Feedback visual: botón parpadea en rojo `#E94560` al activar
- Console logs para debug: `[testVibration] Intentando vibrar...`
- Manejo de errores robusto con try/catch
- Verificación: `'vibrate' in navigator` antes de intentar
- Mensaje claro si el navegador no soporta vibración

**Nota importante**: En Chrome Mobile requiere que el dispositivo NO esté en modo "No molestar" (DND)

**Archivos modificados**:
- `mobile-web/script.js` - Función `testVibration()` mejorada

---

## Archivos Afectados

### Creados
- `mobile-web/style.css` (nuevo)
- `mobile-web/script.js` (nuevo)
- `mobile-web/resources/logo_wiicell.png` (copiado)

### Modificados
- `mobile-web/index.html` (reescrito completamente)
- `server.js` (soporte MIME types para archivos estáticos)

---

## Pendientes

- [ ] PWA: manifest.json, service worker, offline page
- [ ] Tema Dark/Light toggle (actualmente solo dark)
- [ ] Tests unitarios
- [ ] CI/CD para deploy automático
- [ ] Iconos Material Design 3 (reemplazar emojis por iconos SVG de Google Fonts)
- [ ] Vibración testeada en iOS Safari (requiere permisos especiales)
- [ ] Optimizar consumo de batería con sensores activos

---

## Notas Técnicas

### Servidor
El servidor debe ejecutarse con `node server.js` y soporta:
- WebSocket en puerto 8080
- Archivos estáticos desde `mobile-web/` con MIME types correctos
- Mensajes JSON idénticos a mobile-app/

### Compatibilidad
- **Chrome/Android**: 100% funcionalidad (incluye fullscreen, sensores, vibración)
- **iOS Safari**: Limitaciones en vibración y fullscreen (requiere permisos)
- **Opera Mobile**: Funcional pero sin fullscreen API
- **Requisito**: HTTPS o localhost para sensores y micrófono
- **Vibración**: Requiere que el dispositivo NO esté en modo "No molestar" (DND)

### Estado Actual
La web está **funcional y lista para usar**. Tiene paridad de features con mobile-app/ excepto PWA y toggle de tema.
