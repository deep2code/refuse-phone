package com.example.phonequery.data.source

import com.example.phonequery.BuildConfig
import com.example.phonequery.data.NetworkModule
import com.example.phonequery.network.QccService

/**
 * 可选企业源：企查查开放平台（key 门控）。
 * - ApiCode 886 按电话号码反查公司名；ApiCode 2001 核验返回「企查查行业 / 法人 / 状态」。
 * - 免费试用 20 次，之后按次付费（约 0.1～1 元/次）。未配置 key 则 [isEnabled]=false。
 * - 用于补充固话「公司 + 所属行业」，与零 key 的 tmini 公司名反查形成增强。
 */
class QccSource : EnterpriseSource {
    override val name = "qcc"
    override val isEnabled: Boolean =
        BuildConfig.QCC_KEY.isNotBlank() && BuildConfig.QCC_TOKEN.isNotBlank()

    private val service by lazy { NetworkModule.qccRetrofit.create(QccService::class.java) }

    override suspend fun lookup(number: String): EnterpriseSourceResult? {
        if (!isEnabled) return null
        val digits = number.replace(Regex("\\D"), "")

        val search = runCatching {
            service.search(BuildConfig.QCC_KEY, BuildConfig.QCC_TOKEN, digits)
        }.getOrNull()
        val company = search?.result?.items?.firstOrNull()?.name ?: return null

        // 二次核验拿行业/法人/状态（企查查按公司名返回更全的工商信息）
        val verify = runCatching {
            service.verify(BuildConfig.QCC_KEY, BuildConfig.QCC_TOKEN, company)
        }.getOrNull()
        val v = verify?.result

        return EnterpriseSourceResult(
            sourceName = name,
            company = company,
            industry = v?.industry,
            legalPerson = v?.legalPerson,
            status = v?.status
        )
    }
}
