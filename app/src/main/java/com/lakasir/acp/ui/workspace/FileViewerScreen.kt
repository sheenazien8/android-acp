package com.lakasir.acp.ui.workspace

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lakasir.acp.acp.FsFile
import com.lakasir.acp.ui.components.EmptyState
import com.lakasir.acp.ui.markdown.MarkdownView
import com.lakasir.acp.ui.theme.AcpTheme

@Composable
fun FileViewerScreen(
    sessionId: Long,
    path: String,
    onBack: () -> Unit,
    viewModel: FileViewerViewModel = viewModel(
        key = "file-$sessionId-$path",
        factory = FileViewerViewModel.factory(sessionId, path),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val requestBack = { if (state.dirty) confirmDiscard = true else onBack() }
    BackHandler(enabled = state.editing) {
        if (state.dirty) confirmDiscard = true else viewModel.cancelEditing()
    }

    FileViewerContent(
        path = path,
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = requestBack,
        onReload = viewModel::load,
        onAttach = viewModel::attach,
        onEdit = viewModel::startEditing,
        onCancelEdit = { if (state.dirty) confirmDiscard = true else viewModel.cancelEditing() },
        onSave = { viewModel.save() },
        onDraftChange = viewModel::updateDraft,
        onToggleRendered = viewModel::toggleRendered,
    )

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to ${WorkspacePaths.name(path)} haven't been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    viewModel.cancelEditing()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }

    if (state.conflict) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConflict,
            title = { Text("File changed on the bridge") },
            text = { Text("${WorkspacePaths.name(path)} was modified since you opened it. Overwrite it with your version, or reload and lose your edits?") },
            confirmButton = {
                TextButton(onClick = { viewModel.save(overwrite = true) }) {
                    Text("Overwrite", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::load) { Text("Reload") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerContent(
    path: String,
    state: FileViewerState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onAttach: () -> Unit,
    onEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit,
    onDraftChange: (String) -> Unit,
    onToggleRendered: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            WorkspacePaths.name(path),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (state.dirty) "$path · edited" else path,
                            style = AcpTheme.code.codeSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.editing) {
                        IconButton(onClick = onCancelEdit) {
                            Icon(Icons.Filled.Close, contentDescription = "Stop editing")
                        }
                        TextButton(onClick = onSave, enabled = state.dirty && !state.saving) {
                            Text(if (state.saving) "Saving…" else "Save")
                        }
                    } else {
                        if (state.canRender) {
                            IconButton(onClick = onToggleRendered) {
                                if (state.rendered) {
                                    Icon(Icons.Filled.Code, contentDescription = "Show markdown source")
                                } else {
                                    Icon(Icons.AutoMirrored.Outlined.Article, contentDescription = "Render markdown")
                                }
                            }
                        }
                        IconButton(onClick = onAttach, enabled = state.file != null) {
                            Icon(Icons.Filled.AttachFile, contentDescription = "Attach to prompt")
                        }
                        IconButton(onClick = onEdit, enabled = state.editable) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit file")
                        }
                        IconButton(onClick = onReload) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reload file")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val file = state.file
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 2.dp)
                state.error != null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState("Couldn't open file", state.error, Modifier.weight(1f))
                    OutlinedButton(onClick = onReload, modifier = Modifier.padding(bottom = 32.dp)) { Text("Try again") }
                }
                file == null -> Unit
                file.binary || file.text == null -> EmptyState(
                    "Binary file",
                    "${WorkspacePaths.formatSize(file.size)}. Preview isn't available for this file.",
                )
                state.editing -> FileEditor(state.draft, onDraftChange)
                else -> Column(Modifier.fillMaxSize()) {
                    if (file.truncated) TruncatedBanner(file)
                    if (state.showRendered) MarkdownView(file.text, Modifier.navigationBarsPadding()) else FileLines(file.text)
                }
            }
        }
    }
}

@Composable
private fun TruncatedBanner(file: FsFile) {
    Text(
        "Showing the first part of a ${WorkspacePaths.formatSize(file.size)} file. Editing is off for large files.",
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = AcpTheme.extended.warning,
    )
}

@Composable
private fun FileLines(text: String) {
    val lines = remember(text) { text.removeSuffix("\n").lines() }
    val gutterWidth = (lines.size.toString().length * 8 + 12).dp
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
            itemsIndexed(lines) { index, line ->
                Row(Modifier.fillMaxWidth().padding(end = 12.dp)) {
                    Text(
                        (index + 1).toString(),
                        modifier = Modifier.width(gutterWidth).padding(end = 8.dp),
                        style = AcpTheme.code.codeSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                    Text(line, style = AcpTheme.code.codeSmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun FileEditor(text: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        BasicTextField(
            value = text,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            textStyle = AcpTheme.code.codeSmall.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}

private val previewFile = FsFile(
    path = "app/src/main/Main.kt",
    text = "package demo\n\nfun main() {\n    println(\"Hello from the bridge\")\n}\n",
    size = 64,
    mtime = 1,
)

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 480)
@Composable
private fun FileViewerDarkPreview() {
    AcpTheme(darkTheme = true) {
        FileViewerContent(
            path = previewFile.path,
            state = FileViewerState(loading = false, file = previewFile),
            snackbarHostState = SnackbarHostState(),
            onBack = {}, onReload = {}, onAttach = {}, onEdit = {}, onCancelEdit = {}, onSave = {}, onDraftChange = {},
        )
    }
}

@Preview(name = "Editing", heightDp = 480)
@Composable
private fun FileViewerEditingPreview() {
    AcpTheme(darkTheme = false) {
        FileViewerContent(
            path = previewFile.path,
            state = FileViewerState(loading = false, file = previewFile, editing = true, draft = previewFile.text + "// edit\n"),
            snackbarHostState = SnackbarHostState(),
            onBack = {}, onReload = {}, onAttach = {}, onEdit = {}, onCancelEdit = {}, onSave = {}, onDraftChange = {},
        )
    }
}
