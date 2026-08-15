package com.example.phonequery.network

import com.example.phonequery.model.JuheMarkResponse
import com.example.phonequery.model.JuheMobileResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 聚合数据接口。key 在运行时由用户在设置界面填写，经构造注入（非编译期常量）。
 * - [getMobile] 手机号归属地（免费额度有限，仅归属地）。
 * - [queryMark] 骚扰标记（诈骗/骚扰/推销 + 标记次数），需个人实名 key。
 */
interface JuheService {
    @GET("mobile/get")
    suspend fun getMobile(
        @Query("phone") phone: String,
        @Query("key") key: String
    ): JuheMobileResponse

    /**
     * 聚合数据「来电号码显示 / 号码标记」接口（id/511，mobileVerify/query）。
     * 返回号码标记类型（诈骗/骚扰/推销/快递/中介…）与标记次数。
     * 文档：https://www.juhe.cn/docs/api/id/511
     */
    @GET("mobileVerify/query")
    suspend fun queryMark(
        @Query("key") key: String,
        @Query("mobile") mobile: String,
        @Query("country") country: String = "86"
    ): JuheMarkResponse
}
