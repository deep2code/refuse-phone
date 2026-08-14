package com.example.phonequery.data

import org.junit.Assert.*
import org.junit.Test

class SpamPrefixDatabaseTest {

    @Test
    fun `match returns virtual operator hint for 170 prefix`() {
        val hint = SpamPrefixDatabase.match("17012345678")
        assertNotNull(hint)
        assertEquals(SpamPrefixDatabase.Level.VIRTUAL_OPERATOR, hint!!.level)
    }

    @Test
    fun `match returns high risk hint for 95 prefix`() {
        val hint = SpamPrefixDatabase.match("95012345678")
        assertNotNull(hint)
        assertEquals(SpamPrefixDatabase.Level.HIGH_RISK, hint!!.level)
    }

    @Test
    fun `match returns null for normal mobile number`() {
        assertNull(SpamPrefixDatabase.match("13800138000"))
    }

    @Test
    fun `match strips non digits before matching`() {
        val hint = SpamPrefixDatabase.match("+86 170 1234 5678")
        assertNotNull(hint)
        assertEquals(SpamPrefixDatabase.Level.VIRTUAL_OPERATOR, hint!!.level)
    }

    @Test
    fun `match returns null for too short input`() {
        assertNull(SpamPrefixDatabase.match("12"))
    }

    @Test
    fun `mainstream segment 133 is not flagged`() {
        assertNull(SpamPrefixDatabase.match("13312345678"))
    }

    @Test
    fun `match detects high risk 95 prefix even with plus86 country code`() {
        val hint = SpamPrefixDatabase.match("+86 95012345678")
        assertNotNull(hint)
        assertEquals(SpamPrefixDatabase.Level.HIGH_RISK, hint!!.level)
    }
}
