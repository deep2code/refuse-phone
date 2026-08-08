package com.example.phonequery.data.source

/**
 * 统一的工商/企业数据源结果。各企业源（企查查 / 爱企查）都收敛成该结构，
 * 便于在 [EnterpriseRepository] 中合并补充「公司名 / 行业 / 法人 / 状态」。
 */
data class EnterpriseSourceResult(
    val sourceName: String,
    val company: String? = null,
    val industry: String? = null,
    val legalPerson: String? = null,
    val status: String? = null
)

/**
 * 可插拔的工商/企业数据源（固话 → 公司 + 行业）。
 * - 默认无（零 key 时仅用 tmini 反查公司名）。
 * - 可选源（企查查 / 爱企查）仅在配置了对应 key 时 [isEnabled] 为 true。
 */
interface EnterpriseSource {
    val name: String
    val isEnabled: Boolean
    suspend fun lookup(number: String): EnterpriseSourceResult?
}
