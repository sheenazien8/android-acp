package com.lakasir.acp.ui.chat

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lakasir.acp.acp.AgentInfo
import com.lakasir.acp.data.model.DiffState
import com.lakasir.acp.data.model.PermissionRecord
import com.lakasir.acp.data.model.PlanItem
import com.lakasir.acp.data.model.ToolCallState
import com.lakasir.acp.data.repository.ConnectionState
import com.lakasir.acp.ui.chat.components.AgentText
import com.lakasir.acp.ui.chat.components.ChatInputBar
import com.lakasir.acp.ui.chat.components.ErrorRow
import com.lakasir.acp.ui.chat.components.LogRow
import com.lakasir.acp.ui.chat.components.PermissionRecordRow
import com.lakasir.acp.ui.chat.components.PlanBlock
import com.lakasir.acp.ui.chat.components.SystemNoteRow
import com.lakasir.acp.ui.chat.components.ThinkingIndicator
import com.lakasir.acp.ui.chat.components.ThoughtBlock
import com.lakasir.acp.ui.chat.components.ToolCallBlock
import com.lakasir.acp.ui.components.ConnectionStatusIndicator
import com.lakasir.acp.ui.sessions.DeleteSessionDialog
import com.lakasir.acp.ui.sessions.RenameSessionDialog
import com.lakasir.acp.ui.sessions.SessionMenuButton
import com.lakasir.acp.ui.theme.AcpTheme
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    sessionId: Long,
    onBack: (() -> Unit)?,
    onDeleted: () -> Unit,
    viewModel: ChatViewModel = viewModel(key = "chat-$sessionId", factory = ChatViewModel.factory(sessionId)),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val availability by viewModel.availability.collectAsStateWithLifecycle()
    var input by rememberSaveable(sessionId) { mutableStateOf("") }

    ChatContent(
        title = session?.title.orEmpty(),
        profileId = session?.connectionProfileId,
        autoApprove = session?.autoApprove == true,
        items = items.orEmpty(),
        connectionState = connectionState,
        isBusy = isBusy,
        availability = availability,
        input = input,
        onInputChange = { input = it },
        onSend = {
            viewModel.send(input)
            input = ""
        },
        onCancel = viewModel::cancel,
        onRename = viewModel::rename,
        onDelete = { viewModel.delete(onDeleted) },
        onAutoApproveChange = viewModel::setAutoApprove,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContent(
    title: String,
    profileId: Long?,
    autoApprove: Boolean,
    items: List<ChatItem>,
    connectionState: ConnectionState,
    isBusy: Boolean,
    availability: InputAvailability,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onAutoApproveChange: (Boolean) -> Unit,
    onBack: (() -> Unit)?,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showAutoConfirm by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val atBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 48 }
    }
    val reversed = remember(items) { items.asReversed() }
    val lastAgentTextId = remember(items) { items.lastOrNull()?.takeIf { it is ChatItem.AgentText }?.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        if (autoApprove) {
                            Text("Auto mode", style = MaterialTheme.typography.labelSmall, color = AcpTheme.extended.accentText)
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to sessions")
                        }
                    }
                },
                actions = {
                    ConnectionStatusIndicator(connectionState, profileId = profileId)
                    IconToggleButton(
                        checked = autoApprove,
                        onCheckedChange = { enabled -> if (enabled) showAutoConfirm = true else onAutoApproveChange(false) },
                        modifier = Modifier.semantics {
                            contentDescription = if (autoApprove) "Auto mode on" else "Auto mode off"
                        },
                    ) {
                        Icon(
                            if (autoApprove) Icons.Filled.Bolt else Icons.Outlined.Bolt,
                            contentDescription = null,
                            tint = if (autoApprove) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SessionMenuButton(
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = it },
                        onRename = { showRename = true },
                        onDelete = { showDelete = true },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            ChatInputBar(
                text = input,
                onTextChange = onInputChange,
                enabled = availability == InputAvailability.Ready,
                isBusy = isBusy,
                hint = availability.hint(),
                onSend = onSend,
                onCancel = onCancel,
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (isBusy) {
                    item(key = "thinking", contentType = "thinking") { ThinkingIndicator(Modifier.padding(start = 14.dp)) }
                }
                items(reversed, key = { it.id }, contentType = { it::class }) { item ->
                    ChatItemRow(item, streaming = isBusy && item.id == lastAgentTextId)
                }
            }
            if (!atBottom) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Jump to latest")
                }
            }
        }
    }

    if (showRename) {
        RenameSessionDialog(
            currentTitle = title,
            onConfirm = {
                onRename(it)
                showRename = false
            },
            onDismiss = { showRename = false },
        )
    }

    if (showAutoConfirm) {
        AlertDialog(
            onDismissRequest = { showAutoConfirm = false },
            icon = { Icon(Icons.Filled.Bolt, contentDescription = null) },
            title = { Text("Turn on auto mode?") },
            text = { Text("The agent will run tools and edit files in this session without asking. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    showAutoConfirm = false
                    onAutoApproveChange(true)
                }) { Text("Turn on") }
            },
            dismissButton = { TextButton(onClick = { showAutoConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showDelete) {
        DeleteSessionDialog(
            title = title,
            onConfirm = {
                showDelete = false
                onDelete()
            },
            onDismiss = { showDelete = false },
        )
    }
}

@Composable
private fun ChatItemRow(item: ChatItem, streaming: Boolean) {
    when (item) {
        is ChatItem.UserText -> LogRow(borderColor = AcpTheme.extended.roleUser, label = "you") {
            Text(item.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        is ChatItem.AgentText -> LogRow(borderColor = AcpTheme.extended.roleAgent, label = "agent") {
            AgentText(item.text, streaming = streaming)
        }
        is ChatItem.Thought -> ThoughtBlock(item.id, item.text)
        is ChatItem.Tool -> ToolCallBlock(item.state)
        is ChatItem.Plan -> PlanBlock(item.items)
        is ChatItem.Permission -> PermissionRecordRow(item.record)
        is ChatItem.Error -> ErrorRow(item.text)
        is ChatItem.SystemNote -> SystemNoteRow(item.text)
    }
}

private fun InputAvailability.hint(): String? = when (this) {
    InputAvailability.Ready -> null
    InputAvailability.Offline -> "Offline. Connect to this bridge to continue."
    InputAvailability.Resuming -> "Resuming session…"
    InputAvailability.NotResumable -> "This agent can't resume past sessions. Start a new one."
}

@Composable
fun ChatPlaceholder() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Select a session", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val previewItems = listOf(
    ChatItem.UserText(1, "Make the login test stable"),
    ChatItem.Thought(2, "The test waits on a fixed delay, which races with token refresh."),
    ChatItem.Plan(3, listOf(PlanItem("Find the race", "completed"), PlanItem("Patch the test", "in_progress"))),
    ChatItem.Tool(
        4,
        ToolCallState(
            toolCallId = "c1",
            title = "Edit LoginTest.kt",
            kind = "edit",
            status = "completed",
            diffs = listOf(DiffState("src/test/LoginTest.kt", "delay(500)\nassertLoggedIn()", "awaitIdle()\nassertLoggedIn()")),
        ),
    ),
    ChatItem.Permission(5, PermissionRecord("Run ./gradlew test", "execute", choice = "Allow once", auto = true)),
    ChatItem.AgentText(6, "Replaced the fixed delay with `awaitIdle()`:\n```kotlin\nawaitIdle()\n```\nThe test now passes 50/50 runs."),
    ChatItem.Error(7, "Request 'session/prompt' timed out"),
)

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 900)
@Composable
private fun ChatDarkPreview() {
    AcpTheme(darkTheme = true) {
        ChatContent(
            "Fix flaky login test", 1, true, previewItems,
            ConnectionState.Connected(1, AgentInfo(1, "agent", "1.0", true)),
            isBusy = true, availability = InputAvailability.Ready, input = "",
            onInputChange = {}, onSend = {}, onCancel = {}, onRename = {}, onDelete = {}, onAutoApproveChange = {}, onBack = {},
        )
    }
}

@Preview(name = "Light", heightDp = 900)
@Composable
private fun ChatLightPreview() {
    AcpTheme(darkTheme = false) {
        ChatContent(
            "Fix flaky login test", 1, false, previewItems, ConnectionState.Disconnected,
            isBusy = false, availability = InputAvailability.Offline, input = "",
            onInputChange = {}, onSend = {}, onCancel = {}, onRename = {}, onDelete = {}, onAutoApproveChange = {}, onBack = {},
        )
    }
}
