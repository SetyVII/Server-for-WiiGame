package com.tfg.motioncontroller.data.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlowDetector @Inject constructor(
    private val audioRecorder: AudioRecorder
) {
    // Configuracion por defecto (igual que la web)
    companion object {
        const val DEFAULT_THRESHOLD = 0.10f
        const val DEFAULT_COOLDOWN_MS = 800L
        const val DEFAULT_SCALE = 3.33f
        const val BLOW_END_THRESHOLD_MULTIPLIER = 0.3f
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var detectionJob: Job? = null

    private var blowThreshold = DEFAULT_THRESHOLD
    private var blowCooldownMs = DEFAULT_COOLDOWN_MS
    private var barScale = DEFAULT_SCALE

    private var lastBlowTime = 0L
    private var isBlowing = false

    private val _blowState = MutableStateFlow(BlowState())
    val blowState: StateFlow<BlowState> = _blowState.asStateFlow()

    data class BlowState(
        val isDetecting: Boolean = false,
        val rmsLevel: Float = 0f,
        val isBlowing: Boolean = false,
        val volume: Float = 0f,
        val threshold: Float = DEFAULT_THRESHOLD,
        val cooldown: Long = DEFAULT_COOLDOWN_MS,
        val scale: Float = DEFAULT_SCALE
    )

    fun startDetection(
        onBlowStart: (Float) -> Unit,
        onBlowEnd: (Float) -> Unit
    ) {
        if (!audioRecorder.hasPermission()) {
            throw SecurityException("Permiso de microfono no concedido")
        }

        stopDetection()

        audioRecorder.startRecording { rms ->
            _blowState.value = _blowState.value.copy(rmsLevel = rms)

            val now = System.currentTimeMillis()
            val scaledVolume = (rms * 100 * barScale).coerceIn(0f, 100f)

            // Detectar inicio de soplado
            if (rms > blowThreshold && now - lastBlowTime > blowCooldownMs && !isBlowing) {
                lastBlowTime = now
                isBlowing = true
                _blowState.value = _blowState.value.copy(
                    isBlowing = true,
                    volume = scaledVolume
                )
                onBlowStart(scaledVolume)
            }

            // Detectar fin de soplado
            if (rms < blowThreshold * BLOW_END_THRESHOLD_MULTIPLIER && isBlowing) {
                isBlowing = false
                _blowState.value = _blowState.value.copy(
                    isBlowing = false,
                    volume = scaledVolume
                )
                onBlowEnd(scaledVolume)
            }
        }

        _blowState.value = _blowState.value.copy(isDetecting = true)
    }

    fun stopDetection() {
        audioRecorder.stopRecording()
        isBlowing = false
        _blowState.value = BlowState(
            threshold = blowThreshold,
            cooldown = blowCooldownMs,
            scale = barScale
        )
    }

    fun updateSettings(
        threshold: Float = blowThreshold,
        cooldown: Long = blowCooldownMs,
        scale: Float = barScale
    ) {
        blowThreshold = threshold
        blowCooldownMs = cooldown
        barScale = scale
        _blowState.value = _blowState.value.copy(
            threshold = threshold,
            cooldown = cooldown,
            scale = scale
        )
    }

    fun isActive(): Boolean = audioRecorder.isRecording.value
}
