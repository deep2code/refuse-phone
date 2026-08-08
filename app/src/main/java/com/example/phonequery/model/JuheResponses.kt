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
