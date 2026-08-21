package com.example.phonequery.data.source

import com.example.phonequery.model.NumberType
import com.example.phonequery.network.JuheService
import com.example.phonequery.data.NetworkModule

/**
 * 可选在线源：聚合数据「手机号归属地」(id/11)。
 * - key 由构造注入（来自设置中用户填写的 juhe key），未配置则 [isEnabled] = false，不参与查询。
 * - 免费额度约 50 次/天，仅补充归属地（省/市/运营商），不提供骚扰标记。
 */
class JuheSource(
    private val key: String = "",
    private val baseUrl: String = NetworkModule.DEFAULT_JUHE_BASE_URL
) : OnlineMarkSource {
    override val name = "juhe"
    override val isEnabled: Boolean = key.isNotBlank()

    private val service by lazy { NetworkModule.retrofitFor(baseUrl).create(JuheService::class.java) }

    override suspend fun query(number: String, type: NumberType): SourceResult? {
        if (!isEnabled) return null
        if (type != NumberType.MOBILE) return null
        val digits = number.replace(Regex("\\D"), "")
        if (digits.length != 11) return null

        val resp = runCatching { service.getMobile(digits, key) }.getOrNull()
        val r = resp?.result ?: return null
        if (resp.error_code != 0) return null

        return SourceResult(
            sourceName = name,
            province = r.province,
            city = r.city,
            carrier = r.company,
            areaCode = r.areacode,
            zipCode = r.zip
        )
    }
}
