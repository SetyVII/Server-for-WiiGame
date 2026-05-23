# WiiGames - App Android del Mando

Aplicacion Android nativa que actua como mando virtual para el juego **WiiGames** (proyecto multijugador 2D con Unity). Se conecta via WebSocket al servidor Node.js y envia datos de sensores, botones y microfono.

## Caracteristicas

- **Conexion WebSocket** al servidor Node.js (puerto 3000, sin SSL)
- **Sensores nativos** de Android (giroscopio + acelerometro) con calibracion automatica
- **D-Pad visual** que responde a la inclinacion del dispositivo
- **Botones A/B** para saltar y accion secundaria (envio continuo de estado)
- **Deteccion de soplado/grito** por microfono con panel de configuracion expandible
- **Vibracion haptic** para feedback de eventos del juego (colisiones, muertes, pickups)
- **Configuracion de sensibilidad** (Bajo/Medio/Alto/Custom) con grid 2x2
- **Tema oscuro/claro** con Material Design 3 y seleccion por botones
- **Persistencia de ultima IP** conectada para reconexion rapida
- **Solicitud de permisos** en tiempo de ejecucion (microfono)
- **Barra de estado transparente** con iconos adaptativos al tema
- **Barra superior oculta** en pantalla del mando para experiencia inmersiva
- **Cuenta atras visual** de 5 segundos al perder conexion antes de volver al menu
- **Toast informativo** "Se ha perdido la conexion" al volver al menu
- **Panel de debug** con valores de sensores en tiempo real
- **Eventos del juego** visualizados en la UI (choques, monedas, muertes, puzzles)
- **Guardado automatico** en ajustes (sin boton "Guardar")
- **Enlace a control web** como alternativa tras 3 intentos de conexion fallidos

## Requisitos

- Android 8.0+ (API 26)
- Sensores: acelerometro y giroscopio (o rotation vector)
- Microfono (opcional, para deteccion de soplado/grito)
- Vibracion (opcional, para haptic feedback)
- **Conexion en la misma red WiFi** que el servidor

## Estructura del proyecto

```
mobile-app/
├── app/src/main/java/com/tfg/motioncontroller/
│   ├── data/
│   │   ├── audio/              # AudioRecord y deteccion de soplado
│   │   ├── network/            # WebSocket client con OkHttp
│   │   ├── sensor/             # SensorManager wrapper con calibracion
│   │   └── local/              # DataStore para settings y ultima IP
│   ├── domain/
│   │   ├── model/              # Modelos de datos (estados, settings, eventos)
│   │   └── repository/         # GameRepository (envio de input)
│   ├── presentation/
│   │   ├── connection/         # Pantalla de conexion con IP guardada
│   │   ├── controller/         # Pantalla del mando (landscape)
│   │   ├── settings/           # Pantalla de configuracion completa
│   │   └── components/         # Componentes reutilizables
│   ├── di/                     # Hilt modules
│   ├── MainActivity.kt
│   └── MotionControllerApp.kt
│   └── ui/theme/               # Tema MD3 personalizado (dark/light)
├── build.gradle.kts
└── settings.gradle.kts
```

## Stack tecnologico

| Tecnologia | Version |
|-----------|---------|
| Kotlin | 1.9.22 |
| Android Gradle Plugin | 8.3.0 |
| Jetpack Compose | BOM 2024.02 |
| Material Design 3 | Compose BOM |
| Hilt | 2.50 |
| OkHttp | 4.12.0 |
| kotlinx.serialization | 1.6.3 |
| DataStore | 1.0.0 |
| Coroutines | 1.7.3 |

## Configuracion

1. Abrir el proyecto en Android Studio
2. Sincronizar Gradle
3. La app recordara la ultima IP a la que te conectaste
4. Ejecutar en un **dispositivo fisico** (los emuladores no tienen sensores de movimiento reales)
5. El servidor Node.js debe estar ejecutandose en la PC (`pnpm start`)

## Protocolo WebSocket

La app se comunica con el servidor Node.js (`server.js`) usando el siguiente protocolo:

### Conexion
- **URL:** `ws://IP:3000`
- **Handshake:** Al conectar, envia automaticamente `{"type":"join"}`
- **Asignacion de rol:** El servidor responde con `{"type":"assignRole","playerId":1}`

### Mensajes enviados (Mando -> Servidor)

**`{"type":"input"}`** - Enviado a 20 FPS cuando los sensores estan activos, y en cada cambio de boton:

```json
{
  "type": "input",
  "gamma": 0.75,
  "beta": 0.30,
  "dpadX": 0,
  "dpadY": 0,
  "btnA": false,
  "btnB": false,
  "isYelling": false
}
```

| Campo | Tipo | Rango | Descripcion |
|-------|------|-------|-------------|
| `gamma` | float | -1.0 a 1.0 | Inclinacion izquierda/derecha (giroscopio) |
| `beta` | float | -1.0 a 1.0 | Inclinacion adelante/atras |
| `dpadX` | int | -1, 0, 1 | D-pad horizontal (no usado actualmente) |
| `dpadY` | int | -1, 0, 1 | D-pad vertical (no usado actualmente) |
| `btnA` | bool | - | Boton A presionado (salto) |
| `btnB` | bool | - | Boton B presionado (accion secundaria) |
| `isYelling` | bool | - | Microfono supera umbral de volumen |

### Mensajes recibidos (Servidor -> Mando)

El servidor reenvia eventos de Unity al mando correspondiente:

| Tipo | Descripcion | Feedback en mando |
|------|-------------|-------------------|
| `assignRole` | Asigna playerId (1 o 2) | Borde de color (Azul/Rojo) |
| `error` | Error del servidor | Mensaje en rojo |
| `collision` | Choque con objeto | Vibracion 1001ms + log |
| `death` | Jugador muerto | Vibracion 500,100,401ms + log |
| `pickup` | Recogio item | Vibracion 1001ms + log |
| `ui_update` | Actualizacion de UI | Log en pantalla |
| `screen_effect` | Efecto visual | Log + vibracion opcional |
| `puzzle_start` | Inicio de puzzle | Vibracion 1001ms + log |
| `custom` | Evento personalizado | Vibracion 1001ms + log |

## Permisos

La app solicita los siguientes permisos:
- `INTERNET` y `ACCESS_NETWORK_STATE` - Conexion al servidor
- `RECORD_AUDIO` - Deteccion de soplado (solicitado en tiempo de ejecucion)
- `VIBRATE` - Feedback haptic
- `HIGH_SAMPLING_RATE_SENSORS` - Sensores de alta frecuencia (Android 13+)

## Fases de desarrollo

### Fase 1: Infraestructura
- [x] Proyecto base con Compose y Hilt
- [x] WebSocket client con OkHttp
- [x] Modelos de mensajes serializables (protocolo WiiGames)
- [x] SensorManager wrapper con calibracion
- [x] Pantalla de conexion con memoria de IP
- [x] Pantalla del mando completa
- [x] Tema MD3 personalizado (dark/light)

### Fase 2: Protocolo y comunicacion
- [x] Handshake `{"type":"join"}` automatico
- [x] Envio de `InputMessage` con estado completo
- [x] Manejo de `assignRole` para identificar jugador
- [x] Procesamiento de eventos de Unity (collision, death, pickup, etc.)
- [x] Vibracion haptic para cada tipo de evento
- [x] Integracion de microfono como `isYelling` en el input

### Fase 3: Mando completo
- [x] D-Pad animado con punto movil
- [x] Deteccion de soplado (AudioRecord + BlowDetector)
- [x] Panel de microfono expandible con sliders
- [x] Botones A/B con press/release continuo
- [x] Barra superior con controles (sensores, microfono, vibracion, ajustes)
- [x] Barra de estado oculta en pantalla del mando
- [x] Log de eventos del juego en la UI

### Fase 4: Reconexion robusta
- [x] Deteccion de desconexion del servidor (DISCONNECTED y ERROR)
- [x] Cuenta atras visual de 5 segundos antes de volver al menu
- [x] Toast "Se ha perdido la conexion"
- [x] Limpieza de sensores y microfono al desconectar
- [x] Cancelacion correcta de corrutinas de sensores

### Fase 5: Settings completos
- [x] Pantalla de configuracion completa
- [x] DataStore para persistencia
- [x] Botones de modo oscuro/claro con iconos
- [x] Selector de sensibilidad en grid 2x2
- [x] Campo fuerza personalizada (solo en modo Custom)
- [x] Guardado automatico sin boton "Guardar"

### Fase 6: Conexion robusta
- [x] Pantalla de conexion con IP guardada
- [x] Deteccion de 3 intentos fallidos de conexion
- [x] Enlace alternativo a control web (`http://IP:3000`)
- [x] URL dinamica basada en la IP introducida
- [x] Apertura de navegador al tocar el enlace

### Fase 7: Polish y UX
- [x] Solicitud de permisos en tiempo de ejecucion
- [x] Barra de estado transparente adaptativa al tema
- [x] Panel de debug de sensores
- [x] Correccion de textos (tildes, colores en modo claro)
- [x] Guardado automatico en settings (sin boton "Guardar")
- [x] Testing en multiples dispositivos

## Cambios recientes (23/05/2026)

Ver documento detallado en `/docs/changes/23-05_mobile-app.md`.

Principales cambios:
- **Protocolo actualizado** para compatibilidad con servidor Node.js
- **URL cambiada** de `wss://IP:8443/ws/motion` a `ws://IP:3000`
- **Mensajes reestructurados** de tipos separados a `InputMessage` unificado
- **Eventos de Unity** manejados con vibracion y log visual
- **Reconexion mejorada** con cuenta atras de 5 segundos
- **Guardado automatico** en settings sin boton "Guardar"
- **Enlace a control web** tras 3 intentos fallidos de conexion

## Notas

- El servidor Node.js (`server.js`) debe estar ejecutandose antes de conectar la app
- Los sensores requieren un dispositivo fisico, no funcionan correctamente en emulador
- Se recomienda probar en la misma red WiFi que el servidor
- La deteccion de soplado requiere permiso de microfono concedido manualmente si se deniega la primera vez
- La app no intenta reconectar automaticamente; muestra cuenta atras y vuelve al menu
- Para desarrollo, la app permite trafico HTTP no cifrado (`cleartextTrafficPermitted="true"`)