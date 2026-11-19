package com.mokshith.jarvis.ai.memory

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_settings_v1")

data class JarvisAppSettings(
    val adaptiveBatteryMode: Boolean = true,
    val wifiOnlyBackgroundTasks: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val memoryAutoInjectionEnabled: Boolean = true,
    val maxAgentSteps: Int = 8,
    val selectedSafTreeUri: String? = null,
    val strictPrivacyMode: Boolean = false
)

class AppSettingsStore(private val context: Context) {
    private object Keys {
        val ADAPTIVE_BATTERY = booleanPreferencesKey("adaptive_battery_mode")
        val WIFI_ONLY_BG = booleanPreferencesKey("wifi_only_bg")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val MEMORY_INJECTION = booleanPreferencesKey("memory_auto_injection")
        val MAX_AGENT_STEPS = intPreferencesKey("max_agent_steps")
        val SAF_TREE_URI = stringPreferencesKey("saf_tree_uri")
        val STRICT_PRIVACY = booleanPreferencesKey("strict_privacy_mode")
    }

    val settingsFlow: Flow<JarvisAppSettings> = context.jarvisSettingsDataStore.data.map { prefs ->
        JarvisAppSettings(
            adaptiveBatteryMode = prefs[Keys.ADAPTIVE_BATTERY] ?: true,
            wifiOnlyBackgroundTasks = prefs[Keys.WIFI_ONLY_BG] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            memoryAutoInjectionEnabled = prefs[Keys.MEMORY_INJECTION] ?: true,
            maxAgentSteps = prefs[Keys.MAX_AGENT_STEPS] ?: 8,
            selectedSafTreeUri = prefs[Keys.SAF_TREE_URI],
            strictPrivacyMode = prefs[Keys.STRICT_PRIVACY] ?: false
        )
    }

    suspend fun updateAdaptiveBattery(enabled: Boolean) {
        context.jarvisSettingsDataStore.edit { it[Keys.ADAPTIVE_BATTERY] = enabled }
    }

    suspend fun updateWifiOnlyBackground(enabled: Boolean) {
        context.jarvisSettingsDataStore.edit { it[Keys.WIFI_ONLY_BG] = enabled }
    }

    suspend fun updateNotifications(enabled: Boolean) {
        context.jarvisSettingsDataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun updateMemoryAutoInjection(enabled: Boolean) {
        context.jarvisSettingsDataStore.edit { it[Keys.MEMORY_INJECTION] = enabled }
    }

    suspend fun updateMaxAgentSteps(steps: Int) {
        context.jarvisSettingsDataStore.edit { it[Keys.MAX_AGENT_STEPS] = steps.coerceIn(2, 20) }
    }

    suspend fun updateSafTreeUri(uri: String?) {
        context.jarvisSettingsDataStore.edit {
            if (uri == null) it.remove(Keys.SAF_TREE_URI) else it[Keys.SAF_TREE_URI] = uri
        }
    }

    suspend fun updateStrictPrivacy(enabled: Boolean) {
        context.jarvisSettingsDataStore.edit { it[Keys.STRICT_PRIVACY] = enabled }
    }
}
