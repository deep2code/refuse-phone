package com.example.phonequery.network

import com.example.phonequery.model.TminiEnterpriseResponse
import com.example.phonequery.model.TminiMarkResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * tmini.net 第三方聚合网关（零 key、GET 直调）。
 *
 * 它把国内主流标记平台统一成一个接口：
 *  - 号码标记：覆盖腾讯手机管家、360、百度、华为、小米、搜狗、电话邦、泰迪熊、移动、联通 10+ 平台
 *  - 企业/固话反查：电话邦数据源，支持手机号/座机号查询
 *
 * 注意：这是第三方聚合（拼接各家平台接口），稳定性/限流/合规需自担风险，
 * 建议仅作为「默认免费查询源 + 本地黑名单兜底」，不要作为自动挂断的唯一判据。
 * 对应官方文档见各平台号码标记查询聚合接口说明。
 */
interface TminiService {

    /**
     * 号码标记查询（手机/固话均可，返回聚合后的标签）
     * https://www.tmini.net/api/haoma?phone=xxxx
     */
    @GET("api/haoma")
    suspend fun queryMark(
        @Query("phone") phone: String
    ): TminiMarkResponse

    /**
     * 企业/固话反查（电话邦数据源，返回认证企业信息）
     * https://www.tmini.net/api/dianhua?phone=xxxx 或 ?q=企业名
     */
    @GET("api/dianhua")
    suspend fun queryEnterprise(
        @Query("phone") phone: String
    ): TminiEnterpriseResponse
}
