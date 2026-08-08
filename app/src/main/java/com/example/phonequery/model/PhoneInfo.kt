package com.example.phonequery.model

/**
 * 统一的号码查询结果模型
 */
data class PhoneInfo(
    val number: String = "",
    val numberType: NumberType = NumberType.UNKNOWN,
    val province: String? = null,
    val city: String? = null,
    val carrier: String? = null,
    val areaCode: String? = null,
    val zipCode: String? = null,
    val spamType: String? = null,
    val spamCount: String? = null,
    val platformMarks: List<PlatformMark> = emptyList(),
    val codeNumberInfo: String? = null,
    val source: ResultSource = ResultSource.OFFLINE,
    val fromCache: Boolean = false,
    val errorMessage: String? = null
)

enum class NumberType(val displayName: String) {
    MOBILE("手机号码"),
    LANDLINE("固定电话"),
    TOLL_FREE("客服/热线"),
    UNKNOWN("未知类型")
}

enum class ResultSource(val displayName: String) {
    OFFLINE("本地离线"),
    ONLINE("在线接口"),
    CACHED("本地缓存")
}

data class PlatformMark(
    val platform: String,
    val mark: String
)
