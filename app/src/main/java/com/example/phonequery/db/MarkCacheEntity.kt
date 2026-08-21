package com.example.phonequery.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本地标记缓存表。
 *
 * 把在线查询（外部网关 / 阿里云）查到的号码标记 / 固话企业反查结果落库，
 * 实现「越用越准的个人内置标记库」：
 * - 在线查询成功 → 写入缓存；
 * - 断网或接口失效 → 回退到此缓存，仍可标记骚扰/诈骗/企业。
 *
 * cacheType 区分两类数据：
 * - MARK：手机号/固话的标记（骚扰、诈骗、运营商等）
 * - ENTERPRISE：固话反查到的企业名称列表
 */
@Entity(tableName = "mark_cache")
data class MarkCacheEntity(
    @PrimaryKey
    val id: String,                 // "${number}_${cacheType}"
    val number: String,             // 归一化后的纯数字号码
    val cacheType: String,          // MARK | ENTERPRISE
    val province: String? = null,
    val city: String? = null,
    val carrier: String? = null,
    val spamType: String? = null,
    val spamCount: String? = null,
    val marksJson: String? = null,       // JSON 数组：List<PlatformMark>
    val enterpriseJson: String? = null,  // JSON 数组：List<String> 企业名
    val updatedAt: Long = System.currentTimeMillis()
)
