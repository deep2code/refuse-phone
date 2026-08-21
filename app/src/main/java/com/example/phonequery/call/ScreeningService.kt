package com.example.phonequery.call

import android.content.Context
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.CallScreeningService.CallResponse
import android.util.Log
import com.example.phonequery.data.AppSettings
import com.example.phonequery.data.BlocklistEvaluator
import com.example.phonequery.data.BlocklistRepository
import com.example.phonequery.data.CLEAN_SPACE_REGEX
import com.example.phonequery.data.CodeNumberRepository
import com.example.phonequery.data.ContactChecker
import com.example.phonequery.data.MarkCacheRepository
import com.example.phonequery.data.NON_DIGIT_REGEX
import com.example.phonequery.data.PhoneAttributionRepository
import com.example.phonequery.data.RecentCallRepository
import com.example.phonequery.data.SettingsDataStore
import com.example.phonequery.data.SpamHashRepository
import com.example.phonequery.data.SpamPrefixDatabase
import com.example.phonequery.db.BlocklistEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 系统级来电识别 / 拦截（Android 10+ CallScreeningService）。
 *
 * 与既有 [CallHandlerService]（前台服务 + PHONE_STATE 广播）互补：
 * - CallScreeningService 由系统直接回调，无需本应用常驻前台，更可靠也更省电；
 * - 来电时离线识别归属地 / 标记，命中黑名单或骚扰即直接拦截。
 *
 * 热路径优化：onScreenCall 由系统同步回调，内部使用 runBlocking。
 * 为避免每次来电都读 DataStore / 全表 LIKE 扫描，这里在 onCreate 预加载并缓存：
 * - settings 快照（settingsFlow 收集）
 * - 黑白名单规则快照（Room Flow 收集，规则变更自动刷新）
 * 来电回调内只做内存匹配 + 少量缓存查询，最大限度降低 ANR 风险。
 *
 * 关于「在系统来电界面显示识别结果」：
 * Android 10 曾提供 android.telecom.CallIdentification，但该类在 Android 11(API 30) 已被移除，
 * 目前没有公开 API 允许第三方应用改写系统来电界面的来电人信息。
 * 因此识别结果改由 [CallHandlerService] + [FloatingWindowManager] 的悬浮窗展示。
 *
 * 前提：本服务只有在应用持有 ROLE_CALL_SCREENING 角色时才会被系统调用（见 [CallScreeningRole]）。
 * 未持有角色时本服务不会被触发；持有角色但用户关闭「系统级来电识别」开关时本服务原样放行。
 */
class ScreeningService : CallScreeningService() {

    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 来电热路径缓存：设置快照（DataStore 变更自动刷新）。 */
    @Volatile
    private var cachedSettings: AppSettings? = null

    /** 黑名单规则内存快照（isBlock=1，按 createdAt DESC，与 DB 查询一致）。 */
    @Volatile
    private var blacklistRules: List<BlocklistEntity> = emptyList()

    /** 白名单规则内存快照（isBlock=0）。 */
    @Volatile
    private var whitelistRules: List<BlocklistEntity> = emptyList()

    private lateinit var markCacheRepository: MarkCacheRepository
    private lateinit var phoneAttributionRepository: PhoneAttributionRepository
    private lateinit var codeNumberRepository: CodeNumberRepository
    private lateinit var spamHashRepository: SpamHashRepository
    private lateinit var recentCallRepository: RecentCallRepository

    override fun onCreate() {
        super.onCreate()
        val ctx = applicationContext
        markCacheRepository = MarkCacheRepository(ctx)
        phoneAttributionRepository = PhoneAttributionRepository(ctx)
        codeNumberRepository = CodeNumberRepository(ctx)
        spamHashRepository = SpamHashRepository(ctx)
        recentCallRepository = RecentCallRepository(ctx)
        val settingsDataStore = SettingsDataStore(ctx)
        val blocklistRepository = BlocklistRepository(ctx)

        // 预加载快照：settings / 黑白名单规则（Room Flow 在表变化时自动重发，快照随之刷新）
        cacheScope.launch { settingsDataStore.settingsFlow.collect { cachedSettings = it } }
        cacheScope.launch { blocklistRepository.blacklist.collect { blacklistRules = it } }
        cacheScope.launch { blocklistRepository.whitelist.collect { whitelistRules = it } }

        // 后台预填充本地骚扰哈希库 / 工信部码号库，避免首次来电时在 onScreenCall 的
        // 系统回调线程上同步插入 7.3 万条数据导致卡死/ANR。来电热路径 match/lookup 传 allowSeed=false。
        cacheScope.launch {
            runCatching { spamHashRepository.ensureSeeded() }
            runCatching { codeNumberRepository.ensureSeeded() }
        }
    }

    override fun onDestroy() {
        cacheScope.cancel()
        super.onDestroy()
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val response = runBlocking(Dispatchers.IO) {
            // 优先用缓存快照；首次回调时快照可能尚未就绪，回退一次性读取
            val settings = cachedSettings ?: SettingsDataStore(applicationContext).settingsFlow.first()
            if (!settings.enableCallScreening) {
                // 用户未开启系统级识别：原样放行，不做任何处理
                CallResponse.Builder().build()
            } else {
                val number = callDetails.handle?.schemeSpecificPart
                if (number.isNullOrBlank()) {
                    CallResponse.Builder().build()
                } else {
                    buildResponse(callDetails, settings)
                }
            }
        }
        respondToCall(callDetails, response)
    }

    private suspend fun buildResponse(callDetails: Call.Details, settings: AppSettings): CallResponse {
        val ctx: Context = applicationContext
        val rawNumber = callDetails.handle?.schemeSpecificPart ?: ""
        val cleaned = rawNumber.replace(CLEAN_SPACE_REGEX, "").replace("＋", "+")
        val digits = cleaned.replace(NON_DIGIT_REGEX, "")

        val builder = CallResponse.Builder()

        // 1. 本地黑名单（始终拦截）与白名单：内存快照前缀匹配，
        //    语义与原 `:number LIKE number || '%'` 一致（EXACT/PREFIX 均为前缀命中），且无需扫表。
        val isBlacklisted = blacklistRules.any { digits.startsWith(it.number) }

        // 1.5 白名单 / 通讯录优先：被云标记或哈希命中的通讯录/白名单号码，
        //      在系统级拦截中会被误拦——这里先放行，避免误伤重要来电（最高优先级）。
        val isWhitelisted = whitelistRules.any { digits.startsWith(it.number) }
        val inContacts = ContactChecker.hasPermission(ctx) &&
            runCatching { ContactChecker.isInContacts(ctx, digits) }.getOrDefault(false)

        // 2. 离线骚扰识别（社区哈希库 + 本地标记缓存）
        var spamDesc: String? = null
        runCatching { spamHashRepository.match(cleaned, allowSeed = false) }.getOrNull()?.let {
            spamDesc = it.description
        }
        if (spamDesc == null) {
            runCatching { markCacheRepository.getCachedMark(digits) }.getOrNull()?.let { cached ->
                if (!cached.spamType.isNullOrBlank()) spamDesc = cached.spamType
            }
        }
        // 2.5 本地号段库提示（虚拟运营商/高风险营销号段，仅提示不拦截，决策权留给用户）
        if (spamDesc == null) {
            runCatching { SpamPrefixDatabase.match(cleaned) }.getOrNull()?.let {
                spamDesc = it.label
            }
        }

        // 3. 归属地 + 工信部码号
        var attrCity: String? = null
        var attrProvince: String? = null
        val attrParts = mutableListOf<String>()
        phoneAttributionRepository.takeIf { it.isEnabled }?.lookupAttribution(digits)?.let { (p, c, i) ->
            attrProvince = p.takeIf { it.isNotBlank() }
            attrCity = c.takeIf { it.isNotBlank() } ?: attrProvince
            val loc = listOfNotNull(p.takeIf { it.isNotBlank() }, c.takeIf { it.isNotBlank() })
                .joinToString("")
            if (loc.isNotBlank()) attrParts += loc
            if (i.isNotBlank()) attrParts += i
        }
        val codeInfo = runCatching { codeNumberRepository.lookup(cleaned, allowSeed = false) }.getOrNull()
            ?.let { codeNumberRepository.toDisplay(it) }
        codeInfo?.let { attrParts += it }

        // 3.5 非通讯录拦截：开启「仅放行通讯录」且号码不在通讯录中。
        //     必须已授予 READ_CONTACTS，否则 isInContacts 会恒为 false，
        //     导致「连通讯录号码也被误拦」的严重问题。
        val blockNonContacts = settings.enableBlockNonContacts &&
            ContactChecker.hasPermission(ctx) &&
            !ContactChecker.isInContacts(ctx, digits)

        // 3.6 高级规则（正则 / 归属地，含逆向 !城市）：命中黑名单规则即匹配（内存快照求值）
        val advancedHit = matchAdvancedRule(digits, attrCity ?: attrProvince)

        val name = when {
            isBlacklisted -> "黑名单号码"
            advancedHit != null -> "规则命中：${advancedHit.label}"
            blockNonContacts -> "非通讯录号码"
            spamDesc != null -> spamDesc
            attrParts.isNotEmpty() -> attrParts.joinToString(" · ")
            else -> null
        }
        val description = when {
            isBlacklisted -> "已加入黑名单，来电将被拦截"
            advancedHit != null -> "命中拦截规则，来电将被拦截"
            blockNonContacts -> "不在通讯录，已自动拦截"
            spamDesc != null -> "疑似骚扰/诈骗：$spamDesc"
            attrParts.isNotEmpty() -> attrParts.joinToString(" · ")
            else -> null
        }

        // 4. 拦截决策：本地黑名单 或 非通讯录 或（骚扰标记 且 用户开启自动挂断 + 骚扰自动挂断）或 高级规则命中
        //    求职保护模式下不依据骚扰/号段标记自动挂断，确保面试/重要来电不被误拦
        val matched = isBlacklisted || blockNonContacts || advancedHit != null ||
            (spamDesc != null && settings.enableAutoHangup && settings.enableSpamAutoHangup && !settings.enableJobHuntMode)
        // 白名单 / 通讯录命中 → 一律放行，绝不误拦
        val shouldBlock = matched && !isWhitelisted && !inContacts

        // 拦截动作：block=拒接；log=放行仅记录（不实际拦截）
        val willReject = shouldBlock && settings.interceptAction == AppSettings.INTERCEPT_BLOCK

        if (willReject) {
            builder.setDisallowCall(true)
                .setRejectCall(true)
                // 仍写入通话记录，便于用户事后核对「到底拦了谁」；但不弹未接来电通知
                .setSkipCallLog(false)
                .setSkipNotification(true)
        }

        // 5. 识别结果输出到日志（系统来电界面无法由第三方改写，见类注释）；
        //    实际展示由 CallHandlerService 的悬浮窗完成。
        Log.i(
            TAG,
            "screen call=$cleaned block=$shouldBlock name=${name ?: "-"} desc=${description ?: "-"}"
        )

        // 6. 写入「最近来电」留痕（与本服务会按号码+3秒去重合并）
        try {
            recentCallRepository.record(
                number = cleaned,
                digits = digits,
                name = name,
                description = description,
                blocked = shouldBlock,
                spamType = spamDesc
            )
        } catch (_: Exception) {
            // 留痕失败不影响拦截决策
        }

        return builder.build()
    }

    /**
     * 从内存黑名单快照求值高级规则（正则 / 归属地，含逆向），
     * 与 [BlocklistRepository.evaluateAdvanced] 语义一致，但省去每次来电的 2 次 DB 查询。
     */
    private fun matchAdvancedRule(digits: String, city: String?): BlocklistEntity? {
        if (digits.isBlank()) return null
        for (rule in blacklistRules) {
            when (rule.type) {
                BlocklistEntity.TYPE_REGEX ->
                    if (BlocklistEvaluator.matchesRegexRule(rule, digits)) return rule
                BlocklistEntity.TYPE_ATTR ->
                    if (!city.isNullOrBlank() && BlocklistEvaluator.matchesAttrRule(rule, city)) return rule
            }
        }
        return null
    }

    private companion object {
        const val TAG = "ScreeningService"
    }
}
