package com.example.phonequery.data

import android.content.Context
import com.example.phonequery.db.AppDatabase
import com.example.phonequery.db.SpamHashEntity
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 社区维护骚扰/诈骗号码库（md5 离线匹配）。
 *
 * - 离线哈希匹配机制保留：来电/查询时把号码规范化为 E.164 再取 md5，与本地哈希表比对，命中即标记为已知骚扰。
 * - 种子文件：原 seed_spammers.csv / seed_community.csv 均为国外（+1 美国）号码，
 *   与中国来电匹配不上，已于 2026-08-15 删除；当前表为空（离线骚扰识别零覆盖）。
 *   如需恢复，请往 assets 放入中国号码种子文件并在 [ensureSeeded] 中登记。
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
        // 国外种子（seed_spammers.csv / seed_community.csv，均为 +1 美国号）已于 2026-08-15 删除。
        // 当前无中国源种子文件；如需离线骚扰识别，把中国号码种子文件放入 assets 并在此登记。
        val assets = emptyList<String>()
        val all = mutableListOf<SpamHashEntity>()
        for (asset in assets) {
            runCatching {
                appContext.assets.open(asset).use { stream ->
                    all += parseCsv(stream.bufferedReader().readText())
                }
            }
        }
        if (all.isNotEmpty()) dao.insertAll(all)
        seeded.set(true)
    }

    /**
     * 按明文号码匹配已知骚扰库；命中返回描述，否则 null。
     *
     * @param allowSeed 是否允许匹配前同步灌库。来电热路径（系统级 CallScreeningService）
     *   应传 false：若尚未预填充完成则直接跳过，避免在主线程/系统来电回调线程同步插入
     *   7.3 万条哈希导致卡死/ANR；后台预填充完成后下次来电即可命中。手动查号路径用默认 true。
     */
    suspend fun match(rawNumber: String, allowSeed: Boolean = true): SpamHashEntity? {
        if (allowSeed) {
            ensureSeeded()
        } else if (dao.count() == 0) {
            return null
        }
        val e164 = E164Normalizer.normalize(rawNumber)
        if (e164.isBlank()) return null
        val hash = E164Normalizer.md5Hex(e164)
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
                E164Normalizer.md5Hex(E164Normalizer.normalize(value)) to "seed-plaintext"
            }
            SpamHashEntity(id = hash, description = desc, source = source)
        }
    }

    /**
     * 规范化为 E.164（+ 国家码 + 号码，无空格/横线）。
     * 例：13800138000 → +8613800138000；01012345678 → +861012345678。
     */
    private fun normalizeToE164(raw: String): String {
        return E164Normalizer.normalize(raw)
    }

    private fun md5Hex(input: String): String {
        return E164Normalizer.md5Hex(input)
    }
}
