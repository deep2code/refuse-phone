package com.example.phonequery.model

/** 企查查开放平台：ApiCode 886 企业模糊搜索（可按电话号码反查公司） */
data class QccSearchResponse(
    val status: String? = null,
    val message: String? = null,
    val result: QccSearchResult? = null
)

data class QccSearchResult(
    val total: Int? = null,
    val items: List<QccSearchItem>? = null
)

data class QccSearchItem(
    val name: String? = null,
    val creditCode: String? = null,
    val legalPerson: String? = null,
    val status: String? = null,
    val establishDate: String? = null,
    val regCapital: String? = null
)

/** 企查查开放平台：ApiCode 2001 企业信息核验（返回 联系信息、企查查行业、法人、状态） */
data class QccVerifyResponse(
    val status: String? = null,
    val message: String? = null,
    val result: QccVerifyResult? = null
)

data class QccVerifyResult(
    val name: String? = null,
    val industry: String? = null,        // 企查查行业（如「货币金融服务」）
    val industryCode: String? = null,
    val legalPerson: String? = null,
    val status: String? = null,
    val regCapital: String? = null,
    val establishDate: String? = null,
    val creditCode: String? = null
)
