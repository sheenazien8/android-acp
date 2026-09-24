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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lakasir.acp.BuildConfig
import com.lakasir.acp.data.local.ConnectionScheme
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
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(ConnectionScheme.WS to "ws://  LAN", ConnectionScheme.WSS to "wss://  TLS").forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = form.scheme == value,
                        onClick = { onChange { it.copy(scheme = value) } },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(label, style = AcpTheme.code.codeMedium) }
                }
            }
            if (!form.isSecure && !BuildConfig.DEBUG) {
                Warning("This build only allows encrypted wss:// connections.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField(
                    label = "Host",
                    value = form.host,
                    placeholder = if (form.isSecure) "bridge.example.com" else "192.168.1.10",
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
                label = "Path",
                value = form.path,
                placeholder = "/acp",
                error = form.pathError,
                supporting = "WebSocket path on the bridge",
                keyboardType = KeyboardType.Uri,
                mono = true,
                onValueChange = { v -> onChange { it.copy(path = v, pathError = null) } },
            )
            FormField(
                label = "Token",
                value = form.token,
                placeholder = if (form.hasSavedToken) "Saved. Leave empty to keep it" else "Optional",
                supporting = "Sent as a Bearer token. Needed when the bridge runs with --token",
                keyboardType = KeyboardType.Password,
                mono = true,
                visualTransformation = PasswordVisualTransformation(),
                trailingIcon = if (form.hasSavedToken) {
                    {
                        IconButton(onClick = { onChange { it.copy(savedToken = null, token = "") } }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove saved token")
                        }
                    }
                } else {
                    null
                },
                onValueChange = { v -> onChange { it.copy(token = v) } },
            )
            if (form.isSecure) {
                Row(
                    Modifier.fillMaxWidth().semantics {
                        stateDescription = if (form.allowInsecureTls) "On" else "Off"
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Allow self-signed certificate", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Skip certificate checks for this connection only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = form.allowInsecureTls,
                        onCheckedChange = { checked -> onChange { it.copy(allowInsecureTls = checked) } },
                    )
                }
                if (form.allowInsecureTls) {
                    Warning("The certificate isn't verified, so anyone between you and the bridge could read the token and your files.")
                }
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
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
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
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
        shape = MaterialTheme.shapes.small,
    )
}

@Composable
private fun Warning(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Outlined.Warning,
            contentDescription = "Warning",
            modifier = Modifier.padding(end = 8.dp),
            tint = AcpTheme.extended.warning,
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = AcpTheme.extended.warning)
    }
}
