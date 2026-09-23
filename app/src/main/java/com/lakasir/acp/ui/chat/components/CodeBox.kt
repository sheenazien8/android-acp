package com.lakasir.acp.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lakasir.acp.ui.theme.AcpTheme

@Composable
fun CodeBox(text: String, modifier: Modifier = Modifier, caption: String? = null, maxLines: Int = Int.MAX_VALUE) {
    val lines = text.lines()
    val shown = if (lines.size > maxLines) lines.take(maxLines).joinToString("\n") else text
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
    ) {
        if (caption != null) {
            Text(
                caption,
                modifier = Modifier.padding(start = 10.dp, top = 6.dp, end = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(10.dp)) {
            SelectionContainer {
                Text(shown, style = AcpTheme.code.codeSmall, color = MaterialTheme.colorScheme.onSurface, softWrap = false)
            }
        }
        if (lines.size > maxLines) {
            Text(
                "${lines.size - maxLines} more lines",
                modifier = Modifier.padding(start = 10.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
