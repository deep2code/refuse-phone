package com.example.phonequery.data

import android.content.Context
import com.example.phonequery.db.AppDatabase
import com.example.phonequery.db.BlocklistEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * 黑白名单数据仓库
 *
 * 支持两种匹配方式：
 *  - 精确号码（EXACT）：完整号码，命中即匹配
 *  - 号段/区号前缀（PREFIX）：以该前缀开头的整段号码都被屏蔽，
 *    用于应对「营销公司频繁换号」——一条规则即可屏蔽整个号段/区号
 */
class BlocklistRepository(context: Context) {

    // 构造参数为非属性，不能在成员函数中直接引用；保存为私有属性以便 exportAll/importAll 使用
    private val appContext: Context = context

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

    /** 新增正则规则：对纯数字号码整体正则匹配（number 存正则表达式） */
    suspend fun addRegex(pattern: String, label: String, isBlock: Boolean) {
        val p = pattern.trim()
        if (p.isBlank()) return
        // 校验正则合法性，避免存进无效规则
        runCatching { Regex(p) }.onFailure { return }
        findByNumber(p)?.let { dao.delete(it) }
        dao.insert(
            BlocklistEntity(
                number = p,
                note = label,
                isBlock = isBlock,
                type = BlocklistEntity.TYPE_REGEX,
                label = label
            )
        )
    }

    /**
     * 新增归属地规则。
     * @param region 地区名（如「西安」）
     * @param reverse true 表示逆向匹配（拦截除该地外的所有来电，规则存为 `!西安`）
     */
    suspend fun addAttr(region: String, isBlock: Boolean, reverse: Boolean) {
        val r = region.trim()
        if (r.isBlank()) return
        val stored = if (reverse) "!$r" else r
        findByNumber(stored)?.let { dao.delete(it) }
        val label = if (reverse) "逆向：除 $r 外" else "归属地：$r"
        dao.insert(
            BlocklistEntity(
                number = stored,
                note = label,
                isBlock = isBlock,
                type = BlocklistEntity.TYPE_ATTR,
                label = label
            )
        )
    }

    /**
     * 评估高级规则（正则 / 归属地）是否命中该号码。
     * @param digits 纯数字号码
     * @param city 该号码归属城市（用于归属地规则匹配；可空）
     * @return 命中的黑名单规则，未命中返回 null
     */
    suspend fun evaluateAdvanced(digits: String, city: String?): BlocklistEntity? {
        val num = digits.replace(Regex("\\D"), "")
        if (num.isBlank()) return null
        // 正则规则
        dao.getAllByType(BlocklistEntity.TYPE_REGEX).forEach { rule ->
            if (rule.isBlock) {
                runCatching { Regex(rule.number).matches(num) }.getOrDefault(false)
                    .takeIf { it }?.let { return rule }
            }
        }
        // 归属地规则（含逆向）
        if (!city.isNullOrBlank()) {
            dao.getAllByType(BlocklistEntity.TYPE_ATTR).forEach { rule ->
                if (rule.isBlock) {
                    val region = rule.number.removePrefix("!")
                    val reverse = rule.number.startsWith("!")
                    val hit = if (reverse) !city.contains(region) else city.contains(region)
                    if (hit) return rule
                }
            }
        }
        return null
    }

    /**
     * 导出全部黑白名单规则 + 关键设置到 JSON 字符串（用于备份/迁移）。
     * 仅导出与拦截相关的关键设置（在线查询开关、拦截动作、悬浮窗透明度），
     * 不导出与设备/权限相关的开关，避免导入后在新设备产生误导。
     */
    suspend fun exportAll(): String {
        val entities = dao.getAllOnce()
        val settings = SettingsDataStore(appContext).settingsFlow.first()

        val rules = JSONArray()
        entities.forEach { e ->
            rules.put(
                JSONObject().apply {
                    put("number", e.number)
                    put("type", e.type)
                    put("label", e.label)
                    put("note", e.note)
                    put("isBlock", e.isBlock)
                }
            )
        }

        val settingsObj = JSONObject().apply {
            put("enableOnlineLookup", settings.enableOnlineLookup)
            put("interceptAction", settings.interceptAction)
            put("floatingAlpha", settings.floatingAlpha)
        }

        return JSONObject().apply {
            put("app", "refuse-phone")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("rules", rules)
            put("settings", settingsObj)
        }.toString(2)
    }

    /**
     * 从 JSON 字符串导入备份：恢复黑白名单规则 + 关键设置。
     * - 规则按 number 去重，已存在的先删除再插入，避免重复；
     * - 正则规则会先校验合法性，无效规则跳过；
     * - 设置仅在 JSON 含 settings 时恢复。
     * @return 实际导入的规则条数
     */
    suspend fun importAll(json: String): Int {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return 0
        val rules = obj.optJSONArray("rules") ?: return 0

        val validTypes = setOf(
            BlocklistEntity.TYPE_EXACT,
            BlocklistEntity.TYPE_PREFIX,
            BlocklistEntity.TYPE_REGEX,
            BlocklistEntity.TYPE_ATTR
        )

        var imported = 0
        for (i in 0 until rules.length()) {
            val r = runCatching { rules.getJSONObject(i) }.getOrNull() ?: continue
            val number = r.optString("number").replace(Regex("[^0-9!]"), "")
            val type = r.optString("type", BlocklistEntity.TYPE_EXACT)
            val label = r.optString("label")
            val note = r.optString("note")
            val isBlock = r.optBoolean("isBlock", true)
            if (number.isBlank() || type !in validTypes) continue
            // 正则规则校验合法性（inline lambda 内不允许 break/continue，改用 isSuccess 判断后 continue）
            if (type == BlocklistEntity.TYPE_REGEX) {
                if (!runCatching { Regex(number) }.isSuccess) continue
            }
            // 去重：删除已有同号规则再插入
            findByNumber(number)?.let { dao.delete(it) }
            dao.insert(
                BlocklistEntity(
                    number = number,
                    note = note,
                    isBlock = isBlock,
                    type = type,
                    label = label
                )
            )
            imported++
        }

        obj.optJSONObject("settings")?.let { s ->
            val ds = SettingsDataStore(appContext)
            if (s.has("enableOnlineLookup")) ds.updateOnlineLookup(s.optBoolean("enableOnlineLookup", false))
            if (s.has("interceptAction")) ds.updateInterceptAction(
                s.optString("interceptAction", AppSettings.INTERCEPT_BLOCK)
            )
            if (s.has("floatingAlpha")) ds.updateFloatingAlpha(s.optDouble("floatingAlpha", 0.9).toFloat())
        }

        return imported
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
