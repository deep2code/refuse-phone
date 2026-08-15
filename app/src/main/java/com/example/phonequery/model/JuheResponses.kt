package com.example.phonequery.model

/**
 * 聚合数据「手机号归属地」API（id/11）响应。
 * 文档：https://www.juhe.cn/docs/api/id/11
 */
data class JuheMobileResponse(
    val error_code: Int = -1,
    val reason: String? = null,
    val result: JuheMobileResult? = null
)

data class JuheMobileResult(
    val province: String? = null,
    val city: String? = null,
    val areacode: String? = null,
    val zip: String? = null,
    val company: String? = null, // 运营商，如 移动/联通/电信
    val card: String? = null      // 卡类型，如 中国移动
)

/**
 * 聚合数据「来电号码显示 / 号码标记」API（id/511）响应。
 * 文档：https://www.juhe.cn/docs/api/id/511
 */
data class JuheMarkResponse(
    val error_code: Int = -1,
    val reason: String? = null,
    val result: JuheMarkResult? = null
)

data class JuheMarkResult(
    val name: String? = null,        // 商户名称（若为企业的标记）
    val teldesc: String? = null,     // 电话描述信息
    val telloc: String? = null,      // 号码归属地
    val flag: JuheMarkFlag? = null   // 号码标记数据对象
)

data class JuheMarkFlag(
    val type: String? = null,        // 标记类型：诈骗电话/骚扰电话/推销/房产中介/快递送餐…
    val num: Int? = null             // 标记次数
)
