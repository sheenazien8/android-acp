package com.lakasir.acp.ui.workspace

import com.lakasir.acp.acp.FsEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FileTreeTest {

    private fun dir(path: String) = FsEntry(path.substringAfterLast('/'), path, "dir")
    private fun file(path: String) = FsEntry(path.substringAfterLast('/'), path, "file")

    private val folders = mapOf(
        "" to FolderState(listOf(dir("app"), dir("docs"), file("README.md")), loaded = true),
        "app" to FolderState(listOf(dir("app/src"), file("app/build.gradle.kts")), loaded = true),
        "docs" to FolderState(loaded = true),
    )

    private fun describe(rows: List<TreeRow>) = rows.map {
        when (it) {
            is TreeRow.Entry -> "${it.depth}:${it.entry.name}${if (it.expanded) "/" else ""}"
            is TreeRow.Note -> "${it.depth}:(${it.text})"
        }
    }

    @Test
    fun `collapsed tree shows only root entries`() {
        assertEquals(listOf("0:app", "0:docs", "0:README.md"), describe(flattenTree(folders, emptySet())))
    }

    @Test
    fun `expanded folders show children, empty and loading notes`() {
        val rows = flattenTree(folders, setOf("app", "docs", "app/src"))
        assertEquals(
            listOf("0:app/", "1:src/", "2:(Loading…)", "1:build.gradle.kts", "0:docs/", "1:(Empty folder)", "0:README.md"),
            describe(rows),
        )
    }

    @Test
    fun `failed folder shows its error`() {
        val rows = flattenTree(folders + ("app" to FolderState(error = "boom")), setOf("app"))
        val note = rows[1] as TreeRow.Note
        assertEquals("boom", note.text)
        assertEquals(true, note.isError)
    }

    @Test
    fun `expanded state is ignored for files and truncation adds a note`() {
        val truncated = mapOf("" to FolderState(listOf(file("a.txt")), loaded = true, truncated = true))
        assertEquals(listOf("0:a.txt", "0:(Too many items, only the first ones are shown)"), describe(flattenTree(truncated, setOf("a.txt"))))
    }

    @Test
    fun `paths are joined and split relative to the root`() {
        assertEquals("a.txt", WorkspacePaths.child("", "a.txt"))
        assertEquals("src/a.txt", WorkspacePaths.child("src", "a.txt"))
        assertEquals("", WorkspacePaths.parent("a.txt"))
        assertEquals("src/main", WorkspacePaths.parent("src/main/a.kt"))
        assertEquals("a.kt", WorkspacePaths.name("src/main/a.kt"))
        assertEquals(true, WorkspacePaths.isUnder("src/main", "src"))
        assertEquals(false, WorkspacePaths.isUnder("src2/main", "src"))
    }

    @Test
    fun `names are validated`() {
        assertNull(WorkspacePaths.validateName("notes.md"))
        assertNotNull(WorkspacePaths.validateName("  "))
        assertNotNull(WorkspacePaths.validateName("a/b"))
        assertNotNull(WorkspacePaths.validateName(".."))
    }

    @Test
    fun `cwd is shortened and sizes are formatted`() {
        assertEquals("…/code/android-acp", WorkspacePaths.shorten("/home/me/code/android-acp"))
        assertEquals("/srv/app", WorkspacePaths.shorten("/srv/app"))
        assertEquals("512 B", WorkspacePaths.formatSize(512))
        assertEquals("1.5 KB", WorkspacePaths.formatSize(1536))
        assertEquals("2.0 MB", WorkspacePaths.formatSize(2L * 1024 * 1024))
    }
}
