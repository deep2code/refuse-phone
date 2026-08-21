package com.example.phonequery.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 外部网关接口（自建 / 代理网关，默认 http://114.55.170.79:5050）。
 *
 * 约定：GET {baseUrl}query?phone={11位手机号}，返回 JSON。
 * 地址可在「设置 → 在线查询 → 外部接口地址」中修改，留空回落默认地址。
 */
interface GatewayService {
    @GET("query")
    suspend fun query(@Query("phone") phone: String): ResponseBody
}
