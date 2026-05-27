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

/********************************************************************************************
 * SettingsViewModel: actúa como puente entre los ajustes y el almacenamiento persistente.
 * Se asegura de que cualquier cambio (como cambiar a modo oscuro o ajustar la sensibilidad)
 * se guarde correctamente en el dispositivo.
 ********************************************************************************************/
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore, // Almacén de datos (DataStore)
    private val gameRepository: GameRepository       // Referencia al repositorio
) : ViewModel() {

    // Exponemos los ajustes como un flujo de datos (Flow). 
    // La UI se actualizará sola al recibir cambios.
    val settings = settingsDataStore.settingsFlow

    /******************************************************************************
     * Guarda la nueva configuración. Se ejecuta en una corrutina para no bloquear
     * la interfaz de usuario mientras se escribe en el disco.
     ******************************************************************************/
    fun saveSettings(newSettings: GameSettings) {
        viewModelScope.launch {
            settingsDataStore.saveSettings(newSettings)
        }
    }
}
