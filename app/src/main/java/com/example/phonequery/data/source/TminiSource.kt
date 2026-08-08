package com.example.phonequery.data.source

import com.example.phonequery.model.NumberType
import com.example.phonequery.model.PlatformMark
import com.example.phonequery.network.TminiService

/**
 * 默认在线源：tmini.net 免费聚合网关（零 key）。
 * 覆盖 10+ 平台的号码标记（含 360 / 百度 / 腾讯等）以及固话企业反查。
 */
class TminiSource(private val service: TminiService) : OnlineMarkSource {
    override val name = "tmini"
    override val isEnabled = true

    override suspend fun query(number: String, type: NumberType): SourceResult? {
        val digits = number.replace(Regex("\\D"), "")
        return when (type) {
            NumberType.MOBILE -> {
                val mark = runCatching { service.queryMark(digits) }.getOrNull()
                val data = mark?.data
                val tag = data?.tag
                val isScam = data?.isScam == true
                SourceResult(
                    sourceName = name,
                    spamType = if (isScam) "疑似诈骗电话" else null,
                    spamCount = if (data?.count != null) "×${data.count}" else null,
                    marks = if (!tag.isNullOrBlank()) {
                        listOf(
                            PlatformMark(
                                "tmini聚合(${data.tagType ?: "标记"})",
                                buildString {
                                    append(tag)
                                    if (data.count != null) append(" ×${data.count}")
                                }
                            )
                        )
                    } else {
                        emptyList()
                    }
                )
            }
            NumberType.LANDLINE -> {
                val ent = runCatching { service.queryEnterprise(digits) }.getOrNull()
                val name2 = ent?.items?.firstOrNull()?.name
                SourceResult(
                    sourceName = name,
                    enterpriseName = name2,
                    marks = if (!name2.isNullOrBlank()) listOf(PlatformMark("电话邦企业", name2)) else emptyList()
                )
            }
            else -> {
                val mark = runCatching { service.queryMark(digits) }.getOrNull()
                val tag = mark?.data?.tag
                SourceResult(
                    sourceName = name,
                    marks = if (!tag.isNullOrBlank()) listOf(PlatformMark("tmini聚合", tag)) else emptyList()
                )
            }
        }
    }
}
