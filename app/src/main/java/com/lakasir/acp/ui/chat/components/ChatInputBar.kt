package com.lakasir.acp.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    isBusy: Boolean,
    hint: String?,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    attachments: List<String> = emptyList(),
    onRemoveAttachment: (String) -> Unit = {},
    commandHint: String? = null,
    focusRequester: FocusRequester? = null,
) {
    Column(modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
        val fieldFocusRequester = focusRequester ?: remember { FocusRequester() }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        if (hint != null) {
            Text(
                hint,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (attachments.isNotEmpty()) {
            AttachmentInputChips(attachments, onRemoveAttachment, Modifier.padding(top = 6.dp))
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f).focusRequester(fieldFocusRequester),
                enabled = enabled,
                placeholder = { Text("Message the agent") },
                supportingText = commandHint?.let { { Text(it) } },
                maxLines = 6,
                shape = MaterialTheme.shapes.small,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            if (isBusy) {
                OutlinedIconButton(
                    onClick = onCancel,
                    modifier = Modifier.padding(bottom = 4.dp),
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "Cancel")
                }
            } else {
                FilledIconButton(
                    onClick = onSend,
                    modifier = Modifier.padding(bottom = 4.dp),
                    enabled = enabled && (text.isNotBlank() || attachments.isNotEmpty()),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
