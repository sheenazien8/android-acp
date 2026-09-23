package com.lakasir.acp.ui.chat

sealed interface TextSegment {
    data class Prose(val text: String) : TextSegment
    data class Code(val language: String?, val text: String) : TextSegment
}

object TextSegments {
    private const val FENCE = "```"

    fun split(text: String): List<TextSegment> {
        val segments = mutableListOf<TextSegment>()
        var rest = text
        while (true) {
            val start = rest.indexOf(FENCE)
            if (start < 0) {
                if (rest.isNotBlank()) segments += TextSegment.Prose(rest.trim('\n'))
                break
            }
            val before = rest.substring(0, start)
            if (before.isNotBlank()) segments += TextSegment.Prose(before.trim('\n'))
            val afterFence = rest.substring(start + FENCE.length)
            val lineEnd = afterFence.indexOf('\n').let { if (it < 0) afterFence.length else it }
            val language = afterFence.substring(0, lineEnd).trim().ifEmpty { null }
            val body = afterFence.substring(minOf(lineEnd + 1, afterFence.length))
            val end = body.indexOf(FENCE)
            if (end < 0) {
                segments += TextSegment.Code(language, body.trimEnd('\n'))
                break
            }
            segments += TextSegment.Code(language, body.substring(0, end).trimEnd('\n'))
            rest = body.substring(end + FENCE.length)
        }
        return segments
    }
}
