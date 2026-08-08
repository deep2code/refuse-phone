package com.example.phonequery.network

import com.example.phonequery.model.AiqichaSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 百度爱企查开放 API（key 在编译期通过 BuildConfig.AIQICHA_APIKEY 注入）。
 * 按关键字搜索企业；此处用于按电话号码反查公司名 / 行业。
 * 认证：query 参数 apikey=APIKEY。返回结构以实际 API 文档为准，解析失败时安全返回 null。
 */
interface AiqichaService {
    @GET("v1/company/search")
    suspend fun search(
        @Query("apikey") apikey: String,
        @Query("keyword") keyword: String
    ): AiqichaSearchResponse
}
