# Funcionamiento Técnico: Motion Controller (Android)

Este documento explica cómo funciona técnicamente la aplicación móvil de control para el proyecto WiiGames.

## 1. Arquitectura General

El sistema se basa en un flujo de datos en tiempo real entre tres componentes:

1.  **Mobile App (Android/Kotlin):** Captura el movimiento y las acciones del usuario.
2.  **Java Server (Desktop):** Actúa como concentrador (hub). Recibe conexiones de múltiples mandos y las redirige al juego.
3.  **Unity Game:** Recibe los datos del servidor y los aplica a los personajes en escena.

```
[ Móvil ] --(JSON via TCP)--> [ Servidor Java ] --(TCP)--> [ Unity ]
```

## 2. Captura de Movimiento (Sensores)

La aplicación utiliza el giroscopio y el acelerómetro del dispositivo para determinar la inclinación.

-   **Sensor Principal:** `TYPE_GAME_ROTATION_VECTOR`. Es el más estable para juegos ya que no depende del magnetismo terrestre.
-   **Compatibilidad (Fallback):** En dispositivos sin giroscopio, la app conmuta automáticamente al **Acelerómetro**, calculando la inclinación mediante trigonometría basada en la gravedad.
-   **Calibración:** Al activar los sensores, la app toma 30 muestras iniciales para definir el "punto cero". Esto permite jugar cómodamente independientemente de la posición inicial del móvil.
-   **Mapeo Landscape (Mando):** Los ejes se han remapeado específicamente para el uso del móvil en horizontal:
    -   Inclinación lateral (Pitch del sensor) -> Movimiento horizontal en el juego (`gamma`).
    -   Inclinación frontal/atrás (Roll del sensor) -> Movimiento vertical en el juego (`beta`).

## 3. Modos de Control

La aplicación permite alternar entre dos modos de interacción desde la pantalla principal:
-   **Touchpad:** Los sensores están desactivados y el movimiento se controla arrastrando una "bola" virtual en un área circular.
-   **Botones (WASD):** Interfaz clásica de cruceta. 
    -   **Importante:** Para compatibilidad con Unity, las pulsaciones de los botones A/D (izquierda/derecha) se mapean también al campo `gamma` (valores 1 y -1) para permitir el movimiento horizontal sin modificar los scripts de Unity.

## 4. Detección de Soplido (Micrófono)

Para mecánicas especiales (como apagar fuego o empujar objetos), se usa el micrófono:
-   Se analiza el nivel **RMS (Root Mean Square)** del audio capturado.
-   Un algoritmo detecta picos de volumen que duren más de un tiempo determinado (cooldown) para evitar falsos positivos por ruido ambiente.
-   Cuando se detecta un soplido, se envía la bandera `isYelling: true` en el paquete JSON.

## 4. Comunicación (Protocolo JSON)

Cada 50ms (20Hz), la app envía un objeto JSON al servidor con este formato:

```json
{
  "playerId": 1,
  "gamma": 0.54,   // Inclinación X (-1 a 1)
  "beta": -0.12,   // Inclinación Y (-1 a 1)
  "btnA": false,   // Salto
  "btnB": true,    // Acción
  "isYelling": false, // Soplido
  "dpadX": 0,      // Control digital opcional
  "dpadY": 0
}
```

## 5. Feedback (Vibración)

El servidor y Unity pueden enviar comandos de vuelta al móvil para indicar eventos del juego:
-   **Colisión:** El móvil realiza una vibración corta.
-   **Muerte:** Secuencia de vibraciones largas y pausadas.
-   **Recolección:** Vibración sutil.

Esto se gestiona en `VibrationManager.kt` utilizando la API de `Vibrator` de Android.

## 6. Persistencia y Ajustes

La configuración (IP del servidor, puerto, sensibilidad del micro) se guarda de forma persistente usando **Jetpack DataStore**. Esto evita que el usuario tenga que reconfigurar la conexión cada vez que abre la app.

## 7. Diferencias con la Versión Web

| Característica | App Android (Nativa) | Versión Web (Browser) |
| :--- | :--- | :--- |
| **Latencia** | Muy baja (TCP nativo) | Media (Depende del navegador) |
| **Sensores** | Alta precisión (Fusión de sensores) | Limitada (DeviceOrientation API) |
| **Vibración** | Patrones complejos | Solo pulsos simples |
| **Segundo Plano** | Se detiene para ahorrar batería | Se desconecta al cerrar pestaña |
| **Instalación** | Requiere APK | URL directa |
