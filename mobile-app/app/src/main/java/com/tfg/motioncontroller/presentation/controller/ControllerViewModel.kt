package com.tfg.motioncontroller.presentation.controller

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tfg.motioncontroller.data.audio.BlowDetector
import com.tfg.motioncontroller.data.local.SettingsDataStore
import com.tfg.motioncontroller.data.local.VibrationManager
import com.tfg.motioncontroller.data.network.InputMessage
import com.tfg.motioncontroller.data.network.ServerMessage
import com.tfg.motioncontroller.data.sensor.SensorDataSource
import com.tfg.motioncontroller.domain.model.ConnectionStatus
import com.tfg.motioncontroller.domain.model.GameSettings
import com.tfg.motioncontroller.domain.model.MicrophoneState
import com.tfg.motioncontroller.domain.model.SensorValues
import com.tfg.motioncontroller.domain.model.SocketState
import com.tfg.motioncontroller.domain.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class ControllerViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val sensorDataSource: SensorDataSource,
    private val blowDetector: BlowDetector,
    private val vibrationManager: VibrationManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControllerUiState())
    val uiState: StateFlow<ControllerUiState> = _uiState.asStateFlow()

    private var reconnectJob: kotlinx.coroutines.Job? = null
    private var sensorsJob: kotlinx.coroutines.Job? = null
    private var serverIp: String = ""
    private var serverPort: Int = 3000

    // Estado actual del input (se envia periodicamente)
    private var currentInput = InputMessage()
    private val anguloMaximo = 40.0f

    init {
        viewModelScope.launch {
            var wasConnected = false
            
            gameRepository.connectionStatus.collect { status ->
                _uiState.value = _uiState.value.copy(
                    connectionStatus = status,
                    playerId = status.playerId
                )
                
                when (status.socketState) {
                    SocketState.CONNECTED -> {
                        wasConnected = true
                        stopAutoReconnect()
                    }
                    SocketState.DISCONNECTED, SocketState.ERROR -> {
                        // Iniciar cuenta atras si previamente estabamos conectados
                        if (wasConnected) {
                            startAutoReconnect()
                            wasConnected = false
                        }
                    }
                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            blowDetector.blowState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    microphoneState = MicrophoneState(
                        isActive = state.isDetecting,
                        rmsLevel = state.rmsLevel,
                        isBlowing = state.isBlowing,
                        threshold = state.threshold,
                        cooldown = state.cooldown.toInt(),
                        scale = state.scale
                    )
                )
                currentInput = currentInput.copy(isYelling = state.isBlowing)
            }
        }

        viewModelScope.launch {
            gameRepository.connectionStatus.collect { status ->
                status.lastEvent?.let { event ->
                    handleServerEvent(event)
                }
            }
        }
    }

    fun connect(serverIp: String, port: Int = 3000) {
        this.serverIp = serverIp
        this.serverPort = port
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                connectionStatus = _uiState.value.connectionStatus.copy(
                    serverUrl = "$serverIp:$port"
                )
            )
            gameRepository.connect(serverIp, port)
        }
    }

    fun disconnect() {
        stopAutoReconnect()
        stopMicrophone()
        stopSensors()
        gameRepository.disconnect()
    }

    // ===== CUENTA ATRAS PARA VOLVER AL MENU =====
    private fun startAutoReconnect() {
        if (reconnectJob?.isActive == true) return
        
        reconnectJob = viewModelScope.launch {
            // Cuenta atrás de 5 segundos
            for (i in 5 downTo 1) {
                _uiState.value = _uiState.value.copy(
                    reconnectCountdown = i
                )
                delay(1000)
            }
            
            // Llegó a 0 - notificar al UI para volver al menú
            _uiState.value = _uiState.value.copy(
                reconnectCountdown = 0
            )
            // No llamar a disconnect() aquí, el Screen se encargará
            // de navegar al menú y luego llamar a disconnect()
        }
    }

    private fun stopAutoReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        _uiState.value = _uiState.value.copy(
            reconnectCountdown = null
        )
    }

    // ===== SENSORES =====
    fun startSensors() {
        if (!sensorDataSource.hasRequiredSensors()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Sensores no disponibles en este dispositivo"
            )
            return
        }

        // Cancelar job previo si existe
        sensorsJob?.cancel()
        sensorsJob = null

        sensorsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                sensorsActive = true,
                isCalibrating = true
            )

            sensorDataSource.sensorDataFlow()
                .sample(50L) // 20 FPS como la web
                .collect { data ->
                    // Normalizar gamma/beta como en la web (-1 a 1)
                    val gamma = (data.gamma.coerceIn(-anguloMaximo, anguloMaximo) / anguloMaximo)
                    val beta = (data.beta.coerceIn(-anguloMaximo, anguloMaximo) / anguloMaximo)

                    _uiState.value = _uiState.value.copy(
                        sensorValues = SensorValues(
                            tiltX = data.tiltX,
                            tiltY = data.tiltY,
                            alpha = data.alpha,
                            beta = beta,
                            gamma = gamma,
                            accX = data.accX,
                            accY = data.accY,
                            accZ = data.accZ
                        ),
                        isCalibrating = data.isCalibrating,
                        calibrationProgress = data.calibrationProgress
                    )

                    // Actualizar input actual
                    currentInput = currentInput.copy(
                        gamma = gamma,
                        beta = beta
                    )

                    // Enviar al servidor solo si esta conectado y tiene playerId
                    if (_uiState.value.connectionStatus.socketState == SocketState.CONNECTED &&
                        _uiState.value.playerId != null &&
                        !data.isCalibrating) {
                        gameRepository.sendInput(currentInput)
                    }
                }
        }
    }

    fun stopSensors() {
        sensorsJob?.cancel()
        sensorsJob = null

        // Resetear valores de sensores
        currentInput = currentInput.copy(gamma = 0f, beta = 0f)
        
        // Enviar input reseteado al servidor
        sendCurrentInput()

        _uiState.value = _uiState.value.copy(
            sensorsActive = false,
            isCalibrating = false,
            sensorValues = SensorValues()
        )
    }

    // ===== BOTONES =====
    fun setButtonA(pressed: Boolean) {
        currentInput = currentInput.copy(btnA = pressed)
        if (pressed) vibrationManager.vibrateClick()
        sendCurrentInput()
    }

    fun setButtonB(pressed: Boolean) {
        currentInput = currentInput.copy(btnB = pressed)
        if (pressed) vibrationManager.vibrateClick()
        sendCurrentInput()
    }

    private fun sendCurrentInput() {
        if (_uiState.value.connectionStatus.socketState == SocketState.CONNECTED &&
            _uiState.value.playerId != null) {
            gameRepository.sendInput(currentInput)
        }
    }

    // ===== MICROFONO =====
    fun startMicrophone() {
        try {
            blowDetector.startDetection(
                onBlowStart = { volume ->
                    _uiState.value = _uiState.value.copy(
                        microphoneState = _uiState.value.microphoneState.copy(isBlowing = true)
                    )
                    currentInput = currentInput.copy(isYelling = true)
                    vibrationManager.vibrate(VibrationManager.PATTERN_BLOW)
                    sendCurrentInput()
                },
                onBlowEnd = { volume ->
                    _uiState.value = _uiState.value.copy(
                        microphoneState = _uiState.value.microphoneState.copy(isBlowing = false)
                    )
                    currentInput = currentInput.copy(isYelling = false)
                    sendCurrentInput()
                }
            )
        } catch (e: SecurityException) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Permiso de microfono denegado"
            )
        }
    }

    fun stopMicrophone() {
        blowDetector.stopDetection()
        currentInput = currentInput.copy(isYelling = false)
        _uiState.value = _uiState.value.copy(
            microphoneState = MicrophoneState()
        )
    }

    fun showPermissionDenied() {
        _uiState.value = _uiState.value.copy(
            errorMessage = "Permiso de microfono denegado. Activalo en ajustes del sistema."
        )
    }

    fun updateMicrophoneSettings(threshold: Float, cooldown: Int, scale: Float) {
        blowDetector.updateSettings(
            threshold = threshold,
            cooldown = cooldown.toLong(),
            scale = scale
        )
    }

    // ===== VIBRACION =====
    fun testVibration() {
        vibrationManager.vibrateLong(5000)
    }

    fun vibrateOnButtonPress() {
        vibrationManager.vibrateClick()
    }

    // ===== EVENTOS DEL SERVIDOR =====
    private fun handleServerEvent(event: ServerMessage) {
        when (event) {
            is ServerMessage.Collision -> {
                _uiState.value = _uiState.value.copy(
                    logMessage = "Choque con ${event.objectName ?: "objeto"}"
                )
                vibrationManager.vibrate(longArrayOf(1001L))
            }
            is ServerMessage.Death -> {
                _uiState.value = _uiState.value.copy(
                    logMessage = "Has muerto!"
                )
                vibrationManager.vibrate(longArrayOf(500L, 100L, 401L))
            }
            is ServerMessage.Pickup -> {
                _uiState.value = _uiState.value.copy(
                    logMessage = "Recogiste: ${event.pickupType ?: "item"}"
                )
                vibrationManager.vibrate(longArrayOf(1001L))
            }
            is ServerMessage.UIUpdate -> {
                _uiState.value = _uiState.value.copy(
                    logMessage = event.data ?: ""
                )
            }
            is ServerMessage.PuzzleStart -> {
                _uiState.value = _uiState.value.copy(
                    logMessage = "Puzzle: ${event.puzzleId ?: ""}"
                )
                vibrationManager.vibrate(longArrayOf(1001L))
            }
            is ServerMessage.Custom -> {
                _uiState.value = _uiState.value.copy(
                    logMessage = event.data ?: ""
                )
                vibrationManager.vibrate(longArrayOf(1001L))
            }
            is ServerMessage.ScreenEffect -> {
                _uiState.value = _uiState.value.copy(
                    logMessage = "Efecto de pantalla"
                )
                event.vibrate?.let { if (it) vibrationManager.vibrate(longArrayOf(1001L)) }
            }
            else -> {}
        }
    }

    // ===== ERRORES =====
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // ===== LIFECYCLE =====
    fun onAppBackground() {
        if (_uiState.value.sensorsActive) {
            stopSensors()
            _uiState.value = _uiState.value.copy(
                errorMessage = "Sensores pausados (app en background)"
            )
        }
    }

    fun onAppForeground() {
        if (_uiState.value.errorMessage?.contains("background") == true) {
            clearError()
        }
    }

    data class ControllerUiState(
        val connectionStatus: ConnectionStatus = ConnectionStatus(),
        val playerId: Int? = null,
        val sensorsActive: Boolean = false,
        val sensorValues: SensorValues = SensorValues(),
        val microphoneState: MicrophoneState = MicrophoneState(),
        val gameSettings: GameSettings = GameSettings(),
        val errorMessage: String? = null,
        val logMessage: String? = null,
        val isCalibrating: Boolean = false,
        val calibrationProgress: Float = 0f,
        val reconnectCountdown: Int? = null
    )
}