package com.tfg.motioncontroller.presentation.controller

import android.os.Build
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
import com.tfg.motioncontroller.domain.model.ControlMode
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
        loadSettings()
        
        viewModelScope.launch {
            var wasConnected = false
            
            gameRepository.connectionStatus.collect { status ->
                Log.d("ControllerVM", "Estado de conexion: ${status.socketState}, wasConnected=$wasConnected")
                
                _uiState.value = _uiState.value.copy(
                    connectionStatus = status,
                    playerId = status.playerId
                )
                
                when (status.socketState) {
                    SocketState.CONNECTED -> {
                        Log.i("ControllerVM", "CONECTADO - Deteniendo reconexion")
                        wasConnected = true
                        if (_uiState.value.reconnectCountdown != null) {
                            _uiState.value = _uiState.value.copy(
                                reconnectCountdown = null,
                                snackbarMessage = "La conexion ha vuelto"
                            )
                        }
                        stopAutoReconnect()
                    }
                    SocketState.DISCONNECTED, SocketState.ERROR -> {
                        Log.w("ControllerVM", "DESCONEXION/ERROR detectado - wasConnected=$wasConnected")
                        if (wasConnected) {
                            Log.i("ControllerVM", "Iniciando reconexion automatica...")
                            startAutoReconnect()
                            wasConnected = false
                        }
                    }
                    else -> {}
                }

                status.lastEvent?.let { event ->
                    handleServerEvent(event)
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
            Log.i("ControllerVM", "Conectando a $serverIp:$port")
            gameRepository.connect(serverIp, port)
        }
    }

    fun disconnect() {
        Log.i("ControllerVM", "Desconectando manualmente")
        stopAutoReconnect()
        stopMicrophone()
        stopSensors()
        gameRepository.disconnect()
    }

    // ===== RECONEXION FALSA (solo visual) =====
    private fun startAutoReconnect() {
        if (reconnectJob?.isActive == true) return
        
        reconnectJob = viewModelScope.launch {
            // Mostrar "reconectando" por 3 segundos
            _uiState.value = _uiState.value.copy(
                snackbarMessage = "Reconectando..."
            )
            delay(3000)
            
            // Volver al menu
            _uiState.value = _uiState.value.copy(
                reconnectCountdown = 0,
                snackbarMessage = "Conexion perdida"
            )
        }
    }

    private fun stopAutoReconnect() {
        Log.d("ControllerVM", "Deteniendo reconexion")
        reconnectJob?.cancel()
        reconnectJob = null
        if (_uiState.value.reconnectCountdown != null) {
            _uiState.value = _uiState.value.copy(
                reconnectCountdown = null
            )
        }
    }

    fun clearSnackbarMessage() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    // ===== SENSORES =====
    fun startSensors() {
        if (!sensorDataSource.hasRequiredSensors()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Sensores no disponibles en este dispositivo"
            )
            return
        }

        sensorsJob?.cancel()
        sensorsJob = null

        sensorsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                sensorsActive = true,
                isCalibrating = true
            )

            sensorDataSource.sensorDataFlow()
                .sample(50L)
                .collect { data ->
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

                    currentInput = currentInput.copy(
                        gamma = gamma,
                        beta = beta
                    )

                    if (!data.isCalibrating) {
                        gameRepository.sendInput(currentInput)
                    }
                }
        }
    }

    fun stopSensors() {
        Log.d("ControllerVM", "Deteniendo sensores")
        sensorsJob?.cancel()
        sensorsJob = null
        _uiState.value = _uiState.value.copy(
            sensorsActive = false,
            isCalibrating = false,
            sensorValues = SensorValues()
        )
        currentInput = currentInput.copy(gamma = 0f, beta = 0f)
        gameRepository.sendInput(currentInput)
    }

    // ===== BOTONES =====
    fun setButtonA(pressed: Boolean) {
        currentInput = currentInput.copy(btnA = pressed)
        if (_uiState.value.connectionStatus.socketState == SocketState.CONNECTED) {
            gameRepository.sendInput(currentInput)
        }
    }

    fun setButtonB(pressed: Boolean) {
        currentInput = currentInput.copy(btnB = pressed)
        if (_uiState.value.connectionStatus.socketState == SocketState.CONNECTED) {
            gameRepository.sendInput(currentInput)
        }
    }

    // ===== D-PAD MANUAL =====
    fun setManualTilt(gamma: Float, beta: Float) {
        currentInput = currentInput.copy(
            gamma = gamma,
            beta = beta,
            dpadX = 0,
            dpadY = 0
        )
        if (_uiState.value.connectionStatus.socketState == SocketState.CONNECTED) {
            gameRepository.sendInput(currentInput)
        }
    }

    fun resetManualTilt() {
        currentInput = currentInput.copy(gamma = 0f, beta = 0f)
        if (_uiState.value.connectionStatus.socketState == SocketState.CONNECTED) {
            gameRepository.sendInput(currentInput)
        }
    }

    // ===== MODO BOTONES (D-Pad) =====
    fun setDPadButton(x: Int, y: Int) {
        currentInput = currentInput.copy(
            dpadX = x,
            dpadY = y,
            gamma = 0f,
            beta = 0f
        )
        if (_uiState.value.connectionStatus.socketState == SocketState.CONNECTED) {
            gameRepository.sendInput(currentInput)
        }
    }

    fun resetDPadButton() {
        currentInput = currentInput.copy(dpadX = 0, dpadY = 0)
        if (_uiState.value.connectionStatus.socketState == SocketState.CONNECTED) {
            gameRepository.sendInput(currentInput)
        }
    }

    fun setControlMode(mode: ControlMode) {
        val currentSettings = _uiState.value.settings
        _uiState.value = _uiState.value.copy(
            settings = currentSettings.copy(controlMode = mode)
        )
        // Resetear valores del modo anterior
        currentInput = currentInput.copy(
            gamma = 0f,
            beta = 0f,
            dpadX = 0,
            dpadY = 0
        )
        if (_uiState.value.connectionStatus.socketState == SocketState.CONNECTED) {
            gameRepository.sendInput(currentInput)
        }
        viewModelScope.launch {
            settingsDataStore.saveSettings(currentSettings.copy(controlMode = mode))
        }
    }

    // ===== MICROFONO =====
    fun startMicrophone() {
        blowDetector.startDetection(
            onBlowStart = { _ ->
                currentInput = currentInput.copy(isYelling = true)
            },
            onBlowEnd = { _ ->
                currentInput = currentInput.copy(isYelling = false)
            }
        )
    }

    fun stopMicrophone() {
        blowDetector.stopDetection()
        currentInput = currentInput.copy(isYelling = false)
    }

    fun updateMicrophoneSettings(threshold: Float, cooldown: Int, scale: Float) {
        blowDetector.updateSettings(threshold, cooldown.toLong(), scale)
    }

    fun showPermissionDenied() {
        _uiState.value = _uiState.value.copy(
            errorMessage = "Permiso de microfono denegado"
        )
    }

    // ===== EVENTOS DEL SERVIDOR =====
    private fun handleServerEvent(event: ServerMessage) {
        when (event) {
            is ServerMessage.Collision -> {
                vibrationManager.vibrate(longArrayOf(0, 100))
                _uiState.value = _uiState.value.copy(
                    logMessage = "Colision!"
                )
            }
            is ServerMessage.Death -> {
                vibrationManager.vibrate(longArrayOf(0, 500, 200, 500))
                _uiState.value = _uiState.value.copy(
                    logMessage = "Has muerto!"
                )
            }
            is ServerMessage.Pickup -> {
                vibrationManager.vibrate(longArrayOf(0, 50))
                _uiState.value = _uiState.value.copy(
                    pickupCount = _uiState.value.pickupCount + 1,
                    logMessage = "${event.pickupType ?: "Objeto"} recogido!"
                )
            }
            is ServerMessage.UIUpdate -> {
                _uiState.value = _uiState.value.copy(
                    logMessage = event.data ?: "UI Update"
                )
            }
            is ServerMessage.ScreenEffect -> {
                _uiState.value = _uiState.value.copy(
                    logMessage = "Efecto: ${event.color ?: "unknown"}"
                )
            }
            is ServerMessage.PuzzleStart -> {
                _uiState.value = _uiState.value.copy(
                    logMessage = "Puzzle iniciado!"
                )
            }
            is ServerMessage.Custom -> {
                _uiState.value = _uiState.value.copy(
                    logMessage = event.data ?: "Custom event"
                )
            }
            is ServerMessage.Error -> {
                _uiState.value = _uiState.value.copy(
                    errorMessage = event.message
                )
            }
            else -> {}
        }
    }

    fun testVibration() {
        viewModelScope.launch {
            // Secuencia simple de vibracion de test de 4 segundos:
            vibrationManager.vibrate(
                longArrayOf(
                    0, 1000,       // Start, vibrar 1000ms
                    500,           // Pausa 500ms
                    100, 50, 100, 50, 100, 50, 100,  // Zig-zag: 4 pulsos
                    500,           // Pausa 500ms
                    1000           // Vibrar 1000ms
                )
            )
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    settings = settings
                )
            }
        }
    }

    // ===== APP LIFECYCLE =====
    fun onAppBackground() {
        Log.i("ControllerVM", "App va a background - deteniendo sensores y microfono")
        stopSensors()
        stopMicrophone()
    }

    fun onAppForeground() {
        Log.i("ControllerVM", "App vuelve a foreground")
        // No reiniciar automaticamente - dejar que el usuario lo haga manualmente
    }

    fun saveSettings(settings: GameSettings) {
        viewModelScope.launch {
            settingsDataStore.saveSettings(settings)
        }
    }
}

data class ControllerUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus(),
    val playerId: Int? = null,
    val sensorValues: SensorValues = SensorValues(),
    val sensorsActive: Boolean = false,
    val isCalibrating: Boolean = false,
    val calibrationProgress: Float = 0f,
    val microphoneState: MicrophoneState = MicrophoneState(),
    val errorMessage: String? = null,
    val logMessage: String? = null,
    val settings: GameSettings = GameSettings(),
    val reconnectCountdown: Int? = null,
    val snackbarMessage: String? = null,
    val pickupCount: Int = 0
)
