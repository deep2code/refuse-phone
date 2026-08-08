package com.example.phonequery.model

/**
 * 固话推断出的城市信息
 */
data class LandlineLocation(
    val areaCode: String,
    val city: String,
    val province: String? = null,
    val localNumber: String
)

/**
 * 相似企业查询条件
 */
data class SimilarCompanyQuery(
    val originalNumber: String,
    val location: LandlineLocation,
    val similarNumbers: List<String>
)

/**
 * 企业工商信息统一模型
 */
data class EnterpriseInfo(
    val name: String,
    val industry: String? = null,
    val legalPerson: String? = null,
    val creditCode: String? = null,
    val regNo: String? = null,
    val status: String? = null,
    val regCapital: String? = null,
    val establishDate: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val source: String
)
