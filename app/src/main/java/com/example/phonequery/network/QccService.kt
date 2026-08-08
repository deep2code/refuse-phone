package com.example.phonequery.network

import com.example.phonequery.model.QccSearchResponse
import com.example.phonequery.model.QccVerifyResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * 企查查开放平台（key 在编译期通过 BuildConfig.QCC_KEY / QCC_TOKEN 注入）。
 * - ApiCode 886 企业模糊搜索：可按电话号码反查公司（返回名称/法人/状态）。
 * - ApiCode 2001 企业信息核验：返回 联系信息、企查查行业、法人、状态等。
 * 认证：query 参数 key=APPKEY，请求头 Token=TOKEN（企查查官方约定）。
 */
interface QccService {
    @GET("ECIV4/GetV4")
    suspend fun search(
        @Query("key") key: String,
        @Header("Token") token: String,
        @Query("keyword") keyword: String
    ): QccSearchResponse

    @GET("EnterpriseInfo/Verify")
    suspend fun verify(
        @Query("key") key: String,
        @Header("Token") token: String,
        @Query("keyword") keyword: String
    ): QccVerifyResponse
}
