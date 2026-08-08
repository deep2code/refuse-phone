package com.example.phonequery.model

import com.google.gson.annotations.SerializedName

// tmini.net 免费聚合网关响应模型
// 号码标记：https://www.tmini.net/api/haoma?phone=xxxx
data class TminiMarkResponse(
    @SerializedName("code") val code: Int? = null,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("data") val data: TminiMarkData? = null
)

data class TminiMarkData(
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("tag_type") val tagType: String? = null,
    @SerializedName("count") val count: Int? = null,
    @SerializedName("logo") val logo: String? = null,
    @SerializedName("is_scam") val isScam: Boolean? = null
)

// 企业/固话反查：https://www.tmini.net/api/dianhua?phone=xxxx
data class TminiEnterpriseResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("numitms") val numItems: Int? = null,
    @SerializedName("itms") val items: List<TminiEnterpriseItem>? = null
)

data class TminiEnterpriseItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("tels") val tels: List<TminiTel>? = null,
    @SerializedName("logo") val logo: String? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("auth") val auth: Int? = null
)

data class TminiTel(
    @SerializedName("tel_des") val desc: String? = null,
    @SerializedName("tel_num") val num: String? = null
)
