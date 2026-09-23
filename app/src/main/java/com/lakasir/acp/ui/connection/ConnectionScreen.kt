package com.lakasir.acp.ui.connection

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lakasir.acp.data.local.ConnectionProfileEntity
import com.lakasir.acp.data.repository.ConnectionState
import com.lakasir.acp.ui.AppViewModelProvider
import com.lakasir.acp.ui.components.ConnectionStatusIndicator
import com.lakasir.acp.ui.components.EmptyState
import com.lakasir.acp.ui.components.SwipeToDelete
import com.lakasir.acp.ui.components.leftBorder
import com.lakasir.acp.ui.components.relativeTime
import com.lakasir.acp.ui.theme.AcpTheme

@Composable
fun ConnectionScreen(
    onOpenProfile: (Long) -> Unit,
    viewModel: ConnectionViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val openProfile by viewModel.openProfile.collectAsStateWithLifecycle()

    LaunchedEffect(openProfile) {
        openProfile?.let {
            onOpenProfile(it)
            viewModel.onProfileOpened()
        }
    }

    ConnectionContent(
        profiles = profiles,
        connectionState = connectionState,
        onAdd = viewModel::newProfile,
        onOpen = { onOpenProfile(it.id) },
        onEdit = viewModel::editProfile,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
        onDelete = viewModel::delete,
    )

    form?.let {
        ProfileFormSheet(
            form = it,
            onChange = viewModel::updateForm,
            onSave = viewModel::saveForm,
            onDismiss = viewModel::dismissForm,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionContent(
    profiles: List<ConnectionProfileEntity>?,
    connectionState: ConnectionState,
    onAdd: () -> Unit,
    onOpen: (ConnectionProfileEntity) -> Unit,
    onEdit: (ConnectionProfileEntity) -> Unit,
    onConnect: (ConnectionProfileEntity) -> Unit,
    onDisconnect: () -> Unit,
    onDelete: (ConnectionProfileEntity) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<ConnectionProfileEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connections") },
                actions = { ConnectionStatusIndicator(connectionState) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.Add, contentDescription = "Add connection")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                profiles == null -> Unit
                profiles.isEmpty() -> EmptyState(
                    title = "No bridges yet",
                    body = "Add the LAN address of a machine running an ACP bridge at ws://host:port/acp.",
                )
                else -> LazyColumn(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        SwipeToDelete(onDeleteRequest = { pendingDelete = profile }) {
                            ProfileRow(
                                profile = profile,
                                connectionState = connectionState,
                                onOpen = { onOpen(profile) },
                                onEdit = { onEdit(profile) },
                                onConnect = { onConnect(profile) },
                                onDisconnect = onDisconnect,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${profile.name}?") },
            text = { Text("Its sessions and message history on this device will be removed too.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(profile)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: ConnectionProfileEntity,
    connectionState: ConnectionState,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val isActive = connectionState.profileId == profile.id
    val isConnected = isActive && connectionState is ConnectionState.Connected
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .leftBorder(if (isConnected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(profile.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${profile.host}:${profile.port}",
                    style = AcpTheme.code.codeSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    profile.cwd,
                    style = AcpTheme.code.codeSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProfileStatusLine(profile, connectionState.takeIf { isActive })
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit ${profile.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isActive) {
                OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            } else {
                FilledTonalButton(onClick = onConnect) { Text("Connect") }
            }
        }
    }
}

@Composable
private fun ProfileStatusLine(profile: ConnectionProfileEntity, state: ConnectionState?) {
    val style = MaterialTheme.typography.bodySmall
    when (state) {
        is ConnectionState.Error -> Text(
            "${state.message} · retry in ${state.retryInMs / 1000}s",
            style = style,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        is ConnectionState.Connecting -> Text(
            if (state.attempt == 0) "Connecting…" else "Reconnecting, attempt ${state.attempt + 1}…",
            style = style,
            color = AcpTheme.extended.warning,
        )
        is ConnectionState.Connected -> Text(
            listOfNotNull(state.agent.name, state.agent.version).joinToString(" ").ifEmpty { "Agent connected" },
            style = style,
            color = AcpTheme.extended.accentText,
        )
        else -> profile.lastConnectedAt?.let {
            Text("Last connected ${relativeTime(it)}", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val previewProfiles = listOf(
    ConnectionProfileEntity(1, "Workstation", "192.168.1.10", 8080, "/home/dev/project", 0, System.currentTimeMillis() - 3_600_000),
    ConnectionProfileEntity(2, "Build box", "192.168.1.42", 9000, "/srv/repo", 0, null),
)

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConnectionDarkPreview() {
    AcpTheme(darkTheme = true) {
        ConnectionContent(
            previewProfiles,
            ConnectionState.Error(2, "Failed to connect to /192.168.1.42:9000", 4_000),
            {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(name = "Light")
@Composable
private fun ConnectionLightPreview() {
    AcpTheme(darkTheme = false) {
        ConnectionContent(previewProfiles, ConnectionState.Disconnected, {}, {}, {}, {}, {}, {})
    }
}
