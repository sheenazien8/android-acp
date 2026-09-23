package com.lakasir.acp.ui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lakasir.acp.ui.theme.AcpTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFormSheet(
    form: ProfileForm,
    onChange: ((ProfileForm) -> ProfileForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (form.isNew) "New connection" else "Edit connection", style = MaterialTheme.typography.titleMedium)

            FormField(
                label = "Name",
                value = form.name,
                placeholder = "Optional, defaults to host",
                onValueChange = { v -> onChange { it.copy(name = v) } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField(
                    label = "Host",
                    value = form.host,
                    placeholder = "192.168.1.10",
                    error = form.hostError,
                    keyboardType = KeyboardType.Uri,
                    mono = true,
                    modifier = Modifier.weight(2f),
                    onValueChange = { v -> onChange { it.copy(host = v, hostError = null) } },
                )
                FormField(
                    label = "Port",
                    value = form.port,
                    error = form.portError,
                    keyboardType = KeyboardType.Number,
                    mono = true,
                    modifier = Modifier.weight(1f),
                    onValueChange = { v -> onChange { it.copy(port = v.filter(Char::isDigit), portError = null) } },
                )
            }
            FormField(
                label = "Working directory",
                value = form.cwd,
                placeholder = "/home/you/project",
                error = form.cwdError,
                supporting = "Absolute path on the bridge machine, used for new sessions",
                keyboardType = KeyboardType.Uri,
                mono = true,
                imeAction = ImeAction.Done,
                onValueChange = { v -> onChange { it.copy(cwd = v, cwdError = null) } },
            )

            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = onSave, modifier = Modifier.padding(start = 8.dp)) { Text("Save") }
            }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    mono: Boolean = false,
) {
    val textStyle: TextStyle = if (mono) AcpTheme.code.codeMedium else MaterialTheme.typography.bodyLarge
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, style = textStyle) } },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        singleLine = true,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
        shape = MaterialTheme.shapes.small,
    )
}
