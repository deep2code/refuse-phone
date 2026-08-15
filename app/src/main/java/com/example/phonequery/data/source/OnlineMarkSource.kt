package com.example.phonequery.data.source

import com.example.phonequery.model.NumberType
import com.example.phonequery.model.PlatformMark

/**
 * 统一的在线数据源结果。各在线源（聚合数据 / 阿里云聚美 / 百度）都收敛成该结构，
 * 便于在 [mergeSourceResults] 中按字段合并。
 */
data class SourceResult(
    val sourceName: String,
    val province: String? = null,
    val city: String? = null,
    val carrier: String? = null,
    val areaCode: String? = null,
    val zipCode: String? = null,
    val spamType: String? = null,
    val spamCount: String? = null,
    val marks: List<PlatformMark> = emptyList(),
    val enterpriseName: String? = null
)

/**
 * 可插拔的在线标记/归属地数据源。
 * - 所有在线源均需在设置界面运行时填写 key/APPCODE 后才 [isEnabled] 为 true。
 * - 当前实现：聚合数据（归属地 + 骚扰标记）、阿里云聚美（多平台标记）、百度（归属地）。
 * - 离线兜底由 [MarkCacheRepository] / [SpamPrefixDatabase] 等本地库承担，关掉在线开关即纯离线。
 */
interface OnlineMarkSource {
    val name: String
    val isEnabled: Boolean
    suspend fun query(number: String, type: NumberType): SourceResult?
}

/** 把 other 的字段补充进 base（base 已有的字段优先）。 */
fun mergeSourceResults(base: SourceResult, other: SourceResult): SourceResult {
    return base.copy(
        province = base.province ?: other.province,
        city = base.city ?: other.city,
        carrier = base.carrier ?: other.carrier,
        areaCode = base.areaCode ?: other.areaCode,
        zipCode = base.zipCode ?: other.zipCode,
        spamType = base.spamType ?: other.spamType,
        spamCount = base.spamCount ?: other.spamCount,
        enterpriseName = base.enterpriseName ?: other.enterpriseName,
        marks = (base.marks + other.marks).distinctBy { "${it.platform}:${it.mark}" }
    )
}
