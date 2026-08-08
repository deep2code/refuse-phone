package com.example.phonequery.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    internal val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    /**
     * tmini.net 免费聚合网关（零 key）。
     * 作为默认的号码标记 + 企业反查来源，无需配置任何 API Key 即可使用。
     */
    val tminiRetrofit: Retrofit = Retrofit.Builder()
        .client(okHttpClient)
        .baseUrl("https://www.tmini.net/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /**
     * 聚合数据接口（可选在线源）。key 在编译期通过 BuildConfig.JUHE_KEY 注入。
     */
    val juheRetrofit: Retrofit = Retrofit.Builder()
        .client(okHttpClient)
        .baseUrl("https://apis.juhe.cn/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /**
     * 企查查开放平台（可选企业源）。key 在编译期通过 BuildConfig.QCC_KEY / QCC_TOKEN 注入。
     */
    val qccRetrofit: Retrofit = Retrofit.Builder()
        .client(okHttpClient)
        .baseUrl("https://api.qichacha.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /**
     * 百度爱企查开放 API（可选企业源）。key 在编译期通过 BuildConfig.AIQICHA_APIKEY 注入。
     */
    val aiqichaRetrofit: Retrofit = Retrofit.Builder()
        .client(okHttpClient)
        .baseUrl("https://api.aiqicha.baidu.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
