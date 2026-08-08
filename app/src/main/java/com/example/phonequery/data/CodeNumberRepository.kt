package com.example.phonequery.data

import android.content.Context
import com.example.phonequery.db.AppDatabase
import com.example.phonequery.db.CodeNumberEntity
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 工信部电信网码号资源离线仓库（95/96/106/400/800 号段 → 使用单位）。
 *
 * - 首启动从 assets/seed_codenumber.csv 灌库（仅表为空时导入）。
 * - 来电/查询时按「最长号段前缀」匹配，用于「陌生 95 号识别」：
 *   例如 9543729 命中种子中的 954372 → 显示其使用单位，帮助用户判断是否为合规客服或可疑外呼。
 * - 权威数据来自 nac.miit.gov.cn；scripts/fetch_codenumber.py 可拉取最新分配并刷新 seed CSV。
 */
class CodeNumberRepository(context: Context) {

    // 用 applicationContext 持有，避免仓库长生命周期泄漏 Activity
    private val appContext: Context = context.applicationContext
    private val dao = AppDatabase.getInstance(context).codeNumberDao()
    private val seeded = AtomicBoolean(false)
    @Volatile private var cache: List<CodeNumberEntity>? = null

    suspend fun ensureSeeded() {
        if (seeded.get()) return
        if (dao.count() > 0) {
            seeded.set(true)
            return
        }
        runCatching {
            appContext.assets.open("seed_codenumber.csv").use { stream ->
                val text = stream.bufferedReader().readText()
                val rows = parseCsv(text)
                if (rows.isNotEmpty()) dao.insertAll(rows)
            }
        }
        seeded.set(true)
    }

    private suspend fun all(): List<CodeNumberEntity> {
        ensureSeeded()
        cache?.let { return it }
        val list = runCatching { dao.getAll() }.getOrDefault(emptyList())
        // 按前缀长度降序，便于「最长匹配」优先
        val sorted = list.sortedByDescending { it.id.length }
        cache = sorted
        return sorted
    }

    /**
     * 查询号码对应的码号资源信息；命中返回实体，否则 null。
     * 仅对 95/96/106/400/800 等特殊号段尝试匹配，普通手机/固话直接返回 null。
     */
    suspend fun lookup(rawNumber: String): CodeNumberEntity? {
        val digits = rawNumber.replace(Regex("\\D"), "")
            .removePrefix("86")
            .removePrefix("+")
        if (digits.length < 3) return null
        if (!Regex("^(95|96|106|400|800)").containsMatchIn(digits)) return null
        val list = all()
        return list.firstOrNull { digits.startsWith(it.id) }
    }

    /** 组合成展示文案，如「中国工商银行（银行客服热线）」 */
    fun toDisplay(entity: CodeNumberEntity): String = buildString {
        append(entity.owner)
        if (!entity.purpose.isNullOrBlank()) append("（${entity.purpose}）")
    }

    private fun parseCsv(text: String): List<CodeNumberEntity> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val dataLines = if (lines.first().startsWith("prefix", ignoreCase = true)) lines.drop(1) else lines
        return dataLines.mapNotNull { line ->
            val parts = line.split(",", limit = 6)
            if (parts.size < 3) return@mapNotNull null
            val prefix = parts[0].trim()
            val type = parts[1].trim()
            val owner = parts[2].trim()
            if (prefix.isBlank() || type.isBlank() || owner.isBlank()) return@mapNotNull null
            CodeNumberEntity(
                id = prefix,
                type = type,
                owner = owner,
                purpose = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
                validUntil = parts.getOrNull(4)?.takeIf { it.isNotBlank() },
                note = parts.getOrNull(5)?.takeIf { it.isNotBlank() }
            )
        }
    }

    /** 表内条数（用于设置页展示） */
    suspend fun count(): Int {
        ensureSeeded()
        return runCatching { dao.count() }.getOrDefault(0)
    }
}
