package com.example.phonequery.data

import com.example.phonequery.db.BlocklistEntity

/**
 * 黑名单高级规则（正则 / 归属地）匹配的纯函数实现（从 [BlocklistRepository.evaluateAdvanced] 抽取），
 * 便于离线单元测试，且行为与原实现完全一致。
 */
object BlocklistEvaluator {

    /** 正则规则：对纯数字号码整体匹配（Regex.matches）。 */
    fun matchesRegexRule(rule: BlocklistEntity, num: String): Boolean {
        return runCatching { Regex(rule.number).matches(num) }.getOrDefault(false)
    }

    /**
     * 归属地规则（含逆向）：
     * - 正向（如「西安」）：号码归属城市包含该地区即命中；
     * - 逆向（如「!西安」）：归属城市不包含该地区即命中（即「除西安外全部拦截」）。
     */
    fun matchesAttrRule(rule: BlocklistEntity, city: String?): Boolean {
        if (city.isNullOrBlank()) return false
        val region = rule.number.removePrefix("!")
        val reverse = rule.number.startsWith("!")
        return if (reverse) !city.contains(region) else city.contains(region)
    }
}
