package com.lakasir.acp.ui.markdown

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.lakasir.acp.ui.workspace.FileViewerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    private val styles = InlineStyles(
        code = SpanStyle(background = androidx.compose.ui.graphics.Color.Gray),
        link = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)),
        muted = SpanStyle(color = androidx.compose.ui.graphics.Color.DarkGray),
    )

    @Test
    fun `parses headings paragraphs rules and code`() {
        val blocks = MarkdownParser.parse("# Title #\n\nFirst line\nsecond line\n\n---\n```kotlin\nval a = 1\n```\n")
        assertEquals(
            listOf(
                MdBlock.Heading(1, "Title"),
                MdBlock.Paragraph("First line second line"),
                MdBlock.Rule,
                MdBlock.Code("kotlin", "val a = 1"),
            ),
            blocks,
        )
    }

    @Test
    fun `parses lists tasks and nesting`() {
        val blocks = MarkdownParser.parse("- one\n  continued\n  * nested\n3. third\n- [x] done\n- [ ] todo")
        assertEquals(
            listOf(
                MdBlock.ListItem(0, "•", "one continued"),
                MdBlock.ListItem(1, "•", "nested"),
                MdBlock.ListItem(0, "3.", "third"),
                MdBlock.ListItem(0, "•", "done", checked = true),
                MdBlock.ListItem(0, "•", "todo", checked = false),
            ),
            blocks,
        )
    }

    @Test
    fun `parses quotes tables and front matter`() {
        val blocks = MarkdownParser.parse("---\nname: x\n---\n> a\n> b\n\n| A | B |\n|---|:-:|\n| 1 | 2 |\n| 3 |\n")
        assertEquals(
            listOf(
                MdBlock.Code("yaml", "name: x"),
                MdBlock.Quote("a\nb"),
                MdBlock.Table(listOf("A", "B"), listOf(listOf("1", "2"), listOf("3"))),
            ),
            blocks,
        )
    }

    @Test
    fun `unclosed fence runs to the end`() {
        assertEquals(listOf(MdBlock.Code(null, "a\nb")), MarkdownParser.parse("```\na\nb"))
    }

    @Test
    fun `inline styles are applied`() {
        val text = MarkdownInline.render("**bold** *it* ~~gone~~ `code` plain", styles)
        assertEquals("bold it gone code plain", text.text)
        val spans = text.spanStyles.associate { text.text.substring(it.start, it.end) to it.item }
        assertEquals(FontWeight.SemiBold, spans.getValue("bold").fontWeight)
        assertEquals(FontStyle.Italic, spans.getValue("it").fontStyle)
        assertEquals(TextDecoration.LineThrough, spans.getValue("gone").textDecoration)
        assertEquals(styles.code, spans.getValue("code"))
    }

    @Test
    fun `links become url annotations and relative links stay text`() {
        val text = MarkdownInline.render("see [docs](https://a.dev/x \"t\") and [local](./b.md) ![pic](i.png)", styles)
        assertEquals("see docs and local [image: pic]", text.text)
        val link = text.getLinkAnnotations(0, text.length).single()
        assertEquals("https://a.dev/x", (link.item as LinkAnnotation.Url).url)
        assertEquals("docs", text.text.substring(link.start, link.end))
    }

    @Test
    fun `intraword underscores and stray markers are literal`() {
        assertEquals("snake_case_name", MarkdownInline.render("snake_case_name", styles).text)
        assertEquals("2 * 3 = 6", MarkdownInline.render("2 * 3 = 6", styles).text)
        assertEquals("a `b", MarkdownInline.render("a `b", styles).text)
        assertEquals("*literal*", MarkdownInline.render("\\*literal\\*", styles).text)
    }

    @Test
    fun `markdown files are detected by extension`() {
        assertTrue(FileViewerState.isMarkdown("docs/README.md"))
        assertTrue(FileViewerState.isMarkdown("notes.MARKDOWN"))
        assertFalse(FileViewerState.isMarkdown("build.gradle.kts"))
        assertFalse(FileViewerState.isMarkdown("md"))
    }
}
