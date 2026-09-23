package com.lakasir.acp.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSegmentsTest {

    @Test
    fun `plain text is one prose segment`() {
        assertEquals(listOf(TextSegment.Prose("hello")), TextSegments.split("hello"))
    }

    @Test
    fun `fenced code is split out with language`() {
        assertEquals(
            listOf(
                TextSegment.Prose("Run:"),
                TextSegment.Code("bash", "ls -la"),
                TextSegment.Prose("done"),
            ),
            TextSegments.split("Run:\n```bash\nls -la\n```\ndone"),
        )
    }

    @Test
    fun `unterminated fence while streaming is still code`() {
        assertEquals(
            listOf(TextSegment.Prose("x"), TextSegment.Code(null, "partial")),
            TextSegments.split("x\n```\npartial"),
        )
    }
}
