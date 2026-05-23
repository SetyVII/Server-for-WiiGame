package com.tfg.motioncontroller.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tfg.motioncontroller.data.local.SettingsDataStore
import com.tfg.motioncontroller.domain.model.GameSettings
import com.tfg.motioncontroller.domain.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val gameRepository: GameRepository
) : ViewModel() {

    val settings = settingsDataStore.settingsFlow

    fun saveSettings(newSettings: GameSettings) {
        viewModelScope.launch {
            settingsDataStore.saveSettings(newSettings)
        }
    }
}
