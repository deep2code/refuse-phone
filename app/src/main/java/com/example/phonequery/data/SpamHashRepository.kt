package com.example.phonequery.data

import android.content.Context
import com.example.phonequery.db.AppDatabase
import com.example.phonequery.db.SpamHashEntity
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 社区维护骚扰/诈骗号码库（md5 离线匹配）。
 *
 * - 首启动时从 assets 下的种子文件灌库（幂等：表为空才导入）。
 *   - seed_spammers.csv：社区维护的骚扰号码（value 多为 32 位 md5，含少量明文）。
 *   - seed_community.csv：社区众包诈骗/骚扰黑名单（明文 E.164 号码，本地自动算 md5）。
 *     当前内置来源：nathanu98/ScammerPhoneNumbers（美国诈骗号码）、
 *     Shalom-Karr/AI-Number-Blocklist（AI 机器人骚扰号码）。
 * - 来电/查询时把号码规范化为 E.164 再取 md5，与本地哈希表比对，命中即标记为已知骚扰。
 *
 * CSV 格式（description,value）：
 * - value 为 32 位十六进制 → 视为已算好的 md5，直接入库；
 * - value 为明文号码（如 +8613800138000）→ 规范化后本地算 md5 入库。
 * 这样用户可随时往 assets 里追加自己的明文号码清单，无需预计算 md5。
 */
class SpamHashRepository(context: Context) {

    // 用 applicationContext 持有，避免仓库长生命周期泄漏 Activity
    private val appContext: Context = context.applicationContext
    private val dao = AppDatabase.getInstance(context).spamHashDao()
    private val seeded = AtomicBoolean(false)

    /** 确保已灌入种子数据（仅首次、且表为空时）。 */
    suspend fun ensureSeeded() {
        if (seeded.get()) return
        if (dao.count() > 0) {
            seeded.set(true)
            return
        }
        val all = mutableListOf<SpamHashEntity>()
        for (asset in listOf("seed_spammers.csv", "seed_community.csv")) {
            runCatching {
                appContext.assets.open(asset).use { stream ->
                    all += parseCsv(stream.bufferedReader().readText())
                }
            }
        }
        if (all.isNotEmpty()) dao.insertAll(all)
        seeded.set(true)
    }

    /** 按明文号码匹配已知骚扰库；命中返回描述，否则 null。 */
    suspend fun match(rawNumber: String): SpamHashEntity? {
        ensureSeeded()
        val e164 = normalizeToE164(rawNumber)
        if (e164.isBlank()) return null
        val hash = md5Hex(e164)
        return runCatching { dao.getByHash(hash) }.getOrNull()
    }

    /** 库内哈希条数（用于设置页展示） */
    suspend fun count(): Int {
        ensureSeeded()
        return runCatching { dao.count() }.getOrDefault(0)
    }

    private fun parseCsv(text: String): List<SpamHashEntity> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val dataLines = if (lines.first().contains("description", ignoreCase = true)) lines.drop(1) else lines
        return dataLines.mapNotNull { line ->
            val parts = line.split(",", limit = 2)
            if (parts.size < 2) return@mapNotNull null
            val desc = parts[0].trim()
            val value = parts[1].trim()
            if (desc.isBlank() || value.isBlank()) return@mapNotNull null

            val (hash, source) = if (value.length == 32 && value.all { it in "0123456789abcdefABCDEF" }) {
                value.lowercase() to "global-telephone-spammers-list"
            } else {
                md5Hex(normalizeToE164(value)) to "seed-plaintext"
            }
            SpamHashEntity(id = hash, description = desc, source = source)
        }
    }

    /**
     * 规范化为 E.164（+ 国家码 + 号码，无空格/横线）。
     * 例：13800138000 → +8613800138000；01012345678 → +861012345678。
     */
    private fun normalizeToE164(raw: String): String {
        val cleaned = raw.replace(Regex("[\\s()-]"), "").replace("＋", "+")
        return when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("86") && cleaned.length == 13 -> "+$cleaned"
            cleaned.startsWith("0") && cleaned.length >= 10 -> "+86" + cleaned.removePrefix("0")
            cleaned.all { it.isDigit() } && cleaned.length == 11 && cleaned.startsWith("1") -> "+86$cleaned"
            else -> cleaned
        }
    }

    private fun md5Hex(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
