package com.lakasir.acp.ui.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lakasir.acp.acp.ToolCallStatus
import com.lakasir.acp.data.model.PermissionRecord
import com.lakasir.acp.data.model.PlanItem
import com.lakasir.acp.ui.theme.AcpTheme

@Composable
fun ThoughtBlock(id: Long, text: String, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    LogRow(
        borderColor = MaterialTheme.colorScheme.outline,
        label = if (expanded) "thinking" else "thinking · tap to expand",
        modifier = modifier
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse" else "Expand") { expanded = !expanded }
            .animateContentSize(),
    ) {
        Text(
            text.trim(),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PlanBlock(items: List<PlanItem>, modifier: Modifier = Modifier) {
    LogRow(borderColor = MaterialTheme.colorScheme.outline, label = "plan", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEach { item ->
                val (marker, color) = when (item.status) {
                    ToolCallStatus.COMPLETED -> "✓" to MaterialTheme.colorScheme.primary
                    ToolCallStatus.IN_PROGRESS -> "›" to AcpTheme.extended.warning
                    else -> "·" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row {
                    Text(marker, modifier = Modifier.width(16.dp), style = AcpTheme.code.codeSmall, color = color)
                    Text(
                        item.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.status == ToolCallStatus.COMPLETED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRecordRow(record: PermissionRecord, modifier: Modifier = Modifier) {
    val answered = record.choice != null
    LogRow(
        borderColor = if (answered) MaterialTheme.colorScheme.outline else AcpTheme.extended.warning,
        label = "permission",
        modifier = modifier,
    ) {
        Text(record.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            record.choice?.let { "Answered: $it" } ?: "Waiting for your answer",
            style = MaterialTheme.typography.labelSmall,
            color = if (answered) MaterialTheme.colorScheme.onSurfaceVariant else AcpTheme.extended.warning,
        )
    }
}

@Composable
fun ErrorRow(text: String, modifier: Modifier = Modifier) {
    LogRow(borderColor = AcpTheme.extended.roleError, label = "error", modifier = modifier) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun SystemNoteRow(text: String, modifier: Modifier = Modifier) {
    LogRow(borderColor = AcpTheme.extended.roleTool, modifier = modifier) {
        Text(text, style = AcpTheme.code.codeSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
