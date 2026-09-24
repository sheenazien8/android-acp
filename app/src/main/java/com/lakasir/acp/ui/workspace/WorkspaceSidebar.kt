package com.lakasir.acp.ui.workspace

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lakasir.acp.acp.BridgeFeatures
import com.lakasir.acp.acp.FsEntry
import com.lakasir.acp.data.repository.WorkspaceAvailability
import com.lakasir.acp.ui.components.EmptyState
import com.lakasir.acp.ui.theme.AcpTheme

private sealed interface WorkspaceDialog {
    data class Create(val parent: String, val directory: Boolean) : WorkspaceDialog
    data class Rename(val entry: FsEntry) : WorkspaceDialog
    data class Delete(val entry: FsEntry) : WorkspaceDialog
}

data class FileActions(
    val onOpen: (FsEntry) -> Unit,
    val onToggle: (FsEntry) -> Unit,
    val onAttach: (FsEntry) -> Unit,
    val onCopyPath: (FsEntry) -> Unit,
    val onCreate: (parent: String, directory: Boolean) -> Unit,
    val onRename: (FsEntry) -> Unit,
    val onDelete: (FsEntry) -> Unit,
)

@Composable
fun WorkspaceSidebar(
    sessionId: Long,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkspaceViewModel = viewModel(key = "workspace-$sessionId", factory = WorkspaceViewModel.factory(sessionId)),
) {
    val availability by viewModel.availability.collectAsStateWithLifecycle()
    val root by viewModel.root.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    var dialog by remember { mutableStateOf<WorkspaceDialog?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val actions = FileActions(
        onOpen = { onOpenFile(it.path) },
        onToggle = viewModel::toggle,
        onAttach = { viewModel.attach(it.path) },
        onCopyPath = { clipboard.setText(AnnotatedString(it.path)) },
        onCreate = { parent, directory -> dialog = WorkspaceDialog.Create(parent, directory) },
        onRename = { dialog = WorkspaceDialog.Rename(it) },
        onDelete = { dialog = WorkspaceDialog.Delete(it) },
    )

    Box(modifier) {
        WorkspaceSidebarContent(
            availability = availability,
            root = root,
            rows = rows,
            actions = actions,
            onRefresh = viewModel::refresh,
        )
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    when (val current = dialog) {
        is WorkspaceDialog.Create -> NameDialog(
            title = if (current.directory) "New folder" else "New file",
            initial = "",
            confirmLabel = "Create",
            onConfirm = {
                viewModel.create(current.parent, it, current.directory)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is WorkspaceDialog.Rename -> NameDialog(
            title = "Rename",
            initial = current.entry.name,
            confirmLabel = "Rename",
            onConfirm = {
                viewModel.rename(current.entry, it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is WorkspaceDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete ${current.entry.name}?") },
            text = {
                Text(
                    if (current.entry.isDirectory) {
                        "The folder and everything in it will be deleted on the bridge machine. This can't be undone."
                    } else {
                        "The file will be deleted on the bridge machine. This can't be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(current.entry)
                    dialog = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        null -> Unit
    }
}

@Composable
fun WorkspaceSidebarContent(
    availability: WorkspaceAvailability,
    root: FolderState?,
    rows: List<TreeRow>,
    actions: FileActions,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        WorkspaceHeader(
            cwd = (availability as? WorkspaceAvailability.Ready)?.cwd,
            ready = availability is WorkspaceAvailability.Ready,
            onCreate = { directory -> actions.onCreate(WorkspacePaths.ROOT, directory) },
            onRefresh = onRefresh,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        when (availability) {
            WorkspaceAvailability.Offline -> EmptyState("Offline", "Connect to this bridge to browse its files.")
            WorkspaceAvailability.Checking -> CenteredProgress()
            WorkspaceAvailability.Unsupported -> EmptyState(
                "File access not available",
                "This bridge doesn't support file access. Run lakasir-acp-bridge on the machine to browse and edit files.",
            )
            is WorkspaceAvailability.Ready -> when {
                root == null || (root.loading && !root.loaded) -> CenteredProgress()
                root.error != null && !root.loaded -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState("Couldn't load files", root.error, Modifier.weight(1f, fill = false))
                    OutlinedButton(onClick = onRefresh) { Text("Try again") }
                }
                rows.isEmpty() -> EmptyState("Empty folder", "Use + to create a file or folder.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { it.key }, contentType = { it::class }) { row ->
                        when (row) {
                            is TreeRow.Entry -> FileTreeRow(row, actions)
                            is TreeRow.Note -> TreeNoteRow(row)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceHeader(cwd: String?, ready: Boolean, onCreate: (directory: Boolean) -> Unit, onRefresh: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Workspace", style = MaterialTheme.typography.titleSmall)
            if (cwd != null) {
                Text(
                    WorkspacePaths.shorten(cwd),
                    style = AcpTheme.code.codeSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, enabled = ready) {
                Icon(Icons.Filled.Add, contentDescription = "New file or folder")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text("New file") }, onClick = {
                    menuExpanded = false
                    onCreate(false)
                })
                DropdownMenuItem(text = { Text("New folder") }, onClick = {
                    menuExpanded = false
                    onCreate(true)
                })
            }
        }
        IconButton(onClick = onRefresh, enabled = ready) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh files")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreeRow(row: TreeRow.Entry, actions: FileActions) {
    val entry = row.entry
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (entry.isDirectory) actions.onToggle(entry) else if (entry.isFile) actions.onOpen(entry) },
                    onClickLabel = if (entry.isDirectory) (if (row.expanded) "Collapse" else "Expand") else "Open",
                    onLongClick = { menuExpanded = true },
                    onLongClickLabel = "File actions",
                )
                .heightIn(min = 48.dp)
                .padding(start = (8 + row.depth * 12).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.isDirectory) {
                Icon(
                    if (row.expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.width(20.dp))
            }
            Icon(
                when {
                    entry.isDirectory && row.expanded -> Icons.Outlined.FolderOpen
                    entry.isDirectory -> Icons.Outlined.Folder
                    entry.isFile -> Icons.Outlined.Description
                    else -> Icons.Outlined.Link
                },
                contentDescription = null,
                modifier = Modifier.padding(start = 2.dp, end = 8.dp).size(18.dp),
                tint = if (entry.isDirectory) AcpTheme.extended.accentText else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                entry.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.isDirectory || entry.isFile) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.isFile) {
                Text(
                    WorkspacePaths.formatSize(entry.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Actions for ${entry.name}", modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            val close = { menuExpanded = false }
            if (entry.isFile) {
                MenuItem("Open", close) { actions.onOpen(entry) }
                MenuItem("Attach to prompt", close) { actions.onAttach(entry) }
            }
            if (entry.isDirectory) {
                MenuItem("New file", close) { actions.onCreate(entry.path, false) }
                MenuItem("New folder", close) { actions.onCreate(entry.path, true) }
            }
            MenuItem("Copy path", close) { actions.onCopyPath(entry) }
            MenuItem("Rename", close) { actions.onRename(entry) }
            MenuItem("Delete", close, destructive = true) { actions.onDelete(entry) }
        }
    }
}

@Composable
private fun MenuItem(label: String, close: () -> Unit, destructive: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        onClick = {
            close()
            onClick()
        },
    )
}

@Composable
private fun TreeNoteRow(row: TreeRow.Note) {
    Text(
        row.text,
        modifier = Modifier.padding(start = (30 + row.depth * 12).dp, top = 8.dp, bottom = 8.dp, end = 16.dp),
        style = MaterialTheme.typography.labelSmall,
        color = if (row.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    var touched by rememberSaveable { mutableStateOf(false) }
    val error = WorkspacePaths.validateName(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    touched = true
                },
                singleLine = true,
                isError = touched && error != null,
                supportingText = { if (touched && error != null) Text(error) },
                textStyle = AcpTheme.code.codeMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = error == null) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val previewEntries = listOf(
    FsEntry("app", "app", "dir"),
    FsEntry("src", "app/src", "dir"),
    FsEntry("build.gradle.kts", "app/build.gradle.kts", "file", size = 2380),
    FsEntry("docs", "docs", "dir"),
    FsEntry("README.md", "README.md", "file", size = 812),
)

private val previewActions = FileActions({}, {}, {}, {}, { _, _ -> }, {}, {})

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 320, heightDp = 480)
@Composable
private fun WorkspaceSidebarDarkPreview() {
    AcpTheme(darkTheme = true) {
        WorkspaceSidebarContent(
            availability = WorkspaceAvailability.Ready("/home/me/code/android-acp", BridgeFeatures(1, fs = true)),
            root = FolderState(loaded = true),
            rows = listOf(
                TreeRow.Entry(previewEntries[0], 0, expanded = true),
                TreeRow.Entry(previewEntries[1], 1, expanded = false),
                TreeRow.Entry(previewEntries[2], 1, expanded = false),
                TreeRow.Entry(previewEntries[3], 0, expanded = true),
                TreeRow.Note("docs#empty", 1, "Empty folder"),
                TreeRow.Entry(previewEntries[4], 0, expanded = false),
            ),
            actions = previewActions,
            onRefresh = {},
        )
    }
}

@Preview(name = "Unsupported", widthDp = 320, heightDp = 480)
@Composable
private fun WorkspaceSidebarUnsupportedPreview() {
    AcpTheme(darkTheme = false) {
        WorkspaceSidebarContent(WorkspaceAvailability.Unsupported, null, emptyList(), previewActions, onRefresh = {})
    }
}
