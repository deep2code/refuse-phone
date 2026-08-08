package com.example.phonequery.call

import android.app.role.RoleManager
import android.content.Context
import android.os.Build

/**
 * CallScreeningService 角色（ROLE_CALL_SCREENING）辅助。
 *
 * Android 10+ 的来电筛选服务只有在应用持有「来电筛选」角色时才会被系统回调。
 * 普通应用无法自动获得该角色，必须由用户在一次性授权对话框中确认。
 */
object CallScreeningRole {

    /** 当前应用是否已持有来电筛选角色。 */
    fun isHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    /** 构造申请「来电筛选」角色的系统 Intent；不支持（< Android 10）时返回 null。 */
    fun createRequestIntent(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                ?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
        } else null
}
