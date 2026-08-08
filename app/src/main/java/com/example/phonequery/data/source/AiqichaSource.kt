package com.example.phonequery.data.source

import com.example.phonequery.BuildConfig
import com.example.phonequery.data.NetworkModule
import com.example.phonequery.network.AiqichaService

/**
 * 可选企业源：百度爱企查（key 门控）。
 * - 按电话号码（作为关键字）搜索企业，返回公司名 / 行业 / 法人 / 状态。
 * - 未配置 APIKEY 则 [isEnabled]=false，不参与查询。
 * - 作为企查查之外的备选企业源；两者都配置时企查查优先。
 */
class AiqichaSource : EnterpriseSource {
    override val name = "aiqicha"
    override val isEnabled: Boolean = BuildConfig.AIQICHA_APIKEY.isNotBlank()

    private val service by lazy { NetworkModule.aiqichaRetrofit.create(AiqichaService::class.java) }

    override suspend fun lookup(number: String): EnterpriseSourceResult? {
        if (!isEnabled) return null
        val digits = number.replace(Regex("\\D"), "")
        val resp = runCatching {
            service.search(BuildConfig.AIQICHA_APIKEY, digits)
        }.getOrNull()
        val company = resp?.data?.list?.firstOrNull() ?: return null
        if (company.name.isNullOrBlank()) return null
        return EnterpriseSourceResult(
            sourceName = name,
            company = company.name,
            industry = company.industry,
            legalPerson = company.legalPerson,
            status = company.status
        )
    }
}
