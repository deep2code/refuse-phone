package com.example.phonequery.data.source

import com.example.phonequery.model.NumberType
import com.example.phonequery.model.PlatformMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 可选在线标记源：阿里云市场「多平台号码标记查询 API」（聚美智数等商品）。
 * 聚合 腾讯手机管家 / 百度手机卫士 / 电话邦 / 360 / 泰迪熊 等多家平台的号码标记。
 *
 * 重要说明：
 * - appcode 与 url 由构造注入（设置中用户填写），未配置则 isEnabled=false，不参与查询、也不发起任何请求。
 * - 该 API 为「异步任务式」：先提交查询拿到 taskNo，再轮询取结果，本类已封装该流程。
 * - 不同商品的请求路径/返回字段可能略有差异。若解析异常，请按你购买的「调用说明」微调 [submit]/[poll]/[parseMarks]。
 */
class AliyunMarkSource(
    private val appcode: String = "",
    private val url: String = "",
    private val resultUrl: String = ""
) : OnlineMarkSource {
    override val name = "aliyun-mark"
    override val isEnabled: Boolean = appcode.isNotBlank() && url.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun query(number: String, type: NumberType): SourceResult? {
        if (!isEnabled) return null
        val digits = number.replace(Regex("\\D"), "")
        if (digits.length < 7) return null

        return withContext(Dispatchers.IO) {
            runCatching {
                val taskNo = submit(digits) ?: return@runCatching null
                val json = poll(taskNo) ?: return@runCatching null
                val marks = parseMarks(json)
                if (marks.isEmpty()) null else SourceResult(sourceName = name, marks = marks)
            }.getOrNull()
        }
    }

    /** 提交查询，返回任务号（兼容多种返回字段）。 */
    private fun submit(phone: String): String? {
        val body = FormBody.Builder().add("phone", phone).build()
        val request = Request.Builder().url(url)
            .addHeader("Authorization", "APPCODE $appcode")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val json = JSONObject(resp.body?.string().orEmpty())
            return json.optString("taskNo").takeIf { it.isNotBlank() }
                ?: json.optJSONObject("data")?.optString("taskNo")?.takeIf { it.isNotBlank() }
                ?: json.optString("queryTaskNo").takeIf { it.isNotBlank() }
        }
    }

    /** 轮询结果（最多 3 次）。 */
    private fun poll(taskNo: String): JSONObject? {
        val resultUrl = resultUrl.ifBlank { url }
        repeat(3) { i ->
            val url = "$resultUrl?queryTaskNo=$taskNo"
            val request = Request.Builder().url(url)
                .addHeader("Authorization", "APPCODE $appcode")
                .get()
                .build()
            runCatching {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val results = json.optJSONObject("data")?.optJSONArray("results")
                        ?: json.optJSONArray("results")
                    if (results != null && results.length() > 0) return json
                }
            }
            if (i < 2) Thread.sleep(800)
        }
        return null
    }

    /** 从结果中抽取所有 platform_*_name 字段作为标记。 */
    private fun parseMarks(json: JSONObject): List<PlatformMark> {
        val marks = mutableListOf<PlatformMark>()
        val data = json.optJSONObject("data") ?: json
        val results = data.optJSONArray("results") ?: run {
            val single = JSONArray()
            single.put(data)
            single
        }
        for (i in 0 until results.length()) {
            val obj = results.optJSONObject(i) ?: continue
            obj.keys().forEach { key ->
                if (key.startsWith("platform_") && key.endsWith("_name")) {
                    val value = obj.optString(key).takeIf { it.isNotBlank() } ?: return@forEach
                    val platform = key.removePrefix("platform_").removeSuffix("_name")
                    marks.add(PlatformMark("阿里云-$platform", value))
                }
            }
        }
        return marks
    }
}
