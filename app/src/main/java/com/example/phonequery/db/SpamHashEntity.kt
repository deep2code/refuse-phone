package com.example.phonequery.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 社区维护的骚扰/诈骗号码哈希表（离线，md5 匹配）。
 *
 * 设计说明：绝大多数公开「众人标记」的号码清单出于隐私考虑只公布号码的 md5，
 * 而非明文（例如 auino/global-telephone-spammers-list）。因此本表只存
 * 「E.164 号码的 md5 小写十六进制」，来电/查询时把号码规范化为 E.164 再 md5 比对，
 * 既能复用开源众包数据，又不在本地落库任何明文电话号码。
 */
@Entity(tableName = "spam_hash")
data class SpamHashEntity(
    @PrimaryKey val id: String, // md5(E.164 号码)，小写十六进制
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "source") val source: String = "global-telephone-spammers-list"
)
