package com.example.phonequery.data

import com.example.phonequery.db.BlocklistEntity
import org.junit.Assert.*
import org.junit.Test

class BlocklistEvaluatorTest {

    @Test
    fun `regex rule matches whole number`() {
        val rule = BlocklistEntity(
            number = "^170\\d{8}$", isBlock = true, type = BlocklistEntity.TYPE_REGEX, label = "t"
        )
        assertTrue(BlocklistEvaluator.matchesRegexRule(rule, "17012345678"))
        assertFalse(BlocklistEvaluator.matchesRegexRule(rule, "13912345678"))
        assertFalse(BlocklistEvaluator.matchesRegexRule(rule, "1701234567"))
    }

    @Test
    fun `regex rule invalid pattern returns false safely`() {
        val rule = BlocklistEntity(
            number = "[", isBlock = true, type = BlocklistEntity.TYPE_REGEX, label = "t"
        )
        assertFalse(BlocklistEvaluator.matchesRegexRule(rule, "123"))
    }

    @Test
    fun `attr rule forward matches city contains`() {
        val rule = BlocklistEntity(
            number = "西安", isBlock = true, type = BlocklistEntity.TYPE_ATTR, label = "t"
        )
        assertTrue(BlocklistEvaluator.matchesAttrRule(rule, "陕西 西安"))
        assertFalse(BlocklistEvaluator.matchesAttrRule(rule, "上海"))
    }

    @Test
    fun `attr rule reverse blocks all except region`() {
        val rule = BlocklistEntity(
            number = "!西安", isBlock = true, type = BlocklistEntity.TYPE_ATTR, label = "t"
        )
        assertTrue(BlocklistEvaluator.matchesAttrRule(rule, "上海")) // not 西安 -> block
        assertFalse(BlocklistEvaluator.matchesAttrRule(rule, "西安")) // is 西安 -> allow
    }

    @Test
    fun `attr rule returns false when city blank`() {
        val rule = BlocklistEntity(
            number = "西安", isBlock = true, type = BlocklistEntity.TYPE_ATTR, label = "t"
        )
        assertFalse(BlocklistEvaluator.matchesAttrRule(rule, null))
        assertFalse(BlocklistEvaluator.matchesAttrRule(rule, ""))
    }
}
