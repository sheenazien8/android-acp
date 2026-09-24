package com.lakasir.acp.acp

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class BridgeFeatures(val version: Int = 0, val fs: Boolean = false, val git: Boolean = false)

@Serializable
data class FsEntry(
    val name: String,
    val path: String,
    val type: String,
    val size: Long = 0,
    val mtime: Long = 0,
    val symlink: Boolean = false,
) {
    val isDirectory: Boolean get() = type == FsEntryType.DIR
    val isFile: Boolean get() = type == FsEntryType.FILE
}

@Serializable
data class FsListing(val path: String = "", val entries: List<FsEntry> = emptyList(), val truncated: Boolean = false)

@Serializable
data class FsFile(
    val path: String,
    val text: String? = null,
    val binary: Boolean = false,
    val truncated: Boolean = false,
    val size: Long = 0,
    val mtime: Long = 0,
)

@Serializable
data class FsWriteResult(val path: String, val mtime: Long, val size: Long = 0)

class WorkspaceApi(private val client: AcpClient, val cwd: String) {

    suspend fun list(path: String): FsListing = call(LakasirMethods.FS_LIST, FsListing.serializer()) {
        put("path", path)
    }

    suspend fun read(path: String): FsFile = call(LakasirMethods.FS_READ, FsFile.serializer()) {
        put("path", path)
    }

    suspend fun write(path: String, text: String, expectedMtime: Long?): FsWriteResult =
        call(LakasirMethods.FS_WRITE, FsWriteResult.serializer()) {
            put("path", path)
            put("text", text)
            if (expectedMtime != null) put("expectedMtime", expectedMtime)
        }

    suspend fun create(path: String, directory: Boolean): FsEntry = call(LakasirMethods.FS_CREATE, FsEntry.serializer()) {
        put("path", path)
        put("type", if (directory) FsEntryType.DIR else FsEntryType.FILE)
    }

    suspend fun rename(from: String, to: String): FsEntry = call(LakasirMethods.FS_RENAME, FsEntry.serializer()) {
        put("from", from)
        put("to", to)
    }

    suspend fun delete(path: String, recursive: Boolean) {
        client.request(LakasirMethods.FS_DELETE, params {
            put("path", path)
            put("recursive", recursive)
        })
    }

    private suspend fun <T> call(
        method: String,
        serializer: KSerializer<T>,
        build: JsonObjectBuilder.() -> Unit,
    ): T = decode(client.request(method, params(build)), serializer)

    private fun params(build: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
        put("cwd", cwd)
        build()
    }

    companion object {
        const val HELLO_TIMEOUT_MS = 5_000L

        suspend fun hello(client: AcpClient): BridgeFeatures? = try {
            decode(client.request(LakasirMethods.HELLO, buildJsonObject {}, HELLO_TIMEOUT_MS), BridgeFeatures.serializer())
        } catch (e: AcpException) {
            null
        }

        fun <T> decode(element: JsonElement, serializer: KSerializer<T>): T = try {
            AcpJson.decodeFromJsonElement(serializer, element)
        } catch (e: IllegalArgumentException) {
            throw AcpException.InvalidResponse(e.message ?: "unexpected workspace result")
        }
    }
}
