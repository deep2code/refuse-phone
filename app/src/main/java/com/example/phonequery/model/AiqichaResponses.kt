package com.example.phonequery.model

/** 百度爱企查开放 API：企业搜索（按关键字，可用于按电话反查公司） */
data class AiqichaSearchResponse(
    val status: Int? = null,
    val message: String? = null,
    val data: AiqichaData? = null
)

data class AiqichaData(
    val list: List<AiqichaCompany>? = null
)

data class AiqichaCompany(
    val name: String? = null,
    val industry: String? = null,
    val legalPerson: String? = null,
    val status: String? = null
)
