package com.lakasir.acp.ui.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lakasir.acp.acp.UsageInfo
import com.lakasir.acp.ui.chat.TokenFormat

@Composable
fun ContextUsageIndicator(usage: UsageInfo, modifier: Modifier = Modifier) {
    val fraction = if (usage.size > 0) (usage.used.toFloat() / usage.size.toFloat()).coerceIn(0f, 1f) else 0f
    val high = fraction >= 0.8f
    val color = if (high) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    val track = if (high) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
    val label = when {
        usage.size > 0 -> "${TokenFormat.compact(usage.used)} / ${TokenFormat.compact(usage.size)}"
        usage.turnTotal != null -> "${TokenFormat.compact(usage.turnTotal)} tokens"
        else -> "Context"
    }
    Column(
        modifier = modifier
            .widthIn(min = 96.dp, max = 150.dp)
            .semantics {
                contentDescription = "Context usage $label"
            },
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(2.dp).padding(top = 2.dp),
            color = color,
            trackColor = track,
        )
    }
}
