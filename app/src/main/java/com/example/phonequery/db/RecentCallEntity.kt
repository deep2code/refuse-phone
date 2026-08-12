package com.example.phonequery.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 最近来电记录（本地，离线）。
 * 由系统级识别（ScreeningService）与前台服务（CallHandlerService）在来电时写入，
 * 用于「最近来电」列表展示：号码、归属地/标记、是否被拦截、时间。
 */
@Entity(tableName = "recent_call")
data class RecentCallEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val number: String,
    val digits: String = "",
    val name: String? = null,
    val description: String? = null,
    val blocked: Boolean = false,
    val spamType: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
