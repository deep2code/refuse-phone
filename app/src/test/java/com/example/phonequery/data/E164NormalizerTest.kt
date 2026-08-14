package com.example.phonequery.data

import org.junit.Assert.*
import org.junit.Test

class E164NormalizerTest {

    @Test
    fun `normalize 11 digit mobile with leading 1`() {
        assertEquals("+8613800138000", E164Normalizer.normalize("13800138000"))
    }

    @Test
    fun `normalize landline with leading 0`() {
        assertEquals("+861012345678", E164Normalizer.normalize("01012345678"))
    }

    @Test
    fun `normalize already has plus`() {
        assertEquals("+8613800138000", E164Normalizer.normalize("+8613800138000"))
    }

    @Test
    fun `normalize strips spaces dashes parens`() {
        assertEquals("+8613800138000", E164Normalizer.normalize("138-0013-8000"))
    }

    @Test
    fun `normalize fullwidth plus`() {
        assertEquals("+8613800138000", E164Normalizer.normalize("＋8613800138000"))
    }

    @Test
    fun `normalize 86 prefix 13 digits`() {
        assertEquals("+8613800138000", E164Normalizer.normalize("8613800138000"))
    }

    @Test
    fun `md5Hex is deterministic lowercase hex of length 32`() {
        val h = E164Normalizer.md5Hex("+8613800138000")
        assertEquals(32, h.length)
        assertEquals(h, E164Normalizer.md5Hex("+8613800138000"))
        assertTrue(h.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
