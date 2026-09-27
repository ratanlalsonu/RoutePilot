package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.OperatingMode
import com.example.domain.repository.DriverPreferences
import com.example.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "routepilot_prefs")

class UserPreferencesDataStore(
    private val context: Context
) : PreferencesRepository {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_code")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val NAV_VOICE = booleanPreferencesKey("nav_voice_enabled")
        val ALERT_SOUND = booleanPreferencesKey("alert_sound_enabled")
        val USE_KM = booleanPreferencesKey("use_kilometers")
        val OPERATING_MODE = stringPreferencesKey("operating_mode")
        val REMEMBER_ME = booleanPreferencesKey("remember_me")
        val SAVED_EMAIL = stringPreferencesKey("saved_email")
    }

    override val preferencesFlow: Flow<DriverPreferences> = context.dataStore.data.map { prefs ->
        DriverPreferences(
            languageCode = prefs[Keys.LANGUAGE] ?: "en",
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            navigationVoiceEnabled = prefs[Keys.NAV_VOICE] ?: true,
            alertSoundEnabled = prefs[Keys.ALERT_SOUND] ?: true,
            useKilometers = prefs[Keys.USE_KM] ?: true,
            operatingMode = OperatingMode.LIVE,
            rememberMe = prefs[Keys.REMEMBER_ME] ?: true,
            savedEmail = prefs[Keys.SAVED_EMAIL] ?: ""
        )
    }

    override suspend fun setLanguage(languageCode: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = languageCode }
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    override suspend fun setNavigationVoiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NAV_VOICE] = enabled }
    }

    override suspend fun setAlertSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALERT_SOUND] = enabled }
    }

    override suspend fun setUseKilometers(useKm: Boolean) {
        context.dataStore.edit { it[Keys.USE_KM] = useKm }
    }

    override suspend fun setOperatingMode(mode: OperatingMode) {
        context.dataStore.edit { it[Keys.OPERATING_MODE] = mode.name }
    }

    override suspend fun setRememberMe(remember: Boolean, email: String) {
        context.dataStore.edit {
            it[Keys.REMEMBER_ME] = remember
            it[Keys.SAVED_EMAIL] = if (remember) email else ""
        }
    }
}
