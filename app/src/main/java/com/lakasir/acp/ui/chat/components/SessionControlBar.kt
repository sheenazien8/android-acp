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
    modifier: Modifier = Modifier,
) {
    val model = controls.model ?: return
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModelChip(model = model, enabled = !isBusy, onClick = onOpenModelPicker)
    }
}

@Composable
private fun ModelChip(
    model: ConfigOption,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentName = model.choices.firstOrNull { it.value == model.currentValue }?.name ?: model.name
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
