package com.example.phonequery.data

import android.content.Context
import com.example.phonequery.data.source.AliyunMarkSource
import com.example.phonequery.data.source.BaiduSource
import com.example.phonequery.data.source.GatewaySource
import com.example.phonequery.data.source.OnlineMarkSource
import com.example.phonequery.data.source.SourceResult
import com.example.phonequery.data.source.mergeSourceResults
import com.example.phonequery.model.NumberType
import com.example.phonequery.model.PhoneInfo
import com.example.phonequery.model.PlatformMark
import com.example.phonequery.model.ResultSource
import com.example.phonequery.data.AppSettings
import com.example.phonequery.data.PhoneAttributionRepository
import com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale

class PhoneRepository(context: Context) {

    private val appContext: Context = context

    private val phoneUtil = PhoneNumberUtil.getInstance()
    private val geocoder = PhoneNumberOfflineGeocoder.getInstance()
    private val carrierMapper = PhoneNumberToCarrierMapper.getInstance()

    // 本地标记缓存：在线结果落库，断网/接口失效时回退
    private val markCacheRepository: MarkCacheRepository = MarkCacheRepository(context)

    // 社区维护骚扰库（md5 离线匹配）
    private val spamHashRepository: SpamHashRepository = SpamHashRepository(context)

    // 工信部码号资源离线表（95/96/106/400/800 号段 → 使用单位，用于陌生 95 号识别）
    private val codeNumberRepository: CodeNumberRepository = CodeNumberRepository(context)

    // 可刷新的手机号归属地离线库（phonedata.db，来自 xluohome/phonedata 开源数据）
    // 仅当 assets/phonedata.db 存在时启用，否则自动跳过、回落 lalakii/phonedata。
    private val phoneAttributionRepository: PhoneAttributionRepository = PhoneAttributionRepository(context)

    /**
     * 主查询入口：
     * 1. 本地离线解析（libphonenumber + 中国号段库）
     * 2. 本地号段库匹配（零 key，虚商/高风险号段提示）
     * 3. 社区骚扰库 md5 匹配（零 key，开源众包清单）
     * 4. 在线源链（外部网关 + 阿里云多平台标记）→ 结果回写本地缓存
     */
    suspend fun query(number: String): PhoneInfo = withContext(Dispatchers.IO) {
        val cleaned = number.trim()
            .replace(Regex("[\\s()-]"), "")
            .replace("＋", "+")
        if (cleaned.isBlank() || cleaned == "+") {
            return@withContext PhoneInfo(errorMessage = "号码不能为空")
        }

        val offlineInfo = parseOffline(cleaned)
        val digits = cleaned.replace(Regex("\\D"), "")

        // 归属地补充：若用户生成了 phonedata.db（可刷新、比 lalakii 内置库更新），
        // 用它覆盖省/市/运营商；否则回落 lalakii/phonedata 的离线结果。
        var base = offlineInfo
        if (phoneAttributionRepository.isEnabled) {
            phoneAttributionRepository.lookupAttribution(digits)?.let { (p, c, i) ->
                base = base.copy(
                    province = p.ifBlank { base.province },
                    city = c.ifBlank { base.city },
                    carrier = i.ifBlank { base.carrier }
                )
            }
        }

        // 第一层（零 key）：本地号段库提示
        val prefixHint = SpamPrefixDatabase.match(cleaned)
        if (prefixHint != null) {
            base = base.copy(
                platformMarks = base.platformMarks + PlatformMark("本地号段库", prefixHint.label)
            )
        }

        // 第二层（零 key）：社区维护骚扰库（md5 离线匹配）
        val knownSpam = runCatching { spamHashRepository.match(cleaned) }.getOrNull()
        if (knownSpam != null) {
            base = base.copy(
                spamType = base.spamType ?: "社区标记骚扰",
                platformMarks = base.platformMarks + PlatformMark("社区骚扰库", knownSpam.description)
            )
        }

        // 第二点五层（零 key）：工信部码号资源表（陌生 95/96/106/400/800 号段 → 使用单位）
        val codeInfo = runCatching { codeNumberRepository.lookup(cleaned) }.getOrNull()
        if (codeInfo != null) {
            val desc = codeNumberRepository.toDisplay(codeInfo)
            base = base.copy(
                codeNumberInfo = desc,
                platformMarks = base.platformMarks + PlatformMark("工信部码号库", desc)
            )
        }

        // 读取本地缓存标记（断网也能标记骚扰/诈骗）
        val cached = runCatching { markCacheRepository.getCachedMark(digits) }.getOrNull()

        // 第三层：在线源链（外部网关 + 阿里云多平台标记）
        // 受「在线查询开关」控制：默认关闭（离线优先），开启才会把号码发到外部网关/第三方。
        // 网关地址默认 http://114.55.170.79:5050，可在设置中修改。
        val settings = try {
            SettingsDataStore(appContext).settingsFlow.first()
        } catch (_: Exception) {
            null
        }
        val onlineEnabled = settings?.enableOnlineLookup ?: false
        val online = if (onlineEnabled) queryOnline(cleaned, base.numberType, settings) else null

        return@withContext if (online != null) {
            val merged = mergeOnlineToPhoneInfo(base, online)
            // 把确有标记的数据写回本地缓存，越用越准
            val toCache = merged.copy(
                platformMarks = merged.platformMarks.filter { it.platform != "本地号段库" }
            )
            runCatching { markCacheRepository.saveMark(digits, toCache) }
            merged
        } else {
            // 在线失败：回退本地缓存 + 离线结果
            cached?.let { c ->
                base.copy(
                    spamType = c.spamType ?: base.spamType,
                    spamCount = c.spamCount ?: base.spamCount,
                    platformMarks = (base.platformMarks + c.platformMarks)
                        .distinctBy { "${it.platform}:${it.mark}" },
                    source = ResultSource.CACHED,
                    fromCache = true
                )
            } ?: base.copy(
                source = ResultSource.OFFLINE,
                errorMessage = "在线查询失败，未找到本地缓存，已显示离线结果。"
            )
        }
    }

    /** 依次尝试各在线源，合并结果（前者字段优先）。外部网关地址默认 http://114.55.170.79:5050，可在设置中修改。 */
    private suspend fun queryOnline(number: String, type: NumberType, settings: AppSettings?): SourceResult? {
        val gatewayUrl = settings?.gatewayBaseUrl ?: NetworkModule.DEFAULT_GATEWAY_BASE_URL
        val aliyunAppcode = settings?.aliyunMarkAppcode ?: ""
        val aliyunUrl = settings?.aliyunMarkUrl ?: ""
        val sources = listOfNotNull(
            GatewaySource(gatewayUrl),
            AliyunMarkSource(aliyunAppcode, aliyunUrl),
            BaiduSource().takeIf { it.isEnabled }
        )
        var merged: SourceResult? = null
        for (src in sources) {
            if (!src.isEnabled) continue
            val r = runCatching { src.query(number, type) }.getOrNull() ?: continue
            merged = if (merged == null) r else mergeSourceResults(merged, r)
        }
        return merged
    }

    private fun mergeOnlineToPhoneInfo(offline: PhoneInfo, online: SourceResult): PhoneInfo {
        var result = offline.copy(
            source = ResultSource.ONLINE,
            province = online.province ?: offline.province,
            city = online.city ?: offline.city,
            carrier = online.carrier ?: offline.carrier,
            areaCode = online.areaCode ?: offline.areaCode,
            zipCode = online.zipCode ?: offline.zipCode,
            spamType = online.spamType ?: offline.spamType,
            spamCount = online.spamCount ?: offline.spamCount
        )
        val marks = (result.platformMarks + online.marks).distinctBy { "${it.platform}:${it.mark}" }
        result = result.copy(platformMarks = marks)
        return result
    }

    private fun parseOffline(number: String): PhoneInfo {
        val nationalNumber = when {
            number.startsWith("+86") && number.length > 10 -> number.substring(3)
            number.startsWith("86") && number.length > 10 -> number.substring(2)
            else -> number
        }

        // 手机号与固话统一走 libphonenumber 离线解析（geocoder 省/市 + carrier 运营商）。
        // 更精确的号段级归属地由 PhoneAttributionRepository（assets/phonedata.db）在上层覆盖，
        // 详见 buildOfflineResult 中对 phoneAttributionRepository 的调用。
        return try {
            val proto: Phonenumber.PhoneNumber = phoneUtil.parse(nationalNumber, "CN")
            if (!phoneUtil.isValidNumber(proto)) {
                return PhoneInfo(number = number, errorMessage = "号码格式无效")
            }

            val type = when (phoneUtil.getNumberType(proto)) {
                PhoneNumberUtil.PhoneNumberType.MOBILE -> NumberType.MOBILE
                PhoneNumberUtil.PhoneNumberType.FIXED_LINE,
                PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE -> NumberType.LANDLINE
                PhoneNumberUtil.PhoneNumberType.TOLL_FREE -> NumberType.TOLL_FREE
                else -> NumberType.UNKNOWN
            }

            val region = geocoder.getDescriptionForNumber(proto, Locale.CHINESE)
            val carrier = carrierMapper.getNameForNumber(proto, Locale.CHINESE)

            PhoneInfo(
                number = number,
                numberType = type,
                province = parseProvince(region),
                city = parseCity(region),
                carrier = carrier,
                source = ResultSource.OFFLINE
            )
        } catch (e: Exception) {
            PhoneInfo(number = number, errorMessage = "离线解析失败：${e.message}")
        }
    }

    private fun parseProvince(region: String?): String? {
        if (region.isNullOrBlank()) return null
        return region.split(" ").firstOrNull()
    }

    private fun parseCity(region: String?): String? {
        if (region.isNullOrBlank()) return null
        val parts = region.split(" ")
        return parts.getOrNull(1) ?: parts.firstOrNull()
    }
}
