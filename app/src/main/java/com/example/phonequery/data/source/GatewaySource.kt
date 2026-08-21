package com.example.phonequery.data.source

import com.example.phonequery.data.NetworkModule
import com.example.phonequery.data.NON_DIGIT_REGEX
import com.example.phonequery.model.NumberType
import com.example.phonequery.model.PlatformMark
import com.example.phonequery.network.GatewayService
import org.json.JSONObject

/**
 * 外部网关源：App 唯一的默认在线查询接口（自建 / 代理网关，默认 http://114.55.170.79:5050）。
 *
 * 约定接口（可在「设置 → 在线查询」中修改 baseUrl，留空回落默认地址）：
 *   GET {baseUrl}query?phone={11位手机号}
 *
 * 响应 JSON 宽松解析，兼容归属地 + 号码标记两类字段（缺失的字段自动跳过）：
 *   { "code": 0 | 200, "message": "...", "data": { ... } }   // 或直接平铺 / result 包裹
 *   归属地: province / city / carrier(company|isp|operator)
 *   标记:   spam_type(spamType|type) / spam_count(count|num) / marks[] / platform_*_name
 *
 * 若返回结构与此约定不符，只需调整 [parse]，不影响其余链路。
 */
class GatewaySource(
    private val baseUrl: String = NetworkModule.DEFAULT_GATEWAY_BASE_URL
) : OnlineMarkSource {
    override val name = "gateway"
    override val isEnabled: Boolean = baseUrl.isNotBlank()

    private val service by lazy { NetworkModule.retrofitFor(baseUrl).create(GatewayService::class.java) }

    override suspend fun query(number: String, type: NumberType): SourceResult? {
        if (!isEnabled) return null
        if (type != NumberType.MOBILE) return null
        val digits = number.replace(NON_DIGIT_REGEX, "")
        if (digits.length != 11) return null

        val body = runCatching { service.query(digits) }.getOrNull() ?: return null
        val text = runCatching { body.string() }.getOrNull() ?: return null
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        return parse(json)
    }

    private fun parse(json: JSONObject): SourceResult? {
        // 显式错误码直接失败（兼容 code=0/200、error_code=0 表示成功）
        val code = json.optInt("code", -1)
        if (code != -1 && code != 0 && code != 200) return null
        val errorCode = json.optInt("error_code", -1)
        if (errorCode != -1 && errorCode != 0) return null

        val data = json.optJSONObject("data") ?: json
        val result = data.optJSONObject("result") ?: data

        val province = result.optString("province").takeIf { it.isNotBlank() }
        val city = result.optString("city").takeIf { it.isNotBlank() }
        val carrier = result.optString("carrier").takeIf { it.isNotBlank() }
            ?: result.optString("company").takeIf { it.isNotBlank() }
            ?: result.optString("isp").takeIf { it.isNotBlank() }
            ?: result.optString("operator").takeIf { it.isNotBlank() }
        val spamType = result.optString("spam_type").takeIf { it.isNotBlank() }
            ?: result.optString("spamType").takeIf { it.isNotBlank() }
            ?: result.optString("type").takeIf { it.isNotBlank() }
        val spamCount = result.optString("spam_count").takeIf { it.isNotBlank() }
            ?: result.optString("count").takeIf { it.isNotBlank() }
            ?: result.opt("num")?.toString()?.takeIf { it.isNotBlank() && it != "0" }

        val marks = parseMarks(result)
        if (province == null && city == null && carrier == null && spamType == null && marks.isEmpty()) {
            return null
        }

        return SourceResult(
            sourceName = name,
            province = province,
            city = city,
            carrier = carrier,
            spamType = spamType,
            spamCount = spamCount,
            marks = marks
        )
    }

    /** 兼容 marks 数组 与 platform_*_name 平铺两种格式。 */
    private fun parseMarks(obj: JSONObject): List<PlatformMark> {
        val marks = mutableListOf<PlatformMark>()
        obj.optJSONArray("marks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val platform = m.optString("platform").takeIf { it.isNotBlank() } ?: name
                val text = m.optString("mark").takeIf { it.isNotBlank() } ?: continue
                marks.add(PlatformMark(platform, text))
            }
        }
        obj.keys().forEach { key ->
            if (key.startsWith("platform_") && key.endsWith("_name")) {
                val value = obj.optString(key).takeIf { it.isNotBlank() } ?: return@forEach
                val platform = key.removePrefix("platform_").removeSuffix("_name")
                marks.add(PlatformMark("网关-$platform", value))
            }
        }
        return marks
    }
}
