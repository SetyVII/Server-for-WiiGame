package com.tfg.motioncontroller.domain.model

import com.tfg.motioncontroller.data.network.ServerMessage

/**
 * Estado de conexion completo del sistema
 */
data class ConnectionStatus(
    val socketState: SocketState = SocketState.DISCONNECTED,
    val playerId: Int? = null,
    val serverUrl: String = "",
    val errorMessage: String? = null,
    val lastEvent: ServerMessage? = null
)

enum class SocketState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Configuracion del juego
 */
data class GameSettings(
    val sensitivity: SensitivityLevel = SensitivityLevel.MEDIUM,
    val customForce: Int = 45,
    val darkMode: Boolean = true
)

enum class SensitivityLevel(val force: Int, val label: String, val description: String) {
    LOW(8, "Bajo", "0.8"),
    MEDIUM(45, "Medio", "4.5"),
    HIGH(100, "Alto", "10.0"),
    CUSTOM(45, "Custom", "Personalizado")
}

/**
 * Datos de sensores procesados
 */
data class SensorValues(
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val alpha: Float = 0f,
    val beta: Float = 0f,
    val gamma: Float = 0f,
    val accX: Float = 0f,
    val accY: Float = 0f,
    val accZ: Float = 0f
)

/**
 * Estado del microfono y deteccion de soplado
 */
data class MicrophoneState(
    val isActive: Boolean = false,
    val rmsLevel: Float = 0f,
    val isBlowing: Boolean = false,
    val threshold: Float = 0.10f,
    val cooldown: Int = 800,
    val scale: Float = 3.33f
)