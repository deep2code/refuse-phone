package com.example.phonequery.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * 通讯录查询工具：判断来电号码是否存在于本地通讯录。
 * 用于「仅放行通讯录（拦截其余所有）」模式。
 */
object ContactChecker {

    /** 是否已授予读取通讯录权限 */
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 号码是否在通讯录中（按归一化号码匹配）。
     * 无权限或查询出错时返回 false（保守放行，避免误拦）。
     */
    fun isInContacts(context: Context, number: String): Boolean {
        if (!hasPermission(context)) return false
        val digits = number.replace(Regex("\\D"), "")
        if (digits.isBlank()) return false
        return try {
            // 依次尝试原始号码与纯数字，覆盖 +86 / 分隔符 / 区号等情形
            match(context, number) || match(context, digits)
        } catch (e: Exception) {
            false
        }
    }

    private fun match(context: Context, number: String): Boolean {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(number)
            .build()
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup._ID),
            null, null, null
        )?.use { return it.count > 0 }
        return false
    }
}
