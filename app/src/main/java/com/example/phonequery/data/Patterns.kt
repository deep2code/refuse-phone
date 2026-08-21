package com.example.phonequery.data

/**
 * 常用号码处理正则常量。
 * 复用同一实例，避免在来电热路径 / 高频查询中反复编译正则。
 */

/** 提取纯数字：删除所有非数字字符（如 `+`、空格、`-`、括号）。 */
val DIGITS_ONLY_REGEX = Regex("[^0-9]")

/** 删除所有非数字字符（含字母、符号）。 */
val NON_DIGIT_REGEX = Regex("\\D")

/** 清理号码中的空白 / 括号 / 连字符（保留 `+`）。 */
val CLEAN_SPACE_REGEX = Regex("[\\s()-]")

/** 备份导出用：仅保留数字与 `!`。 */
val DIGITS_BANG_REGEX = Regex("[^0-9!]")
