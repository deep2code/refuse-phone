package com.example.phonequery.network

import com.example.phonequery.model.JuheMobileResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 聚合数据「手机号归属地」接口（key 在编译期通过 BuildConfig.JUHE_KEY 注入）。
 * 免费额度约 50 次/天，仅提供归属地，不提供骚扰标记。
 */
interface JuheService {
    @GET("mobile/get")
    suspend fun getMobile(
        @Query("phone") phone: String,
        @Query("key") key: String
    ): JuheMobileResponse
}
