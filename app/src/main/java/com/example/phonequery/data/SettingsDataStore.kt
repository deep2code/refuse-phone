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
        /** 聚合数据 juhe.cn 密钥：个人实名后获取，用于号码标记 + 归属地增强（留空则此源不生效） */
        val JUHE_KEY = stringPreferencesKey("juhe_key")
        /** 阿里云云市场「聚美智数」号码标记 APPCODE：个人购买后获取（留空则此源不生效） */
        val ALIYUN_MARK_APPCODE = stringPreferencesKey("aliyun_mark_appcode")
        /** 阿里云云市场「聚美智数」号码标记调用地址（不同商品路径不同，需按购买商品填写） */
        val ALIYUN_MARK_URL = stringPreferencesKey("aliyun_mark_url")
        /** 聚合数据 juhe.cn 网关地址（默认官方地址，可改为代理/自建网关；留空回落默认） */
        val JUHE_BASE_URL = stringPreferencesKey("juhe_base_url")
        /** 企查查开放平台网关地址（默认官方地址，可改为代理/自建网关；留空回落默认） */
        val QCC_BASE_URL = stringPreferencesKey("qcc_base_url")
        /** 百度爱企查开放 API 网关地址（默认官方地址，可改为代理/自建网关；留空回落默认） */
        val AIQICHA_BASE_URL = stringPreferencesKey("aiqicha_base_url")
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
            juheKey = prefs[JUHE_KEY] ?: "",
            aliyunMarkAppcode = prefs[ALIYUN_MARK_APPCODE] ?: "",
            aliyunMarkUrl = prefs[ALIYUN_MARK_URL] ?: "",
            juheBaseUrl = prefs[JUHE_BASE_URL]?.takeIf { it.isNotBlank() } ?: NetworkModule.DEFAULT_JUHE_BASE_URL,
            qccBaseUrl = prefs[QCC_BASE_URL]?.takeIf { it.isNotBlank() } ?: NetworkModule.DEFAULT_QCC_BASE_URL,
            aiqichaBaseUrl = prefs[AIQICHA_BASE_URL]?.takeIf { it.isNotBlank() } ?: NetworkModule.DEFAULT_AIQICHA_BASE_URL,
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

    suspend fun updateJuheKey(value: String) {
        dataStore.edit { it[JUHE_KEY] = value.trim() }
    }

    suspend fun updateAliyunMarkAppcode(value: String) {
        dataStore.edit { it[ALIYUN_MARK_APPCODE] = value.trim() }
    }

    suspend fun updateAliyunMarkUrl(value: String) {
        dataStore.edit { it[ALIYUN_MARK_URL] = value.trim() }
    }

    suspend fun updateJuheBaseUrl(value: String) {
        dataStore.edit { it[JUHE_BASE_URL] = normalizeBaseUrl(value) }
    }

    suspend fun updateQccBaseUrl(value: String) {
        dataStore.edit { it[QCC_BASE_URL] = normalizeBaseUrl(value) }
    }

    suspend fun updateAiqichaBaseUrl(value: String) {
        dataStore.edit { it[AIQICHA_BASE_URL] = normalizeBaseUrl(value) }
    }

    /** 归一化网关地址：去首尾空白并保证以 / 结尾（Retrofit 要求）；留空则回落默认地址。 */
    private fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.isBlank()) "" else trimmed.trimEnd('/') + "/"
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
    val juheKey: String = "",
    val aliyunMarkAppcode: String = "",
    val aliyunMarkUrl: String = "",
    val juheBaseUrl: String = NetworkModule.DEFAULT_JUHE_BASE_URL,
    val qccBaseUrl: String = NetworkModule.DEFAULT_QCC_BASE_URL,
    val aiqichaBaseUrl: String = NetworkModule.DEFAULT_AIQICHA_BASE_URL,
    val interceptAction: String = INTERCEPT_BLOCK,
    val floatingAlpha: Float = 0.9f
) {
    companion object {
        const val INTERCEPT_BLOCK = "block"
        const val INTERCEPT_LOG = "log"
    }
}