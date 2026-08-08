package com.example.phonequery.data

/**
 * 内置骚扰号段库（零 key、纯离线）。
 *
 * 数据来源于公开的开源骚扰号码研究（如 BanHarassment 等项目的号段统计），
 * 仅作为「风险提示」第一层 —— 命中后标红/标记，但不自动拦截，
 * 把最终决策权留给用户（结合黑白名单与求职保护模式）。
 *
 * 注意：虚拟运营商号段（170/171/165/167 等）被大量营销/诈骗电话滥用，
 * 但并非全部是骚扰号；这里只做提示，不默认硬拦截，避免误杀正常快递/外卖/打车来电。
 */
object SpamPrefixDatabase {

    /** 虚拟运营商号段：营销/诈骗高发，需重点留意 */
    val VIRTUAL_OPERATOR_PREFIXES = listOf(
        "170", "171", "162", "165", "167", "174", "172"
    )

    /** 公认高频营销/骚扰号段（仅提示，不硬拦） */
    val HIGH_RISK_PREFIXES = listOf(
        "95", "96", "400", "800"   // 95/96 为增值业务号段，400/800 为企业客服，常被冒用
    )

    data class SpamHint(
        val level: Level,
        val label: String
    )

    enum class Level {
        VIRTUAL_OPERATOR,   // 虚拟运营商号段
        HIGH_RISK           // 公认高风险号段
    }

    /**
     * 判断号码是否命中已知骚扰号段前缀。
     * @return 命中则返回提示，否则 null
     */
    fun match(number: String): SpamHint? {
        val digits = number.replace(Regex("\\D"), "")
        if (digits.length < 3) return null

        for (prefix in VIRTUAL_OPERATOR_PREFIXES) {
            if (digits.startsWith(prefix)) {
                return SpamHint(
                    Level.VIRTUAL_OPERATOR,
                    "虚拟运营商号段（$prefix 开头），营销/诈骗电话高发，请留意"
                )
            }
        }

        for (prefix in HIGH_RISK_PREFIXES) {
            if (digits.startsWith(prefix)) {
                return SpamHint(
                    Level.HIGH_RISK,
                    "$prefix 号段（常被营销/诈骗冒用）"
                )
            }
        }

        return null
    }

    /**
     * 返回所有内置虚商号段前缀（供「一键屏蔽虚商号段」功能复用）
     */
    fun allVirtualOperatorPrefixes(): List<String> = VIRTUAL_OPERATOR_PREFIXES
}
