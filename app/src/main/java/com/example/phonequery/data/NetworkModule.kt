package com.example.phonequery.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.example.phonequery.BuildConfig
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object NetworkModule {

    /**
     * 各在线源默认网关地址。
     * 用户可在「设置 → 在线查询」中覆盖（支持代理/自建网关/内网地址），
     * 留空时回落以下默认官方地址。
     */
    const val DEFAULT_JUHE_BASE_URL = "https://apis.juhe.cn/"
    const val DEFAULT_QCC_BASE_URL = "https://api.qichacha.com/"
    const val DEFAULT_AIQICHA_BASE_URL = "https://api.aiqicha.baidu.com/"

    /**
     * 日志仅在 Debug 构建打印 BODY（含请求/响应明细，可能含号码）。
     * Release 构建一律 NONE，避免把用户号码写入 logcat 造成隐私泄露。
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE
    }

    internal val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    /** 按 baseUrl 缓存 Retrofit 实例，同一地址复用，避免重复创建。 */
    private val retrofitCache = ConcurrentHashMap<String, Retrofit>()

    /**
     * 根据网关地址创建（或复用）Retrofit 实例。
     * 地址来自用户设置（可配置），默认值为各源的官方地址。
     */
    fun retrofitFor(baseUrl: String): Retrofit =
        retrofitCache.getOrPut(baseUrl) {
            Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
}
