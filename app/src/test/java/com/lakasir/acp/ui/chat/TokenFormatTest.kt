package com.lakasir.acp.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenFormatTest {

    @Test
    fun `formats small numbers as raw`() {
        assertEquals("0", TokenFormat.compact(0))
        assertEquals("999", TokenFormat.compact(999))
    }

    @Test
    fun `formats thousands with k`() {
        assertEquals("1k", TokenFormat.compact(1_000))
        assertEquals("1.5k", TokenFormat.compact(1_500))
        assertEquals("999.9k", TokenFormat.compact(999_900))
    }

    @Test
    fun `formats millions with M`() {
        assertEquals("1M", TokenFormat.compact(1_000_000))
        assertEquals("2.5M", TokenFormat.compact(2_500_000))
    }
}
