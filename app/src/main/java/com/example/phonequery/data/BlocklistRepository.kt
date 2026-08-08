package com.example.phonequery.data

import android.content.Context
import com.example.phonequery.db.AppDatabase
import com.example.phonequery.db.BlocklistEntity
import kotlinx.coroutines.flow.Flow

/**
 * 黑白名单数据仓库
 *
 * 支持两种匹配方式：
 *  - 精确号码（EXACT）：完整号码，命中即匹配
 *  - 号段/区号前缀（PREFIX）：以该前缀开头的整段号码都被屏蔽，
 *    用于应对「营销公司频繁换号」——一条规则即可屏蔽整个号段/区号
 */
class BlocklistRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).blocklistDao()

    val blacklist: Flow<List<BlocklistEntity>> = dao.getBlacklist()
    val whitelist: Flow<List<BlocklistEntity>> = dao.getWhitelist()
    val blacklistCount: Flow<Int> = dao.countBlacklist()
    val whitelistCount: Flow<Int> = dao.countWhitelist()

    /** 新增精确号码（兼容旧逻辑） */
    suspend fun add(number: String, note: String, isBlock: Boolean) {
        val clean = number.replace(Regex("[^0-9]"), "")
        if (clean.isBlank()) return
        dao.insert(BlocklistEntity(number = clean, note = note, isBlock = isBlock))
    }

    /** 新增号段/区号前缀规则（整段匹配） */
    suspend fun addPrefix(prefix: String, label: String, isBlock: Boolean) {
        val clean = prefix.replace(Regex("[^0-9]"), "")
        if (clean.isBlank()) return
        // 先按号码去重，避免重复插入同一条规则
        findByNumber(clean)?.let { dao.delete(it) }
        dao.insert(
            BlocklistEntity(
                number = clean,
                note = label,
                isBlock = isBlock,
                type = BlocklistEntity.TYPE_PREFIX,
                label = label
            )
        )
    }

    /**
     * 一键屏蔽国内虚拟运营商 / 卫星通信号段。
     * 这些号段被大量营销、骚扰电话使用，很多主流拦截 App 不敢整段屏蔽，
     * 这里用一条规则整段封杀。号段清单复用 SpamPrefixDatabase 单一来源。
     */
    suspend fun quickBlockVirtualOperators() {
        SpamPrefixDatabase.allVirtualOperatorPrefixes().forEach { prefix ->
            addPrefix(prefix, "虚拟运营商/卫星号段 $prefix", isBlock = true)
        }
    }

    /** 按区号批量屏蔽（适用于固话骚扰，如 010 / 021 / 0755） */
    suspend fun addAreaCodeBlock(areaCode: String) {
        val clean = areaCode.replace(Regex("[^0-9]"), "")
        if (clean.isBlank()) return
        addPrefix(clean, "区号 $clean", isBlock = true)
    }

    suspend fun delete(entity: BlocklistEntity) {
        dao.delete(entity)
    }

    suspend fun isBlacklisted(number: String): Boolean {
        return dao.isBlacklisted(number.replace(Regex("[^0-9]"), ""))
    }

    suspend fun isWhitelisted(number: String): Boolean {
        return dao.isWhitelisted(number.replace(Regex("[^0-9]"), ""))
    }

    suspend fun toggle(number: String, note: String, isBlock: Boolean) {
        val clean = number.replace(Regex("[^0-9]"), "")
        val existing = findByNumber(clean)
        if (existing != null) {
            delete(existing)
        } else {
            add(clean, note, isBlock)
        }
    }

    private suspend fun findByNumber(number: String): BlocklistEntity? {
        return dao.findByNumber(number)
    }
}
