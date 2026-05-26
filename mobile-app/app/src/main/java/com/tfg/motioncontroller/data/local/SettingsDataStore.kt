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

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val SENSITIVITY = stringPreferencesKey("sensitivity")
        val CUSTOM_FORCE = intPreferencesKey("custom_force")
        val CONTROL_MODE = stringPreferencesKey("control_mode")
        val LAST_SERVER_IP = stringPreferencesKey("last_server_ip")
    }

    val settingsFlow: Flow<GameSettings> = dataStore.data.map { preferences ->
        GameSettings(
            darkMode = preferences[DARK_MODE] ?: true,
            sensitivity = SensitivityLevel.valueOf(
                preferences[SENSITIVITY] ?: SensitivityLevel.MEDIUM.name
            ),
            customForce = preferences[CUSTOM_FORCE] ?: 45,
            controlMode = try {
                ControlMode.valueOf(preferences[CONTROL_MODE] ?: ControlMode.TOUCHPAD.name)
            } catch (e: IllegalArgumentException) {
                ControlMode.TOUCHPAD
            }
        )
    }

    suspend fun saveSettings(settings: GameSettings) {
        dataStore.edit { preferences ->
            preferences[DARK_MODE] = settings.darkMode
            preferences[SENSITIVITY] = settings.sensitivity.name
            preferences[CUSTOM_FORCE] = settings.customForce
            preferences[CONTROL_MODE] = settings.controlMode.name
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

    val lastServerIpFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[LAST_SERVER_IP] ?: ""
    }

    suspend fun saveLastServerIp(ip: String) {
        dataStore.edit { preferences ->
            preferences[LAST_SERVER_IP] = ip
        }
    }
}
