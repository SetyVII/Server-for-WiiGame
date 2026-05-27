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


/**************************************************************************
 * ControllerViewModel: es el "motor" del mando. 
 * Se encarga de recoger los datos de los sensores (acelerómetro, micro) y los botones,
 * y enviarlos al servidor Java para que el juego en Unity reaccione.
 **************************************************************************/
@OptIn(FlowPreview::class)
@HiltViewModel
class ControllerViewModel @Inject constructor(
    private val gameRepository: GameRepository,   // Conexión con el servidor
    private val sensorDataSource: SensorDataSource, // Lectura de inclinación del móvil
    private val blowDetector: BlowDetector,        // Detección de soplidos en el micro
    private val vibrationManager: VibrationManager, // Feedback táctil (vibración)
    private val settingsDataStore: SettingsDataStore // Configuración (sensibilidad, modo...)
) : ViewModel() {

    // El estado único de la pantalla. Si algo cambia aquí, la UI se redibuja sola.
    private val _uiState = MutableStateFlow(ControllerUiState())
    val uiState: StateFlow<ControllerUiState> = _uiState.asStateFlow()

    // Trabajos en segundo plano para no bloquear la app
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private var sensorsJob: kotlinx.coroutines.Job? = null
    
    // Datos de conexión actuales.
    private var serverIp: String = ""
    private var serverPort: Int = 3000

    // El "paquete" de datos que enviamos al servidor constantemente
    private var currentInput = InputMessage()
    private val anguloMaximo = 40.0f // Grados de inclinación para llegar al tope de movimiento

    init {
        // Al arrancar, cargamos los ajustes del usuario
        loadSettings()
        
        /***********************************************************
         * Escuchamos el estado de la conexión en tiempo real.
         * Si se cae el WiFi, intentamos reconectar automáticamente.
         ***********************************************************/
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
                        // Si estábamos jugando y se corta, intentamos volver a entrar
                        if (wasConnected) {
                            startAutoReconnect()
                            wasConnected = false
                        }
                    }
                    else -> {}
                }

                // Procesamos mensajes que vienen de Unity (choques, monedas, etc.)
                status.lastEvent?.let { event ->
                    handleServerEvent(event)
                }
            }
        }

        // Escuchamos al micrófono.
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

    /**************************************************************************
     * CARGA CONFIGURACIÓN GUARDADA:
     **************************************************************************/
    private fun loadSettings() {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(settings = settings)
                // Si cambiamos ajustes de micro, los aplicamos al detector
                blowDetector.updateSettings(
                    threshold = settings.micThreshold,
                    cooldown = settings.micCooldown.toLong(),
                    scale = settings.micScale
                )
            }
        }
    }

    /**************************************************************************
     * GESTIÓN DE SENSORES:
     * Activa los sensores del móvil para usarlos como mando.
     * Incluye una pequeña fase de calibración para ignorar la inclinación inicial.
     **************************************************************************/
    fun startSensors() {
        if (!sensorDataSource.hasRequiredSensors()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Sensores no disponibles en este dispositivo"
            )
            return
        }

        sensorsJob?.cancel()
        sensorsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                sensorsActive = true,
                isCalibrating = true
            )

            sensorDataSource.sensorDataFlow()
                .sample(50L) // Enviamos datos cada 50ms (20 veces por segundo) para no saturar
                .collect { data ->
                    // Mapeamos los grados a un rango de -1 a 1 para Unity
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

                    // Solo enviamos al servidor si ya ha terminado de calibrar
                    if (!data.isCalibrating) {
                        gameRepository.sendInput(currentInput)
                    }
                }
        }
    }

    fun stopSensors() {
        sensorsJob?.cancel()
        _uiState.value = _uiState.value.copy(sensorsActive = false)
        // Reset input
        currentInput = currentInput.copy(gamma = 0f, beta = 0f)
        gameRepository.sendInput(currentInput)
    }

    /**************************************************************************
     * GESTIÓN DE MICROFONO:
     **************************************************************************/
    fun startMicrophone() {
        viewModelScope.launch {
            blowDetector.startDetection(
                onBlowStart = { /* Notificamos al servidor si es necesario, aunque ya lo hace el collector del blowState */ },
                onBlowEnd = { }
            )
        }
    }

    fun stopMicrophone() {
        viewModelScope.launch {
            blowDetector.stopDetection()
        }
    }

    fun updateMicrophoneSettings(threshold: Float, cooldown: Int, scale: Float) {
        val currentSettings = _uiState.value.settings.copy(
            micThreshold = threshold,
            micCooldown = cooldown,
            micScale = scale
        )
        _uiState.value = _uiState.value.copy(settings = currentSettings)
        
        viewModelScope.launch {
            settingsDataStore.saveSettings(currentSettings)
        }
        
        blowDetector.updateSettings(threshold, cooldown.toLong(), scale)
    }

    /**************************************************************************
     * RECONEXIÓN AUTOMÁTICA:
     **************************************************************************/
    private fun startAutoReconnect() {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            for (i in 5 downTo 1) {
                _uiState.value = _uiState.value.copy(reconnectCountdown = i)
                delay(1000)
            }
            _uiState.value = _uiState.value.copy(reconnectCountdown = null)
            connect(serverIp, serverPort)
        }
    }

    private fun stopAutoReconnect() {
        reconnectJob?.cancel()
        _uiState.value = _uiState.value.copy(reconnectCountdown = null)
    }

    fun connect(ip: String, port: Int) {
        serverIp = ip
        serverPort = port
        viewModelScope.launch {
            gameRepository.connect(ip, port)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            gameRepository.disconnect()
            stopSensors()
            stopMicrophone()
        }
    }

    /*****************************************************************
     * bIDIRECCIONALIDAD:
     * Responde a los eventos que nos manda el juego desde el PC.
     *****************************************************************/
    private fun handleServerEvent(event: ServerMessage) {
        when (event) {
            is ServerMessage.Collision -> {
                vibrationManager.vibrate(longArrayOf(0, 100)) // Vibración corta al chocar
                _uiState.value = _uiState.value.copy(logMessage = "¡Colisión!")
            }
            is ServerMessage.Death -> {
                vibrationManager.vibrate(longArrayOf(0, 500, 200, 500)) // Vibración doble al morir
                _uiState.value = _uiState.value.copy(logMessage = "¡Has muerto!")
            }
            is ServerMessage.Pickup -> {
                vibrationManager.vibrate(longArrayOf(0, 50)) // Vibración muy leve al coger moneda
                _uiState.value = _uiState.value.copy(
                    pickupCount = _uiState.value.pickupCount + 1,
                    logMessage = "${event.pickupType ?: "Objeto"} recogido!"
                )
            }
            is ServerMessage.Error -> {
                _uiState.value = _uiState.value.copy(errorMessage = event.message)
            }
            else -> {}
        }
    }

    /**********************************************************
     * MODOS DE CONTROL (DPAD / TOUCHPAD / BOTONES)
     **********************************************************/
    fun setControlMode(mode: ControlMode) {
        val currentSettings = _uiState.value.settings
        _uiState.value = _uiState.value.copy(
            settings = currentSettings.copy(controlMode = mode)
        )
        // Reset de seguridad
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

    fun setDPadButton(x: Int, y: Int) {
        currentInput = currentInput.copy(
            dpadX = x,
            dpadY = y,
            gamma = x.toFloat(), // Valor formateado para que Unity lo entienda como movimiento.
            beta = 0f
        )
        if (_uiState.value.connectionStatus.socketState == SocketState.CONNECTED) {
            gameRepository.sendInput(currentInput)
        }
    }

    fun resetDPadButton() {
        currentInput = currentInput.copy(
            dpadX = 0, 
            dpadY = 0,
            gamma = 0f,
            beta = 0f
        )
        if (_uiState.value.connectionStatus.socketState == SocketState.CONNECTED) {
            gameRepository.sendInput(currentInput)
        }
    }

    fun setButtonA(pressed: Boolean) {
        currentInput = currentInput.copy(btnA = pressed)
        gameRepository.sendInput(currentInput)
    }

    fun setButtonB(pressed: Boolean) {
        currentInput = currentInput.copy(btnB = pressed)
        gameRepository.sendInput(currentInput)
    }

    fun setManualTilt(gamma: Float, beta: Float) {
        currentInput = currentInput.copy(gamma = gamma, beta = beta)
        gameRepository.sendInput(currentInput)
    }

    fun resetManualTilt() {
        currentInput = currentInput.copy(gamma = 0f, beta = 0f)
        gameRepository.sendInput(currentInput)
    }

    /**********************************************************
     * OTROS:
     **********************************************************/
    fun testVibration() {
        vibrationManager.vibrate(longArrayOf(0, 200, 100, 200))
    }

    fun showPermissionDenied() {
        _uiState.value = _uiState.value.copy(snackbarMessage = "Permiso de micrófono denegado")
    }

    fun clearSnackbarMessage() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    // --- Ciclo de Vida ---
    fun onAppForeground() {
        loadSettings()
    }

    fun onAppBackground() {
        stopSensors()
        stopMicrophone()
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
