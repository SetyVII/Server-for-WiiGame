package com.tfg.motioncontroller.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * SensorDataSource es el encargado de leer los sensores físicos del teléfono (giroscopio, acelerómetro).
 * Su trabajo es traducir la inclinación física del móvil en valores que el juego de Unity 
 * entienda (como "ir a la izquierda" o "saltar").
 */
@Singleton
class SensorDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SensorDataSource"
        private const val TILT_SCALE = 45f // Cuántos grados de inclinación equivalen al 100% de fuerza
        private const val CALIBRATION_FRAMES = 30 // Cuántas muestras tomamos para el "punto cero"
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Preferimos el sensor de rotación de juegos porque ignora el norte magnético (más estable)
    private val gameRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val activeRotationSensor = gameRotationSensor ?: rotationSensor

    /**
     * Esta función crea un flujo (Flow) constante de datos.
     * Incluye una fase de auto-calibración al inicio: 
     * el usuario debe mantener el móvil quieto para definir qué es "estar recto".
     */
    fun sensorDataFlow(): Flow<SensorData> = callbackFlow {
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        
        // Variables para calcular el "punto cero"
        var calibrationCount = 0
        var pitchSum = 0.0
        var rollSum = 0.0
        var isCalibrated = false
        var pitchOffset = 0f
        var rollOffset = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                var pitchDeg = 0f
                var rollDeg = 0f
                var hasNewData = false

                if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR || 
                    event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    // Obtener orientación desde el giroscopio virtual
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    
                    pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                    rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
                    hasNewData = true
                } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && activeRotationSensor == null) {
                    // Cálculo alternativo si el dispositivo no tiene giroscopio
                    val accX = event.values[0]
                    val accY = event.values[1]
                    val accZ = event.values[2]
                    
                    pitchDeg = Math.toDegrees(atan2(accY.toDouble(), sqrt((accX * accX + accZ * accZ).toDouble()))).toFloat()
                    rollDeg = Math.toDegrees(atan2(-accX.toDouble(), accZ.toDouble())).toFloat()
                    hasNewData = true
                }

                if (hasNewData) {
                    if (!isCalibrated) {
                        // Fase inicial para establecer la posición de reposo
                        pitchSum += pitchDeg
                        rollSum += rollDeg
                        calibrationCount++
                        
                        if (calibrationCount >= CALIBRATION_FRAMES) {
                            pitchOffset = (pitchSum / calibrationCount).toFloat()
                            rollOffset = (rollSum / calibrationCount).toFloat()
                            isCalibrated = true
                            Log.i(TAG, "Calibrado: Pitch=$pitchOffset, Roll=$rollOffset")
                        }
                        
                        trySend(SensorData(isCalibrating = true, calibrationProgress = calibrationCount.toFloat() / CALIBRATION_FRAMES))
                    } else {
                        val calibratedPitch = pitchDeg - pitchOffset
                        val calibratedRoll = rollDeg - rollOffset
                        
                        // Ajuste de ejes para el uso del dispositivo en horizontal
                        val tiltX = (calibratedPitch / TILT_SCALE).coerceIn(-1f, 1f)
                        val tiltY = (calibratedRoll / TILT_SCALE).coerceIn(-1f, 1f)
                        
                        trySend(
                            SensorData(
                                tiltX = tiltX,
                                tiltY = tiltY,
                                beta = calibratedRoll, // Eje longitudinal en horizontal
                                gamma = calibratedPitch, // Eje transversal en horizontal
                                isCalibrating = false
                            )
                        )
                    }
                }


                if (hasNewData) {
                    if (!isCalibrated) {
                        pitchSum += pitchDeg
                        rollSum += rollDeg
                        calibrationCount++
                        
                        if (calibrationCount >= CALIBRATION_FRAMES) {
                            pitchOffset = (pitchSum / calibrationCount).toFloat()
                            rollOffset = (rollSum / calibrationCount).toFloat()
                            isCalibrated = true
                            Log.i(TAG, "Calibrado: Pitch=$pitchOffset, Roll=$rollOffset")
                        }
                        
                        trySend(SensorData(isCalibrating = true, calibrationProgress = calibrationCount.toFloat() / CALIBRATION_FRAMES))
                    } else {
                        val calibratedPitch = pitchDeg - pitchOffset
                        val calibratedRoll = rollDeg - rollOffset
                        
                        // Mapeo corregido para Modo Horizontal (Landscape):
                        // Al estar el móvil de lado:
                        // - La inclinación hacia adelante/atrás del usuario es el "Roll" del sensor.
                        // - La inclinación lateral (izquierda/derecha) es el "Pitch" del sensor.
                        
                        val tiltX = (calibratedPitch / TILT_SCALE).coerceIn(-1f, 1f)
                        val tiltY = (calibratedRoll / TILT_SCALE).coerceIn(-1f, 1f)
                        
                        trySend(
                            SensorData(
                                tiltX = tiltX,
                                tiltY = tiltY,
                                beta = calibratedRoll, // Inclinación adelante/atrás
                                gamma = calibratedPitch, // Inclinación lateral
                                isCalibrating = false
                            )
                        )
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        // Registramos los sensores disponibles
        val sensorDelay = SensorManager.SENSOR_DELAY_GAME
        
        if (activeRotationSensor != null) {
            Log.i(TAG, "Usando sensor de rotación: ${activeRotationSensor.name}")
            sensorManager.registerListener(listener, activeRotationSensor, sensorDelay)
        } else {
            Log.i(TAG, "No hay sensor de rotación, usando acelerómetro como fallback")
            accelerometer?.let {
                sensorManager.registerListener(listener, it, sensorDelay)
            }
        }

        awaitClose {
            Log.i(TAG, "Cerrando flujo de sensores")
            sensorManager.unregisterListener(listener)
        }
    }


    fun hasRequiredSensors(): Boolean {
        val hasRotation = gameRotationSensor != null || rotationSensor != null
        val hasAccel = accelerometer != null
        Log.i(TAG, "Sensores disponibles: gameRotation=${gameRotationSensor != null}, " +
                "rotation=${rotationSensor != null}, accelerometer=${hasAccel}")
        return hasRotation || hasAccel
    }
}

/**
 * Datos del sensor incluyendo estado de calibracion
 */
data class SensorData(
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val alpha: Float = 0f,
    val beta: Float = 0f,
    val gamma: Float = 0f,
    val accX: Float = 0f,
    val accY: Float = 0f,
    val accZ: Float = 0f,
    val isCalibrating: Boolean = false,
    val calibrationProgress: Float = 0f
)
