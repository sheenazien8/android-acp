package com.lakasir.acp.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lakasir.acp.data.model.DiffState
import com.lakasir.acp.ui.chat.DiffLineType
import com.lakasir.acp.ui.chat.LineDiff
import com.lakasir.acp.ui.theme.AcpTheme

private const val MAX_DIFF_LINES = 300

@Composable
fun DiffBlock(diff: DiffState, modifier: Modifier = Modifier) {
    val lines = remember(diff) { LineDiff.compute(diff.oldText, diff.newText) }
    val added = lines.count { it.type == DiffLineType.ADDED }
    val removed = lines.count { it.type == DiffLineType.REMOVED }
    val extended = AcpTheme.extended
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                diff.path,
                modifier = Modifier.weight(1f),
                style = AcpTheme.code.codeSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("+$added", style = AcpTheme.code.codeSmall, color = extended.diffAdded)
            Text(" −$removed", style = AcpTheme.code.codeSmall, color = extended.diffRemoved)
        }
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp)) {
            lines.take(MAX_DIFF_LINES).forEach { line ->
                val (prefix, background, color) = when (line.type) {
                    DiffLineType.ADDED -> Triple("+", extended.diffAddedBackground, extended.diffAdded)
                    DiffLineType.REMOVED -> Triple("-", extended.diffRemovedBackground, extended.diffRemoved)
                    DiffLineType.CONTEXT -> Triple(" ", Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.widthIn(min = 2000.dp).background(background).padding(horizontal = 10.dp)) {
                    Text(prefix, modifier = Modifier.width(14.dp), style = AcpTheme.code.codeSmall, color = color)
                    Text(line.text, style = AcpTheme.code.codeSmall, color = MaterialTheme.colorScheme.onSurface, softWrap = false)
                }
            }
            if (lines.size > MAX_DIFF_LINES) {
                Text(
                    "${lines.size - MAX_DIFF_LINES} more lines",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
