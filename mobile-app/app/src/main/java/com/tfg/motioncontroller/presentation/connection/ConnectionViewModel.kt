package com.tfg.motioncontroller.presentation.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tfg.motioncontroller.data.local.SettingsDataStore
import com.tfg.motioncontroller.domain.model.ConnectionStatus
import com.tfg.motioncontroller.domain.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/************************************************************************************************
 * ConnectionViewModel: actúa como el puente entre la pantalla de login (UI) y la logica de red.
 * Aquí manejamos el estado de la conexión y nos aseguramos de que los dtos del servidor
 * se guarden correctamente cuando el usuario intenta conectar.
 ***********************************************************************************************/
@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val gameRepository: GameRepository, // Donde vive la lógica real del Socket
    private val settingsDataStore: SettingsDataStore // Para recordar la IP y el Puerto
) : ViewModel() {

    /************************************************************************************
     * Estado de la conexión (CONECTANDO, CONECTADO, ERROR...).
     * Usamos 'stateIn' para convertir el flujo del repositorio en un estado que la UI
     * pueda leer fácilmente, manteniendo el último valor incluso si la pantalla se rota.
     ************************************************************************************/
    val connectionStatus: StateFlow<ConnectionStatus> = gameRepository.connectionStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Mantiene el flujo vivo 5s tras cerrar la pantalla
            initialValue = ConnectionStatus()
        )

    // Recuperamos la última IP que funcionó para no obligar al usuario a escribirla siempre.
    val lastServerIp: StateFlow<String> = settingsDataStore.lastServerIpFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    // Recuperamos el último puerto guardado (por defecto 3000 si es la primera vez).
    val lastServerPort: StateFlow<Int> = settingsDataStore.lastServerPortFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 3000
        )

    /**********************************************************************************
     * Iniciación del Proceso de Conexión.
     * Antes de conectar, guardamos los datos en el DataStore para que la próxima vez
     * aparezcan automáticamente en los campos de texto.
     **********************************************************************************/
    fun connect(serverIp: String, port: Int) {
        viewModelScope.launch {
            // Guardamos "en segundo plano" para no congelar la app
            settingsDataStore.saveLastServerIp(serverIp)
            settingsDataStore.saveLastServerPort(port)
        }
        // Le pedimos al repositorio que abra el túnel de comunicación
        gameRepository.connect(serverIp, port)
    }

    // Permite guardar el puerto de forma independente
    // Cuando el usuario pulsa el 'check' en la UI).
    fun saveServerPort(port: Int) {
        viewModelScope.launch {
            settingsDataStore.saveLastServerPort(port)
        }
    }

    // Corta la conexión actual de forma limpia.
    fun disconnect() {
        gameRepository.disconnect()
    }
}
