package com.tfg.motioncontroller.data.sensor

/**
 * Datos crudos de los sensores Android
 */
data class RawSensorData(
    val rotationVector: FloatArray? = null, // TYPE_ROTATION_VECTOR (quaternion o matriz)
    val accelerometer: FloatArray? = null,  // TYPE_ACCELEROMETER
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawSensorData

        if (rotationVector != null) {
            if (other.rotationVector == null) return false
            if (!rotationVector.contentEquals(other.rotationVector)) return false
        } else if (other.rotationVector != null) return false
        if (accelerometer != null) {
            if (other.accelerometer == null) return false
            if (!accelerometer.contentEquals(other.accelerometer)) return false
        } else if (other.accelerometer != null) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rotationVector?.contentHashCode() ?: 0
        result = 31 * result + (accelerometer?.contentHashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * Datos procesados listos para enviar al servidor
 */
data class ProcessedSensorData(
    val tiltX: Float,      // Normalizado [-1, 1] (pitch / 45)
    val tiltY: Float,      // Normalizado [-1, 1] (roll / 45)
    val alpha: Float,      // Azimuth en grados
    val beta: Float,       // Pitch en grados
    val gamma: Float,      // Roll en grados
    val accX: Float,       // Aceleracion X (m/s^2)
    val accY: Float,       // Aceleracion Y (m/s^2)
    val accZ: Float,       // Aceleracion Z (m/s^2)
    val timestamp: Long = System.currentTimeMillis()
)
