package com.example.phonequery.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 应用设置 DataStore：保存来电悬浮窗、自动挂断等开关状态
 */
class SettingsDataStore(context: Context) {

    private val dataStore = context.dataStore

    companion object {
        val ENABLE_FLOATING_WINDOW = booleanPreferencesKey("enable_floating_window")
        val ENABLE_AUTO_HANGUP = booleanPreferencesKey("enable_auto_hangup")
        val ENABLE_BLACKLIST_ONLY = booleanPreferencesKey("enable_blacklist_only")
        val ENABLE_SPAM_AUTO_HANGUP = booleanPreferencesKey("enable_spam_auto_hangup")
        val ENABLE_BOOT_START = booleanPreferencesKey("enable_boot_start")
        val ENABLE_JOB_HUNT_MODE = booleanPreferencesKey("enable_job_hunt_mode")
        val ENABLE_SILENCE_UNKNOWN = booleanPreferencesKey("enable_silence_unknown")
        val ENABLE_CALL_SCREENING = booleanPreferencesKey("enable_call_screening")
        val ENABLE_BLOCK_NON_CONTACTS = booleanPreferencesKey("enable_block_non_contacts")
        /** 在线查询总开关：默认关闭（离线优先，保护隐私，不把号码发给第三方） */
        val ENABLE_ONLINE_LOOKUP = booleanPreferencesKey("enable_online_lookup")
        /** 拦截动作：block=拒接 / log=放行仅记录 */
        val INTERCEPT_ACTION = stringPreferencesKey("intercept_action")
        /** 悬浮窗透明度 0.3~1.0 */
        val FLOATING_ALPHA = floatPreferencesKey("floating_alpha")
        val HAS_SEEN_SETUP_GUIDE = booleanPreferencesKey("has_seen_setup_guide")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            enableFloatingWindow = prefs[ENABLE_FLOATING_WINDOW] ?: false,
            enableAutoHangup = prefs[ENABLE_AUTO_HANGUP] ?: false,
            enableBlacklistOnly = prefs[ENABLE_BLACKLIST_ONLY] ?: false,
            enableSpamAutoHangup = prefs[ENABLE_SPAM_AUTO_HANGUP] ?: false,
            enableBootStart = prefs[ENABLE_BOOT_START] ?: false,
            enableJobHuntMode = prefs[ENABLE_JOB_HUNT_MODE] ?: false,
            enableSilenceUnknown = prefs[ENABLE_SILENCE_UNKNOWN] ?: false,
            enableCallScreening = prefs[ENABLE_CALL_SCREENING] ?: false,
            enableBlockNonContacts = prefs[ENABLE_BLOCK_NON_CONTACTS] ?: false,
            enableOnlineLookup = prefs[ENABLE_ONLINE_LOOKUP] ?: false,
            interceptAction = prefs[INTERCEPT_ACTION] ?: AppSettings.INTERCEPT_BLOCK,
            floatingAlpha = prefs[FLOATING_ALPHA] ?: 0.9f
        )
    }

    val hasSeenSetupGuide: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[HAS_SEEN_SETUP_GUIDE] ?: false
    }

    suspend fun markSetupGuideSeen() {
        dataStore.edit { it[HAS_SEEN_SETUP_GUIDE] = true }
    }

    suspend fun updateFloatingWindow(enabled: Boolean) {
        dataStore.edit { it[ENABLE_FLOATING_WINDOW] = enabled }
    }

    suspend fun updateAutoHangup(enabled: Boolean) {
        dataStore.edit { it[ENABLE_AUTO_HANGUP] = enabled }
    }

    suspend fun updateBlacklistOnly(enabled: Boolean) {
        dataStore.edit { it[ENABLE_BLACKLIST_ONLY] = enabled }
    }

    suspend fun updateSpamAutoHangup(enabled: Boolean) {
        dataStore.edit { it[ENABLE_SPAM_AUTO_HANGUP] = enabled }
    }

    suspend fun updateBootStart(enabled: Boolean) {
        dataStore.edit { it[ENABLE_BOOT_START] = enabled }
    }

    suspend fun updateJobHuntMode(enabled: Boolean) {
        dataStore.edit { it[ENABLE_JOB_HUNT_MODE] = enabled }
    }

    suspend fun updateSilenceUnknown(enabled: Boolean) {
        dataStore.edit { it[ENABLE_SILENCE_UNKNOWN] = enabled }
    }

    suspend fun updateCallScreening(enabled: Boolean) {
        dataStore.edit { it[ENABLE_CALL_SCREENING] = enabled }
    }

    suspend fun updateBlockNonContacts(enabled: Boolean) {
        dataStore.edit { it[ENABLE_BLOCK_NON_CONTACTS] = enabled }
    }

    suspend fun updateOnlineLookup(enabled: Boolean) {
        dataStore.edit { it[ENABLE_ONLINE_LOOKUP] = enabled }
    }

    suspend fun updateInterceptAction(action: String) {
        dataStore.edit { it[INTERCEPT_ACTION] = action }
    }

    suspend fun updateFloatingAlpha(alpha: Float) {
        dataStore.edit { it[FLOATING_ALPHA] = alpha.coerceIn(0.3f, 1.0f) }
    }
}

data class AppSettings(
    val enableFloatingWindow: Boolean = false,
    val enableAutoHangup: Boolean = false,
    val enableBlacklistOnly: Boolean = false,
    val enableSpamAutoHangup: Boolean = false,
    val enableBootStart: Boolean = false,
    val enableJobHuntMode: Boolean = false,
    val enableSilenceUnknown: Boolean = false,
    val enableCallScreening: Boolean = false,
    val enableBlockNonContacts: Boolean = false,
    val enableOnlineLookup: Boolean = false,
    val interceptAction: String = INTERCEPT_BLOCK,
    val floatingAlpha: Float = 0.9f
) {
    companion object {
        const val INTERCEPT_BLOCK = "block"
        const val INTERCEPT_LOG = "log"
    }
}