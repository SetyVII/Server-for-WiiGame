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

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val connectionStatus: StateFlow<ConnectionStatus> = gameRepository.connectionStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConnectionStatus()
        )

    val lastServerIp: StateFlow<String> = settingsDataStore.lastServerIpFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    fun connect(serverIp: String, port: Int = 3000) {
        viewModelScope.launch {
            settingsDataStore.saveLastServerIp(serverIp)
        }
        gameRepository.connect(serverIp, port)
    }

    fun disconnect() {
        gameRepository.disconnect()
    }
}
