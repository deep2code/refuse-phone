package com.example.phonequery.data

import java.security.MessageDigest

/**
 * 号码规范化与 md5 计算的纯函数实现（从 [SpamHashRepository] 抽取，便于离线单元测试）。
 * 行为与原私有方法完全一致。
 */
object E164Normalizer {

    /**
     * 规范化为 E.164（+ 国家码 + 号码，无空格/横线）。
     * 例：13800138000 → +8613800138000；01012345678 → +861012345678。
     */
    fun normalize(raw: String): String {
        val cleaned = raw.replace(Regex("[\\s()-]"), "").replace("＋", "+")
        return when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("86") && cleaned.length == 13 -> "+$cleaned"
            cleaned.startsWith("0") && cleaned.length >= 10 -> "+86" + cleaned.removePrefix("0")
            cleaned.all { it.isDigit() } && cleaned.length == 11 && cleaned.startsWith("1") -> "+86$cleaned"
            else -> cleaned
        }
    }

    fun md5Hex(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
