package com.example.phonequery.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 工信部电信网码号资源离线表（95/96/106/400/800 号段 → 使用单位）。
 *
 * 权威数据来自 nac.miit.gov.cn（电信网码号资源使用和调整审批系统）的「号码查询」端口；
 * 具体每条记录的 owner（使用单位）/ purpose（用途）/ validUntil（有效期）由该系统的公开查询结果得来。
 * 本项目仅内置一份「精选种子 + 号段类别兜底」，完整数据请用 scripts/fetch_codenumber.py 刷新。
 *
 * id 为号段前缀（如 "95588"、"954372"、"400"、"95"），匹配时取最长前缀命中。
 */
@Entity(tableName = "code_number")
data class CodeNumberEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "type") val type: String,        // 95 / 96 / 106 / 400 / 800
    @ColumnInfo(name = "owner") val owner: String,       // 使用单位（公司/机构名）
    @ColumnInfo(name = "purpose") val purpose: String?,   // 用途（如 银行客服热线）
    @ColumnInfo(name = "valid_until") val validUntil: String?, // 有效期
    @ColumnInfo(name = "note") val note: String?         // 备注（如 五大行/区域呼叫中心常为营销）
)
