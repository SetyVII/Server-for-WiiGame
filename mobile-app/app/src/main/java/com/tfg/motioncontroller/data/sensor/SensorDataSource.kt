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

@Singleton
class SensorDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SensorDataSource"
        private const val TILT_SCALE = 45f
        private const val CALIBRATION_FRAMES = 30 // ~0.5 segundos a 60fps
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val gameRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val activeRotationSensor = gameRotationSensor ?: rotationSensor

    /**
     * Flow de datos de sensores procesados con calibracion automatica
     * 
     * Durante los primeros CALIBRATION_FRAMES frames, calcula el offset promedio
     * y lo aplica a todos los valores posteriores.
     */
    fun sensorDataFlow(): Flow<SensorData> = callbackFlow {
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        var lastAccelerometer: FloatArray? = null
        var hasRotationData = false
        
        // Variables de calibracion
        var calibrationCount = 0
        var pitchSum = 0.0
        var rollSum = 0.0
        var isCalibrated = false
        var pitchOffset = 0f
        var rollOffset = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GAME_ROTATION_VECTOR,
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        hasRotationData = true
                        
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        
                        val pitchRad = orientationAngles[1]
                        val rollRad = orientationAngles[2]
                        
                        val pitchDeg = Math.toDegrees(pitchRad.toDouble()).toFloat()
                        val rollDeg = Math.toDegrees(rollRad.toDouble()).toFloat()
                        
                        if (!isCalibrated) {
                            // Fase de calibracion: acumular valores
                            pitchSum += pitchDeg
                            rollSum += rollDeg
                            calibrationCount++
                            
                            if (calibrationCount >= CALIBRATION_FRAMES) {
                                // Calcular offset promedio
                                pitchOffset = (pitchSum / calibrationCount).toFloat()
                                rollOffset = (rollSum / calibrationCount).toFloat()
                                isCalibrated = true
                                Log.i(TAG, "Calibracion completada. Offset: pitch=$pitchOffset, roll=$rollOffset")
                            }
                            
                            // Durante calibracion, enviar valores neutros
                            trySend(
                                SensorData(
                                    tiltX = 0f,
                                    tiltY = 0f,
                                    alpha = 0f,
                                    beta = 0f,
                                    gamma = 0f,
                                    accX = 0f,
                                    accY = 0f,
                                    accZ = 0f,
                                    isCalibrating = true,
                                    calibrationProgress = calibrationCount.toFloat() / CALIBRATION_FRAMES
                                )
                            )
                        } else {
                            // Aplicar offset para valores calibrados
                            val calibratedPitch = pitchDeg - pitchOffset
                            val calibratedRoll = rollDeg - rollOffset
                            
                            // Mapeo landscape (igual que antes)
                            val tiltX = (calibratedRoll / TILT_SCALE).coerceIn(-1f, 1f)
                            val tiltY = (-calibratedPitch / TILT_SCALE).coerceIn(-1f, 1f)
                            
                            val alpha = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                            
                            val acc = lastAccelerometer
                            trySend(
                                SensorData(
                                    tiltX = tiltX,
                                    tiltY = tiltY,
                                    alpha = alpha,
                                    beta = calibratedPitch,
                                    gamma = calibratedRoll,
                                    accX = acc?.get(0) ?: 0f,
                                    accY = acc?.get(1) ?: 0f,
                                    accZ = acc?.get(2) ?: 0f,
                                    isCalibrating = false,
                                    calibrationProgress = 1f
                                )
                            )
                        }
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        lastAccelerometer = event.values.clone()

                        if (activeRotationSensor == null) {
                            val x = event.values[0]
                            val y = event.values[1]
                            val z = event.values[2]

                            val pitch = atan2(-x.toDouble(), sqrt((y * y + z * z).toDouble()))
                                .times(180.0 / kotlin.math.PI).toFloat()
                            val roll = atan2(y.toDouble(), z.toDouble())
                                .times(180.0 / kotlin.math.PI).toFloat()

                            if (!isCalibrated) {
                                pitchSum += pitch
                                rollSum += roll
                                calibrationCount++
                                
                                if (calibrationCount >= CALIBRATION_FRAMES) {
                                    pitchOffset = (pitchSum / calibrationCount).toFloat()
                                    rollOffset = (rollSum / calibrationCount).toFloat()
                                    isCalibrated = true
                                }
                                
                                trySend(
                                    SensorData(
                                        tiltX = 0f,
                                        tiltY = 0f,
                                        alpha = 0f,
                                        beta = 0f,
                                        gamma = 0f,
                                        accX = 0f,
                                        accY = 0f,
                                        accZ = 0f,
                                        isCalibrating = true,
                                        calibrationProgress = calibrationCount.toFloat() / CALIBRATION_FRAMES
                                    )
                                )
                            } else {
                                val calibratedPitch = pitch - pitchOffset
                                val calibratedRoll = roll - rollOffset
                                
                                val tiltX = (calibratedRoll / TILT_SCALE).coerceIn(-1f, 1f)
                                val tiltY = (-calibratedPitch / TILT_SCALE).coerceIn(-1f, 1f)

                                trySend(
                                    SensorData(
                                        tiltX = tiltX,
                                        tiltY = tiltY,
                                        alpha = 0f,
                                        beta = calibratedPitch,
                                        gamma = calibratedRoll,
                                        accX = x,
                                        accY = y,
                                        accZ = z,
                                        isCalibrating = false,
                                        calibrationProgress = 1f
                                    )
                                )
                            }
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                Log.d(TAG, "Precision del sensor ${sensor.name}: $accuracy")
            }
        }

        activeRotationSensor?.let {
            Log.i(TAG, "Registrando sensor de rotacion: ${it.name}")
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }

        accelerometer?.let {
            Log.i(TAG, "Registrando acelerometro: ${it.name}")
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }

        awaitClose {
            Log.i(TAG, "Desregistrando sensores")
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
