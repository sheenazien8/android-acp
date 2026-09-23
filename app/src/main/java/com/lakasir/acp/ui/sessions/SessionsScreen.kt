package com.lakasir.acp.ui.sessions

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lakasir.acp.acp.AgentInfo
import com.lakasir.acp.data.local.ConnectionProfileEntity
import com.lakasir.acp.data.local.SessionEntity
import com.lakasir.acp.data.local.SessionSummary
import com.lakasir.acp.data.repository.ConnectionState
import com.lakasir.acp.ui.AppViewModelProvider
import com.lakasir.acp.ui.components.ConnectionStatusIndicator
import com.lakasir.acp.ui.components.EmptyState
import com.lakasir.acp.ui.components.SwipeToDelete
import com.lakasir.acp.ui.components.leftBorder
import com.lakasir.acp.ui.components.relativeTime
import com.lakasir.acp.ui.theme.AcpTheme

@Composable
fun SessionsScreen(
    isExpanded: Boolean,
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
    detailPane: @Composable (sessionId: Long?) -> Unit,
    viewModel: SessionsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val busy by viewModel.busySessions.collectAsStateWithLifecycle()
    val creating by viewModel.creating.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val createdSession by viewModel.createdSession.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val open: (Long) -> Unit = { id -> if (isExpanded) selectedId = id else onOpenSession(id) }

    LaunchedEffect(createdSession) {
        createdSession?.let {
            open(it)
            viewModel.onCreatedSessionOpened()
        }
    }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    val list: @Composable (Modifier) -> Unit = { modifier ->
        SessionsContent(
            profile = profile,
            profileId = viewModel.profileId,
            sessions = sessions,
            connectionState = connectionState,
            busy = busy,
            creating = creating,
            selectedId = if (isExpanded) selectedId else null,
            snackbarHostState = snackbarHostState,
            onBack = onBack,
            onOpen = open,
            onCreate = viewModel::createSession,
            onConnect = viewModel::connect,
            onDelete = { id ->
                if (selectedId == id) selectedId = null
                viewModel.deleteSession(id)
            },
            modifier = modifier,
        )
    }

    if (isExpanded) {
        Row(Modifier.fillMaxSize()) {
            list(Modifier.width(360.dp).fillMaxHeight())
            VerticalDivider(color = MaterialTheme.colorScheme.outline)
            Box(Modifier.weight(1f).fillMaxHeight()) { detailPane(selectedId) }
        }
    } else {
        list(Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsContent(
    profile: ConnectionProfileEntity?,
    profileId: Long,
    sessions: List<SessionSummary>?,
    connectionState: ConnectionState,
    busy: Set<Long>,
    creating: Boolean,
    selectedId: Long?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onConnect: () -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnected = connectionState is ConnectionState.Connected && connectionState.profileId == profileId
    val isActive = connectionState.profileId == profileId
    var pendingDelete by remember { mutableStateOf<SessionEntity?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(profile?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            profile?.let {
                                Text(
                                    "${it.host}:${it.port}",
                                    style = AcpTheme.code.codeSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to connections")
                        }
                    },
                    actions = { ConnectionStatusIndicator(connectionState, profileId = profileId) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                if (creating) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!isActive) OfflineRow(onConnect)
            }
        },
        floatingActionButton = {
            if (isConnected) {
                ExtendedFloatingActionButton(
                    onClick = { if (!creating) onCreate() },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New session") },
                    containerColor = MaterialTheme.colorScheme.primary,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                sessions == null -> Unit
                sessions.isEmpty() -> EmptyState(
                    title = "No sessions",
                    body = if (isConnected) "Start a new session to talk to the agent." else "Connect to this bridge to start a session.",
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(sessions, key = { it.session.id }) { summary ->
                        SwipeToDelete(onDeleteRequest = { pendingDelete = summary.session }) {
                            SessionRow(
                                summary = summary,
                                isBusy = summary.session.id in busy,
                                isSelected = summary.session.id == selectedId,
                                onClick = { onOpen(summary.session.id) },
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete session?") },
            text = { Text("\"${session.title}\" and its history will be removed from this device. The agent is not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(session.id)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun OfflineRow(onConnect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Offline. History is read-only.",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onConnect) { Text("Connect") }
    }
}

@Composable
private fun SessionRow(summary: SessionSummary, isBusy: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val session = summary.session
    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .leftBorder(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background,
        ),
        headlineContent = { Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = summary.preview?.let {
            { Text(it.trim(), maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(relativeTime(session.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isBusy) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            }
        },
    )
}

private val previewSessions = System.currentTimeMillis().let { now ->
    listOf(
        SessionSummary(SessionEntity(1, "r1", 1, "Fix flaky login test", now, now - 60_000), "I found the race in AuthRepository…"),
        SessionSummary(SessionEntity(2, "r2", 1, "Add pagination to orders API", now, now - 7_200_000), null),
    )
}

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SessionsDarkPreview() {
    AcpTheme(darkTheme = true) {
        SessionsContent(
            profile = ConnectionProfileEntity(1, "Workstation", "192.168.1.10", 8080, "/home/dev", 0),
            profileId = 1,
            sessions = previewSessions,
            connectionState = ConnectionState.Connected(1, AgentInfo(1, "agent", "1.0", true)),
            busy = setOf(1L),
            creating = false,
            selectedId = null,
            snackbarHostState = SnackbarHostState(),
            onBack = {}, onOpen = {}, onCreate = {}, onConnect = {}, onDelete = {},
        )
    }
}

@Preview(name = "Light")
@Composable
private fun SessionsLightPreview() {
    AcpTheme(darkTheme = false) {
        SessionsContent(
            profile = ConnectionProfileEntity(1, "Workstation", "192.168.1.10", 8080, "/home/dev", 0),
            profileId = 1,
            sessions = previewSessions,
            connectionState = ConnectionState.Disconnected,
            busy = emptySet(),
            creating = false,
            selectedId = 2,
            snackbarHostState = SnackbarHostState(),
            onBack = {}, onOpen = {}, onCreate = {}, onConnect = {}, onDelete = {},
        )
    }
}
