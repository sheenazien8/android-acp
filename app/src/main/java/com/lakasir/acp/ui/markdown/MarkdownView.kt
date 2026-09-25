package com.lakasir.acp.ui.markdown

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lakasir.acp.ui.chat.components.CodeBox
import com.lakasir.acp.ui.components.leftBorder
import com.lakasir.acp.ui.theme.AcpTheme

@Composable
fun MarkdownView(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    val styles = rememberInlineStyles()
    SelectionContainer(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(blocks) { _, block -> MarkdownBlock(block, styles) }
        }
    }
}

@Composable
fun rememberInlineStyles(): InlineStyles {
    val colors = MaterialTheme.colorScheme
    val codeStyle = AcpTheme.code.codeSmall
    val accent = AcpTheme.extended.accentText
    return remember(colors, codeStyle, accent) {
        InlineStyles(
            code = SpanStyle(
                fontFamily = codeStyle.fontFamily,
                fontSize = codeStyle.fontSize,
                background = colors.surfaceContainerHigh,
            ),
            link = TextLinkStyles(style = SpanStyle(color = accent, textDecoration = TextDecoration.Underline)),
            muted = SpanStyle(color = colors.onSurfaceVariant),
        )
    }
}

@Composable
fun MarkdownBlock(block: MdBlock, styles: InlineStyles) {
    val body = MaterialTheme.typography.bodyMedium
    val onSurface = MaterialTheme.colorScheme.onSurface
    when (block) {
        is MdBlock.Heading -> Column(Modifier.padding(top = if (block.level <= 2) 8.dp else 4.dp)) {
            Text(MarkdownInline.render(block.text, styles), style = headingStyle(block.level), color = onSurface)
            if (block.level <= 2) {
                HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outline)
            }
        }
        is MdBlock.Paragraph -> Text(MarkdownInline.render(block.text, styles), style = body, color = onSurface)
        is MdBlock.ListItem -> Row(Modifier.padding(start = (block.depth * 16).dp)) {
            if (block.checked != null) {
                Icon(
                    if (block.checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    contentDescription = if (block.checked) "Done" else "Not done",
                    modifier = Modifier.padding(top = 2.dp, end = 6.dp).size(16.dp),
                    tint = if (block.checked) AcpTheme.extended.accentText else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    block.marker,
                    modifier = Modifier.width(22.dp),
                    style = body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(MarkdownInline.render(block.text, styles), style = body, color = onSurface)
        }
        is MdBlock.Quote -> Column(
            Modifier.fillMaxWidth().leftBorder(MaterialTheme.colorScheme.outline).padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            block.text.split("\n\n").forEach {
                Text(
                    MarkdownInline.render(it.replace('\n', ' '), styles),
                    style = body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is MdBlock.Code -> CodeBox(block.text, caption = block.language)
        is MdBlock.Table -> MarkdownTable(block, styles)
        MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle {
    val typography = MaterialTheme.typography
    return when (level) {
        1 -> typography.headlineSmall
        2 -> typography.titleLarge
        3 -> typography.titleMedium
        else -> typography.titleSmall
    }.copy(fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MarkdownTable(table: MdBlock.Table, styles: InlineStyles) {
    val columns = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    val widths = (0 until columns).map { column ->
        val longest = (listOf(table.header) + table.rows).maxOf { it.getOrNull(column)?.length ?: 0 }
        (longest * 8 + 20).coerceIn(64, 240).dp
    }
    val outline = MaterialTheme.colorScheme.outline
    Column(
        Modifier
            .horizontalScroll(rememberScrollState())
            .border(1.dp, outline, MaterialTheme.shapes.small),
    ) {
        (listOf(table.header) + table.rows).forEachIndexed { index, row ->
            Row(
                Modifier.then(
                    if (index == 0) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier
                ),
                verticalAlignment = Alignment.Top,
            ) {
                widths.forEachIndexed { column, width ->
                    Text(
                        MarkdownInline.render(row.getOrNull(column).orEmpty(), styles),
                        modifier = Modifier.width(width).padding(horizontal = 8.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall.let {
                            if (index == 0) it.copy(fontWeight = FontWeight.SemiBold) else it
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (index < table.rows.size) HorizontalDivider(color = outline)
        }
    }
}

private const val PREVIEW_MARKDOWN = """---
name: demo
---
# Workspace notes

Some **bold**, *italic*, `inline code` and a [link](https://example.com).

- [x] Browse files
- [ ] Git support
  - nested item
1. First
2. Second

> Quoted text

| Plan | Status |
|------|--------|
| 14 | done |

```kotlin
fun main() = println("hi")
```
"""

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 900)
@Composable
private fun MarkdownDarkPreview() {
    AcpTheme(darkTheme = true) { MarkdownView(PREVIEW_MARKDOWN) }
}
