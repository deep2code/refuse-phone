package com.example.phonequery.call

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.telecom.TelecomManager

/**
 * 默认拨号应用角色（ROLE_DIALER）辅助。
 *
 * 仅「主动挂断来电」(TelecomManager.endCall) 需要该角色；来电识别 / 系统级拦截
 * 由 CallScreeningService(ROLE_CALL_SCREENING) 或悬浮窗承担，与该角色无关。
 *
 * 注意：华为 / HarmonyOS 等 ROM 通常不允许第三方应用成为默认拨号应用，
 * 此时 [isHeld] 恒为 false，主动挂断功能不可用，应引导用户改用系统级来电识别。
 */
object DefaultDialerRole {

    /** 当前应用是否已持有默认拨号角色。 */
    fun isHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val tm = context.getSystemService(TelecomManager::class.java) ?: return false
            return tm.defaultDialerPackage == context.packageName
        }
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleHeld(RoleManager.ROLE_DIALER)
    }

    /** 构造申请默认拨号角色的系统 Intent（Android 10+）；不支持时返回 null。 */
    fun createRequestIntent(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                ?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } else null
}
