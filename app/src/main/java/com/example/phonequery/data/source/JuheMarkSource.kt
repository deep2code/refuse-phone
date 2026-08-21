package com.example.phonequery.data.source

import com.example.phonequery.model.NumberType
import com.example.phonequery.model.PlatformMark
import com.example.phonequery.network.JuheService
import com.example.phonequery.data.NetworkModule

/**
 * 可选在线标记源：聚合数据「来电号码显示 / 号码标记」(id/511, mobileVerify/query)。
 *
 * - key 由构造注入（设置中用户填写的 juhe key），未配置则 [isEnabled] = false。
 * - 返回号码标记类型（诈骗电话 / 骚扰电话 / 推销 / 房产中介 / 快递送餐…）与标记次数。
 * - 这是目前可替代 tmini 的免费个人可用源：juhe.cn 个人实名后即可拿到 key，有免费额度。
 */
class JuheMarkSource(
    private val key: String = "",
    private val baseUrl: String = NetworkModule.DEFAULT_JUHE_BASE_URL
) : OnlineMarkSource {
    override val name = "juhe-mark"
    override val isEnabled: Boolean = key.isNotBlank()

    private val service by lazy { NetworkModule.retrofitFor(baseUrl).create(JuheService::class.java) }

    override suspend fun query(number: String, type: NumberType): SourceResult? {
        if (!isEnabled) return null
        val digits = number.replace(Regex("\\D"), "")
        if (digits.length != 11) return null

        val resp = runCatching { service.queryMark(key, digits) }.getOrNull()
        if (resp?.error_code != 0) return null
        val flag = resp.result?.flag ?: return null
        val type_label = flag.type ?: return null
        val num = flag.num

        val markText = buildString {
            append(type_label)
            if (num != null) append(" ×$num")
        }
        return SourceResult(
            sourceName = name,
            spamType = type_label,
            spamCount = if (num != null) "×$num" else null,
            marks = listOf(PlatformMark("聚合数据标记", markText))
        )
    }
}
