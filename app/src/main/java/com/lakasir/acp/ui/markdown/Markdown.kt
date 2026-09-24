package com.lakasir.acp.ui.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItem(val depth: Int, val marker: String, val text: String, val checked: Boolean? = null) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val language: String?, val text: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

object MarkdownParser {
    private val HEADING = Regex("""^ {0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val RULE = Regex("""^ {0,3}([-*_])(\s*\1){2,}\s*$""")
    private val FENCE = Regex("""^ {0,3}(```+|~~~+)\s*([^`\s]*).*$""")
    private val LIST = Regex("""^(\s*)([-*+]|\d{1,9}[.)])\s+(.*)$""")
    private val TASK = Regex("""^\[([ xX])]\s+(.*)$""")
    private val TABLE_SEPARATOR = Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$""")

    fun parse(text: String): List<MdBlock> {
        val lines = text.replace("\r\n", "\n").split('\n')
        val blocks = mutableListOf<MdBlock>()
        val paragraph = mutableListOf<String>()
        var i = frontMatter(lines, blocks)

        fun flush() {
            if (paragraph.isNotEmpty()) blocks += MdBlock.Paragraph(paragraph.joinToString(" ") { it.trim() })
            paragraph.clear()
        }

        while (i < lines.size) {
            val line = lines[i]
            val fence = FENCE.matchEntire(line)
            when {
                fence != null -> {
                    flush()
                    val marker = fence.groupValues[1]
                    val body = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith(marker)) body += lines[i++]
                    blocks += MdBlock.Code(fence.groupValues[2].ifEmpty { null }, body.joinToString("\n"))
                }
                line.isBlank() -> flush()
                HEADING.matches(line) -> {
                    flush()
                    val match = HEADING.matchEntire(line)!!
                    blocks += MdBlock.Heading(match.groupValues[1].length, match.groupValues[2])
                }
                RULE.matches(line) -> {
                    flush()
                    blocks += MdBlock.Rule
                }
                line.trimStart().startsWith(">") -> {
                    flush()
                    val quote = mutableListOf<String>()
                    while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                        quote += lines[i].trimStart().removePrefix(">").removePrefix(" ")
                        i++
                    }
                    blocks += MdBlock.Quote(quote.joinToString("\n").trim())
                    continue
                }
                LIST.matches(line) -> {
                    flush()
                    blocks += listItem(LIST.matchEntire(line)!!)
                }
                '|' in line && i + 1 < lines.size && TABLE_SEPARATOR.matches(lines[i + 1]) && '-' in lines[i + 1] -> {
                    flush()
                    val header = cells(line)
                    val rows = mutableListOf<List<String>>()
                    i += 2
                    while (i < lines.size && '|' in lines[i] && lines[i].isNotBlank()) rows += cells(lines[i++])
                    blocks += MdBlock.Table(header, rows)
                    continue
                }
                paragraph.isEmpty() && blocks.lastOrNull() is MdBlock.ListItem && line.startsWith("  ") -> {
                    val last = blocks.removeAt(blocks.lastIndex) as MdBlock.ListItem
                    blocks += last.copy(text = last.text + " " + line.trim())
                }
                else -> paragraph += line
            }
            i++
        }
        flush()
        return blocks
    }

    private fun frontMatter(lines: List<String>, blocks: MutableList<MdBlock>): Int {
        if (lines.firstOrNull()?.trim() != "---") return 0
        val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" } ?: return 0
        blocks += MdBlock.Code("yaml", lines.subList(1, end).joinToString("\n"))
        return end + 1
    }

    private fun listItem(match: MatchResult): MdBlock.ListItem {
        val (indent, marker, rest) = match.destructured
        val depth = indent.replace("\t", "    ").length / 2
        val task = TASK.matchEntire(rest)
        val shown = if (marker.first().isDigit()) marker.dropLast(1) + "." else "•"
        return if (task != null) {
            MdBlock.ListItem(depth, shown, task.groupValues[2], checked = task.groupValues[1] != " ")
        } else {
            MdBlock.ListItem(depth, shown, rest)
        }
    }

    private fun cells(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }
}

data class InlineStyles(
    val code: SpanStyle,
    val link: TextLinkStyles,
    val muted: SpanStyle,
)

object MarkdownInline {
    private val BOLD = SpanStyle(fontWeight = FontWeight.SemiBold)
    private val ITALIC = SpanStyle(fontStyle = FontStyle.Italic)
    private val STRIKE = SpanStyle(textDecoration = TextDecoration.LineThrough)

    fun render(text: String, styles: InlineStyles): AnnotatedString = buildAnnotatedString { append(text, styles) }

    private fun AnnotatedString.Builder.append(text: String, styles: InlineStyles) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val next = text.getOrNull(i + 1)
            when {
                c == '\\' && next != null && !next.isLetterOrDigit() -> {
                    append(next)
                    i += 2
                }
                c == '`' -> {
                    val ticks = text.substring(i).takeWhile { it == '`' }
                    val end = text.indexOf(ticks, i + ticks.length)
                    if (end < 0) {
                        append(ticks)
                        i += ticks.length
                    } else {
                        withStyle(styles.code) { append(text.substring(i + ticks.length, end).trim()) }
                        i = end + ticks.length
                    }
                }
                (c == '*' || c == '_') && next == c -> i = delimited(text, i, "$c$c", BOLD, styles)
                c == '~' && next == '~' -> i = delimited(text, i, "~~", STRIKE, styles)
                c == '*' || (c == '_' && !text.getOrNull(i - 1).isWordChar()) -> i = delimited(text, i, "$c", ITALIC, styles)
                c == '!' && next == '[' -> i = link(text, i + 1, styles, image = true) ?: (i + 1).also { append(c) }
                c == '[' -> i = link(text, i, styles, image = false) ?: (i + 1).also { append(c) }
                c == '<' && text.startsWith("http", i + 1) -> {
                    val end = text.indexOf('>', i)
                    if (end < 0) {
                        append(c)
                        i++
                    } else {
                        val url = text.substring(i + 1, end)
                        withLink(LinkAnnotation.Url(url, styles.link)) { append(url) }
                        i = end + 1
                    }
                }
                else -> {
                    append(c)
                    i++
                }
            }
        }
    }

    private fun AnnotatedString.Builder.delimited(
        text: String,
        start: Int,
        delimiter: String,
        style: SpanStyle,
        styles: InlineStyles,
    ): Int {
        val contentStart = start + delimiter.length
        var end = text.indexOf(delimiter, contentStart)
        if (delimiter == "_") {
            while (end >= 0 && text.getOrNull(end + 1).isWordChar()) end = text.indexOf(delimiter, end + 1)
        }
        if (end <= contentStart || text[contentStart].isWhitespace()) {
            append(delimiter)
            return contentStart
        }
        withStyle(style) { append(text.substring(contentStart, end), styles) }
        return end + delimiter.length
    }

    private fun AnnotatedString.Builder.link(text: String, start: Int, styles: InlineStyles, image: Boolean): Int? {
        val labelEnd = text.indexOf("](", start)
        if (labelEnd < 0) return null
        val urlEnd = text.indexOf(')', labelEnd + 2)
        if (urlEnd < 0) return null
        val label = text.substring(start + 1, labelEnd)
        val url = text.substring(labelEnd + 2, urlEnd).trim().substringBefore(' ')
        if (image) {
            withStyle(styles.muted) { append("[image: ${label.ifBlank { url }}]") }
        } else if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) {
            withLink(LinkAnnotation.Url(url, styles.link)) { append(label, styles) }
        } else {
            withStyle(styles.link.style ?: SpanStyle()) { append(label, styles) }
        }
        return urlEnd + 1
    }

    private fun Char?.isWordChar(): Boolean = this != null && isLetterOrDigit()
}
