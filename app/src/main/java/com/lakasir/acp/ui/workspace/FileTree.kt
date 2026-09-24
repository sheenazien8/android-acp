package com.lakasir.acp.ui.workspace

import com.lakasir.acp.acp.FsEntry
import java.util.Locale

data class FolderState(
    val entries: List<FsEntry> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val truncated: Boolean = false,
)

sealed interface TreeRow {
    val key: String
    val depth: Int

    data class Entry(val entry: FsEntry, override val depth: Int, val expanded: Boolean) : TreeRow {
        override val key: String get() = entry.path
    }

    data class Note(override val key: String, override val depth: Int, val text: String, val isError: Boolean = false) : TreeRow
}

object WorkspacePaths {
    const val ROOT = ""

    fun child(parent: String, name: String): String = if (parent.isEmpty()) name else "$parent/$name"

    fun parent(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = ROOT)

    fun name(path: String): String = path.substringAfterLast('/')

    fun isUnder(path: String, ancestor: String): Boolean = path == ancestor || path.startsWith("$ancestor/")

    fun validateName(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Enter a name"
            '/' in trimmed || '\\' in trimmed -> "Names can't contain / or \\"
            trimmed == "." || trimmed == ".." -> "That name isn't allowed"
            else -> null
        }
    }

    fun shorten(cwd: String, keep: Int = 2): String {
        val parts = cwd.trimEnd('/').split('/').filter { it.isNotEmpty() }
        return if (parts.size <= keep) cwd else "…/" + parts.takeLast(keep).joinToString("/")
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

fun flattenTree(
    folders: Map<String, FolderState>,
    expanded: Set<String>,
    path: String = WorkspacePaths.ROOT,
    depth: Int = 0,
): List<TreeRow> {
    val folder = folders[path] ?: return emptyList()
    val rows = mutableListOf<TreeRow>()
    folder.entries.forEach { entry ->
        val isOpen = entry.isDirectory && entry.path in expanded
        rows += TreeRow.Entry(entry, depth, isOpen)
        if (isOpen) rows += childRows(folders, expanded, entry.path, depth + 1)
    }
    if (folder.truncated) rows += TreeRow.Note("$path#truncated", depth, "Too many items, only the first ones are shown")
    return rows
}

private fun childRows(folders: Map<String, FolderState>, expanded: Set<String>, path: String, depth: Int): List<TreeRow> {
    val folder = folders[path]
    return when {
        folder == null || (folder.loading && !folder.loaded) -> listOf(TreeRow.Note("$path#loading", depth, "Loading…"))
        folder.error != null && !folder.loaded -> listOf(TreeRow.Note("$path#error", depth, folder.error, isError = true))
        folder.entries.isEmpty() && !folder.truncated -> listOf(TreeRow.Note("$path#empty", depth, "Empty folder"))
        else -> flattenTree(folders, expanded, path, depth)
    }
}
