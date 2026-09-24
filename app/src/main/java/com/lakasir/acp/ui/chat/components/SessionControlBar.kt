package com.lakasir.acp.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lakasir.acp.acp.ConfigOption
import com.lakasir.acp.acp.SessionControlState

@Composable
fun SessionControlBar(
    controls: SessionControlState,
    isBusy: Boolean,
    onOpenModelPicker: () -> Unit,
    onOpenThinkingPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = controls.model
    val thinking = controls.thinking
    if (model == null && thinking == null) return
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        model?.let { ConfigChip(option = it, enabled = !isBusy, onClick = onOpenModelPicker) }
        thinking?.let { ConfigChip(option = it, enabled = !isBusy, onClick = onOpenThinkingPicker) }
    }
}

@Composable
private fun ConfigChip(
    option: ConfigOption,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentName = option.choices.firstOrNull { it.value == option.currentValue }?.name ?: option.name
    AssistChip(
        onClick = onClick,
        label = { Text(currentName, maxLines = 1, style = MaterialTheme.typography.labelLarge) },
        modifier = modifier,
        enabled = enabled,
        trailingIcon = {
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
