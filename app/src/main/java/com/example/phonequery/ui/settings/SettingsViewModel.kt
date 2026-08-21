package com.example.phonequery.ui.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.phonequery.call.CallHandlerService
import com.example.phonequery.data.AppSettings
import com.example.phonequery.data.BlocklistRepository
import com.example.phonequery.data.CodeNumberRepository
import com.example.phonequery.data.MarkCacheRepository
import com.example.phonequery.data.RecentCallRepository
import com.example.phonequery.data.SettingsDataStore
import com.example.phonequery.data.SpamHashRepository
import com.example.phonequery.db.BlocklistEntity
import com.example.phonequery.db.RecentCallEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)
    private val blocklistRepository by lazy { BlocklistRepository(application) }
    private val markCacheRepository by lazy { MarkCacheRepository(application) }
    private val spamHashRepository by lazy { SpamHashRepository(application) }
    private val codeNumberRepository by lazy { CodeNumberRepository(application) }
    private val recentCallRepository by lazy { RecentCallRepository(application) }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                settingsDataStore.settingsFlow,
                blocklistRepository.blacklist,
                blocklistRepository.whitelist
            ) { settings, blacklist, whitelist ->
                SettingsUiState(
                    settings = settings,
                    blacklist = blacklist,
                    whitelist = whitelist
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setFloatingWindow(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateFloatingWindow(enabled)
            if (enabled) startCallService()
        }
    }

    fun setFloatingAlpha(alpha: Float) {
        viewModelScope.launch {
            settingsDataStore.updateFloatingAlpha(alpha)
        }
    }

    fun setAutoHangup(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateAutoHangup(enabled)
            if (enabled) startCallService()
        }
    }

    fun setBlacklistOnly(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateBlacklistOnly(enabled)
        }
    }

    fun setSpamAutoHangup(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateSpamAutoHangup(enabled)
        }
    }

    fun setBootStart(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateBootStart(enabled)
        }
    }

    fun setJobHuntMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateJobHuntMode(enabled)
        }
    }

    fun setSilenceUnknown(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateSilenceUnknown(enabled)
        }
    }

    fun setCallScreening(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateCallScreening(enabled)
        }
    }

    fun setBlockNonContacts(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateBlockNonContacts(enabled)
        }
    }

    /** 在线查询总开关：开启即把号码发往已配置的在线标记源（外部网关 / 阿里云），隐私敏感，默认关。 */
    fun setOnlineLookup(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateOnlineLookup(enabled)
        }
    }

    /** 保存外部网关地址（留空回落默认 http://114.55.170.79:5050/）。 */
    fun setGatewayBaseUrl(value: String) {
        viewModelScope.launch {
            settingsDataStore.updateGatewayBaseUrl(value)
        }
    }

    /** 保存阿里云云市场「聚美智数」号码标记 APPCODE，留空则此源不生效。 */
    fun setAliyunMarkAppcode(value: String) {
        viewModelScope.launch {
            settingsDataStore.updateAliyunMarkAppcode(value)
        }
    }

    /** 保存阿里云云市场「聚美智数」号码标记调用地址。 */
    fun setAliyunMarkUrl(value: String) {
        viewModelScope.launch {
            settingsDataStore.updateAliyunMarkUrl(value)
        }
    }

    val hasSeenSetupGuide: Flow<Boolean> = settingsDataStore.hasSeenSetupGuide

    fun markSetupGuideSeen() {
        viewModelScope.launch {
            settingsDataStore.markSetupGuideSeen()
        }
    }

    fun addBlockNumber(number: String, note: String) {
        viewModelScope.launch {
            blocklistRepository.add(number, note, isBlock = true)
        }
    }

    fun addWhitelistNumber(number: String, note: String) {
        viewModelScope.launch {
            blocklistRepository.add(number, note, isBlock = false)
        }
    }

    fun delete(entity: BlocklistEntity) {
        viewModelScope.launch {
            blocklistRepository.delete(entity)
        }
    }

    fun quickBlockVirtualOperators() {
        viewModelScope.launch {
            blocklistRepository.quickBlockVirtualOperators()
        }
    }

    fun addAreaCodeBlock(areaCode: String) {
        viewModelScope.launch {
            blocklistRepository.addAreaCodeBlock(areaCode)
        }
    }

    fun addBlockPrefix(prefix: String, label: String, isBlock: Boolean) {
        viewModelScope.launch {
            blocklistRepository.addPrefix(prefix, label, isBlock)
        }
    }

    fun addRegexRule(pattern: String, label: String, isBlock: Boolean) {
        viewModelScope.launch {
            blocklistRepository.addRegex(pattern, label, isBlock)
        }
    }

    fun addAttrRule(region: String, isBlock: Boolean, reverse: Boolean) {
        viewModelScope.launch {
            blocklistRepository.addAttr(region, isBlock, reverse)
        }
    }

    fun startCallService() {
        val context = getApplication<Application>()
        val intent = Intent(context, CallHandlerService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // 缺少权限或服务启动失败
        }
    }

    fun stopCallService() {
        val context = getApplication<Application>()
        context.stopService(Intent(context, CallHandlerService::class.java))
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private val _cacheCount = MutableStateFlow(0)
    val cacheCount: StateFlow<Int> = _cacheCount

    private val _spamHashCount = MutableStateFlow(0)
    val spamHashCount: StateFlow<Int> = _spamHashCount

    private val _codeNumberCount = MutableStateFlow(0)
    val codeNumberCount: StateFlow<Int> = _codeNumberCount

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _cacheCount.value = markCacheRepository.count()
            spamHashRepository.ensureSeeded()
            _spamHashCount.value = spamHashRepository.count()
            codeNumberRepository.ensureSeeded()
            _codeNumberCount.value = codeNumberRepository.count()
        }
    }

    fun clearMarkCache() {
        viewModelScope.launch {
            markCacheRepository.clearAll()
            _cacheCount.value = 0
        }
    }

    /** 最近来电列表（Flow） */
    val recentCalls: Flow<List<RecentCallEntity>> = recentCallRepository.all

    fun clearRecentCalls() {
        viewModelScope.launch { recentCallRepository.clear() }
    }

    /** 导出全部规则+关键设置为 JSON 字符串（供备份文件写入）。 */
    suspend fun exportBackup(): String {
        return blocklistRepository.exportAll()
    }

    /** 从 JSON 字符串导入备份，返回导入的规则条数。 */
    suspend fun importBackup(json: String): Int {
        return blocklistRepository.importAll(json)
    }
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val blacklist: List<BlocklistEntity> = emptyList(),
    val whitelist: List<BlocklistEntity> = emptyList()
)