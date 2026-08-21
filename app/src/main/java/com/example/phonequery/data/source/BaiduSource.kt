package com.example.phonequery.data.source

import com.example.phonequery.BuildConfig
import com.example.phonequery.data.NON_DIGIT_REGEX
import com.example.phonequery.model.NumberType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 可选在线源：百度手机号归属地（key 门控）。
 *
 * 说明：百度旧的免 key 接口（resource_id=6004）已于 2024 年左右停服，公开返回空数据；
 * 若想启用百度源，需自行准备一个返回 JSON（含 province/city/company 字段）的接口地址与 key，
 * 配置到 local.properties 的 BAIDU_PHONE_API_URL / BAIDU_PHONE_KEY。
 * 未配置时 [isEnabled] = false，不参与查询，不会发起任何请求。
 *
 * 这也契合 CallerInfo 的「隐藏功能：自定义百度 API」设计——把百度当作可选增强源。
 */
class BaiduSource : OnlineMarkSource {
    override val name = "baidu"
    override val isEnabled: Boolean = BuildConfig.BAIDU_PHONE_API_URL.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun query(number: String, type: NumberType): SourceResult? {
        if (!isEnabled) return null
        if (type != NumberType.MOBILE) return null
        val digits = number.replace(NON_DIGIT_REGEX, "")
        if (digits.length != 11) return null

        val url = "${BuildConfig.BAIDU_PHONE_API_URL}?phone=$digits&key=${BuildConfig.BAIDU_PHONE_KEY}"
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val json = JSONObject(resp.body?.string().orEmpty())
                    SourceResult(
                        sourceName = name,
                        province = json.optString("province").takeIf { it.isNotBlank() },
                        city = json.optString("city").takeIf { it.isNotBlank() },
                        carrier = json.optString("company").takeIf { it.isNotBlank() }
                    )
                }
            }.getOrNull()
        }
    }
}
