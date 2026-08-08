package com.example.phonequery.data.source

import com.example.phonequery.model.NumberType
import com.example.phonequery.model.PlatformMark

/**
 * 统一的在线数据源结果。各在线源（tmini / 聚合数据 / 百度）都收敛成该结构，
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
 * - 默认源（tmini）始终启用，零 key。
 * - 可选源（聚合数据、百度）仅在配置了对应 key 时 [isEnabled] 为 true。
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
