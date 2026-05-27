package com.tfg.motioncontroller.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tfg.motioncontroller.domain.model.ControlMode
import com.tfg.motioncontroller.domain.model.GameSettings
import com.tfg.motioncontroller.domain.model.SensitivityLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wii_cell_settings")

/***********************************************************************************
 * SettingsDataStore es el "cerebro" de la persistencia de configuración.
 * Usamos Jetpack DataStore en lugar de SharedPreferences porque es más seguro 
 * (corre en hilos de fondo) y funciona de forma reactiva con Flows.
 ***********************************************************************************/
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore


    companion object {
        // Claves para guardar los datos.
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val SENSITIVITY = stringPreferencesKey("sensitivity")
        val CUSTOM_FORCE = intPreferencesKey("custom_force")
        val CONTROL_MODE = stringPreferencesKey("control_mode")
        val LAST_SERVER_IP = stringPreferencesKey("last_server_ip")
        val LAST_SERVER_PORT = intPreferencesKey("last_server_port")
        val MIC_THRESHOLD = androidx.datastore.preferences.core.floatPreferencesKey("mic_threshold")
        val MIC_COOLDOWN = intPreferencesKey("mic_cooldown")
        val MIC_SCALE = androidx.datastore.preferences.core.floatPreferencesKey("mic_scale")
    }

    /***************************************************************************************
     * PARA LEER LA CONFIGURACIÓN DE LOS AJUSTES
     * Este Flow emite un objeto "GameSettings" completo cada vez que algo cambia.
     * Si no hay nada guardado, se usan valores por defecto (como el tema oscuro activado).
     ***************************************************************************************/
    val settingsFlow: Flow<GameSettings> = dataStore.data.map { preferences ->
        GameSettings(
            darkMode = preferences[DARK_MODE] ?: true,
            sensitivity = SensitivityLevel.valueOf(
                preferences[SENSITIVITY] ?: SensitivityLevel.MEDIUM.name
            ),
            // Nos aseguramos de que la fuerza esté siempre entre 1 y 100
            customForce = preferences[CUSTOM_FORCE] ?: 45,
            controlMode = try {
                ControlMode.valueOf(preferences[CONTROL_MODE] ?: ControlMode.TOUCHPAD.name)
            } catch (e: IllegalArgumentException) {
                ControlMode.TOUCHPAD // Si algo falla, volvemos al "modo seguro".
            },
            micThreshold = preferences[MIC_THRESHOLD] ?: 0.10f,
            micCooldown = preferences[MIC_COOLDOWN] ?: 800,
            micScale = preferences[MIC_SCALE] ?: 3.33f
        )
    }

    /***********************************************
     * PARA GUARDAR LA CONFIGURACIÓN DE LOS AJUSTES
     ***********************************************/
    suspend fun saveSettings(settings: GameSettings) {
        dataStore.edit { preferences ->
            preferences[DARK_MODE] = settings.darkMode
            preferences[SENSITIVITY] = settings.sensitivity.name
            preferences[CUSTOM_FORCE] = settings.customForce
            preferences[CONTROL_MODE] = settings.controlMode.name
            preferences[MIC_THRESHOLD] = settings.micThreshold
            preferences[MIC_COOLDOWN] = settings.micCooldown
            preferences[MIC_SCALE] = settings.micScale
        }
    }

    suspend fun saveDarkMode(darkMode: Boolean) {
        dataStore.edit { preferences ->
            preferences[DARK_MODE] = darkMode
        }
    }

    suspend fun saveSensitivity(sensitivity: SensitivityLevel) {
        dataStore.edit { preferences ->
            preferences[SENSITIVITY] = sensitivity.name
        }
    }

    suspend fun saveCustomForce(force: Int) {
        dataStore.edit { preferences ->
            preferences[CUSTOM_FORCE] = force.coerceIn(1, 100)
        }
    }

    suspend fun saveControlMode(mode: ControlMode) {
        dataStore.edit { preferences ->
            preferences[CONTROL_MODE] = mode.name
        }
    }

    /********************************************************************
     * Recuperamos la última IP (ara no obligar al usuario a escribirla).
     ********************************************************************/
    val lastServerIpFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[LAST_SERVER_IP] ?: ""
    }

    suspend fun saveLastServerIp(ip: String) {
        dataStore.edit { preferences ->
            preferences[LAST_SERVER_IP] = ip
        }
    }

    /***************************************************************
     * Leemos el puerto por separado. 
     * Por defecto el 3000 de WiiGames.
     ***************************************************************/
    val lastServerPortFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[LAST_SERVER_PORT] ?: 3000
    }

    suspend fun saveLastServerPort(port: Int) {
        dataStore.edit { preferences ->
            preferences[LAST_SERVER_PORT] = port
        }
    }
}
