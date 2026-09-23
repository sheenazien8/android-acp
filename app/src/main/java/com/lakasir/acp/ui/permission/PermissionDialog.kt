package com.lakasir.acp.ui.permission

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lakasir.acp.acp.AcpJson
import com.lakasir.acp.acp.AcpEvent
import com.lakasir.acp.acp.PermissionOption
import com.lakasir.acp.acp.ToolCallInfo
import com.lakasir.acp.data.repository.PendingPermission
import com.lakasir.acp.ui.AppViewModelProvider
import com.lakasir.acp.ui.chat.components.CodeBox
import com.lakasir.acp.ui.theme.AcpTheme
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun PermissionHost(viewModel: PermissionViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val current by viewModel.current.collectAsStateWithLifecycle()
    current?.let { pending ->
        PermissionDialog(
            pending = pending,
            onOption = { viewModel.answer(pending, it) },
            onCancel = { viewModel.answer(pending, null) },
        )
    }
}

@Composable
fun PermissionDialog(pending: PendingPermission, onOption: (String) -> Unit, onCancel: () -> Unit) {
    val toolCall = pending.request.toolCall
    val title = toolCall.title ?: toolCall.kind ?: "Permission requested"
    val titleIsLong = title.length > TITLE_MAX_CHARS || title.contains('\n')
    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Outlined.Security, contentDescription = null) },
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    listOfNotNull(pending.sessionTitle, toolCall.kind).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (titleIsLong) CodeBox(title, caption = "title")
                toolCall.rawInput?.let { CodeBox(it.toString(), caption = "input", maxLines = 12) }
                Text(
                    "The agent is waiting for your answer.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pending.request.options.forEach { option -> OptionButton(option) { onOption(option.optionId) } }
                if (pending.request.options.isEmpty()) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Dismiss") }
                }
            }
        },
    )
}

private const val TITLE_MAX_CHARS = 60

@Composable
private fun OptionButton(option: PermissionOption, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth()
    val label: @Composable () -> Unit = { Text(option.name) }
    when (permissionButtonStyle(option.kind)) {
        PermissionButtonStyle.PRIMARY -> Button(onClick = onClick, modifier = modifier) { label() }
        PermissionButtonStyle.TONAL -> FilledTonalButton(onClick = onClick, modifier = modifier) { label() }
        PermissionButtonStyle.DESTRUCTIVE -> OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { label() }
        PermissionButtonStyle.NEUTRAL -> OutlinedButton(onClick = onClick, modifier = modifier) { label() }
    }
}

private val previewPending = PendingPermission(
    localSessionId = 1,
    sessionTitle = "Fix flaky login test",
    messageId = 1,
    request = AcpEvent.PermissionRequest(
        sessionId = "s1",
        requestId = JsonPrimitive(1),
        toolCall = ToolCallInfo(
            toolCallId = "c1",
            title = "Run ./gradlew test",
            kind = "execute",
            rawInput = AcpJson.parseToJsonElement("""{"command":"./gradlew test","cwd":"/home/dev/app"}"""),
        ),
        options = listOf(
            PermissionOption("allow", "Allow once", "allow_once"),
            PermissionOption("always", "Always allow", "allow_always"),
            PermissionOption("deny", "Deny", "reject_once"),
        ),
        raw = JsonPrimitive(0),
    ),
)

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PermissionDarkPreview() {
    AcpTheme(darkTheme = true) { PermissionDialog(previewPending, {}, {}) }
}

@Preview(name = "Light")
@Composable
private fun PermissionLightPreview() {
    AcpTheme(darkTheme = false) { PermissionDialog(previewPending, {}, {}) }
}
