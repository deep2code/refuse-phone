package com.example.phonequery.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 黑白名单实体
 * isBlock = true 表示黑名单，false 表示白名单
 *
 * type 表示匹配方式：
 *  - [TYPE_EXACT] 精确号码（默认，兼容旧数据）
 *  - [TYPE_PREFIX] 号段/区号前缀匹配（整段屏蔽，用于虚拟运营商号段、固话区号批量拦截）
 */
@Entity(tableName = "blocklist")
data class BlocklistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val number: String,
    val note: String = "",
    val isBlock: Boolean = true,
    val type: String = TYPE_EXACT,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_EXACT = "EXACT"
        const val TYPE_PREFIX = "PREFIX"
    }
}
