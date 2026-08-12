package com.example.phonequery.call

import android.content.Context
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.CallScreeningService.CallResponse
import android.util.Log
import com.example.phonequery.data.CodeNumberRepository
import com.example.phonequery.data.ContactChecker
import com.example.phonequery.data.MarkCacheRepository
import com.example.phonequery.data.PhoneAttributionRepository
import com.example.phonequery.data.SettingsDataStore
import com.example.phonequery.data.SpamHashRepository
import com.example.phonequery.data.SpamPrefixDatabase
import com.example.phonequery.data.AppSettings
import com.example.phonequery.data.BlocklistRepository
import com.example.phonequery.data.RecentCallRepository
import com.example.phonequery.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 系统级来电识别 / 拦截（Android 10+ CallScreeningService）。
 *
 * 与既有 [CallHandlerService]（前台服务 + PHONE_STATE 广播）互补：
 * - CallScreeningService 由系统直接回调，无需本应用常驻前台，更可靠也更省电；
 * - 来电时离线识别归属地 / 标记，命中黑名单或骚扰即直接拦截。
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

    override fun onScreenCall(callDetails: Call.Details) {
        val response = runBlocking(Dispatchers.IO) {
            val settings = SettingsDataStore(applicationContext).settingsFlow.first()
            if (!settings.enableCallScreening) {
                // 用户未开启系统级识别：原样放行，不做任何处理
                CallResponse.Builder().build()
            } else {
                val number = callDetails.handle?.schemeSpecificPart
                if (number.isNullOrBlank()) {
                    CallResponse.Builder().build()
                } else {
                    buildResponse(callDetails)
                }
            }
        }
        respondToCall(callDetails, response)
    }

    private suspend fun buildResponse(callDetails: Call.Details): CallResponse {
        val ctx: Context = applicationContext
        val rawNumber = callDetails.handle?.schemeSpecificPart ?: ""
        val cleaned = rawNumber.replace(Regex("[\\s()-]"), "").replace("＋", "+")
        val digits = cleaned.replace(Regex("\\D"), "")
        val settings = SettingsDataStore(ctx).settingsFlow.first()

        val builder = CallResponse.Builder()

        // 1. 本地黑名单（始终拦截）
        val isBlacklisted = runCatching {
            AppDatabase.getInstance(ctx).blocklistDao().isBlacklisted(digits)
        }.getOrDefault(false)

        // 1.5 白名单 / 通讯录优先：被云标记或哈希命中的通讯录/白名单号码，
        //      在系统级拦截中会被误拦——这里先放行，避免误伤重要来电（最高优先级）。
        val isWhitelisted = runCatching {
            AppDatabase.getInstance(ctx).blocklistDao().isWhitelisted(digits)
        }.getOrDefault(false)
        val inContacts = ContactChecker.hasPermission(ctx) &&
            runCatching { ContactChecker.isInContacts(ctx, digits) }.getOrDefault(false)

        // 2. 离线骚扰识别（社区哈希库 + 本地标记缓存）
        var spamDesc: String? = null
        runCatching { SpamHashRepository(ctx).match(cleaned) }.getOrNull()?.let {
            spamDesc = it.description
        }
        if (spamDesc == null) {
            runCatching { MarkCacheRepository(ctx).getCachedMark(digits) }.getOrNull()?.let { cached ->
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
        PhoneAttributionRepository(ctx).takeIf { it.isEnabled }?.lookupAttribution(digits)?.let { (p, c, i) ->
            attrProvince = p.takeIf { it.isNotBlank() }
            attrCity = c.takeIf { it.isNotBlank() } ?: attrProvince
            val loc = listOfNotNull(p.takeIf { it.isNotBlank() }, c.takeIf { it.isNotBlank() })
                .joinToString("")
            if (loc.isNotBlank()) attrParts += loc
            if (i.isNotBlank()) attrParts += i
        }
        val codeInfo = runCatching { CodeNumberRepository(ctx).lookup(cleaned) }.getOrNull()
            ?.let { CodeNumberRepository(ctx).toDisplay(it) }
        codeInfo?.let { attrParts += it }

        // 3.5 非通讯录拦截：开启「仅放行通讯录」且号码不在通讯录中。
        //     必须已授予 READ_CONTACTS，否则 isInContacts 会恒为 false，
        //     导致「连通讯录号码也被误拦」的严重问题。
        val blockNonContacts = settings.enableBlockNonContacts &&
            ContactChecker.hasPermission(ctx) &&
            !ContactChecker.isInContacts(ctx, digits)

        // 3.6 高级规则（正则 / 归属地，含逆向 !城市）：命中黑名单规则即匹配
        val advancedHit = runCatching {
            BlocklistRepository(ctx).evaluateAdvanced(digits, attrCity ?: attrProvince)
        }.getOrNull()

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
            RecentCallRepository(ctx).record(
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

    private companion object {
        const val TAG = "ScreeningService"
    }
}
