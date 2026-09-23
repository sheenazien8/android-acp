package com.lakasir.acp.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class LineDiffTest {

    private fun render(lines: List<DiffLine>) = lines.map {
        when (it.type) {
            DiffLineType.ADDED -> "+${it.text}"
            DiffLineType.REMOVED -> "-${it.text}"
            DiffLineType.CONTEXT -> " ${it.text}"
        }
    }

    @Test
    fun `new file is all additions`() {
        assertEquals(listOf("+a", "+b"), render(LineDiff.compute(null, "a\nb")))
    }

    @Test
    fun `changed line shows removal then addition`() {
        assertEquals(listOf(" a", "-b", "+B", " c"), render(LineDiff.compute("a\nb\nc", "a\nB\nc")))
    }

    @Test
    fun `insertions and deletions keep context`() {
        assertEquals(listOf(" a", "+x", " b", "-c"), render(LineDiff.compute("a\nb\nc", "a\nx\nb")))
    }

    @Test
    fun `identical text is all context`() {
        assertEquals(listOf(" a", " b"), render(LineDiff.compute("a\nb", "a\nb")))
    }
}
