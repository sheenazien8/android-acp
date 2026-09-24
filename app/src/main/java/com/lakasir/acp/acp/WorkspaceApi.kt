package com.lakasir.acp.acp

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

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

    suspend fun gitStatus(): GitStatus = call(LakasirMethods.GIT_STATUS, GitStatus.serializer()) {}

    suspend fun gitDiff(path: String, staged: Boolean, origPath: String? = null): GitDiff =
        call(LakasirMethods.GIT_DIFF, GitDiff.serializer()) {
            put("path", path)
            put("staged", staged)
            if (origPath != null) put("origPath", origPath)
        }

    suspend fun gitLog(limit: Int, skip: Int): GitLog = call(LakasirMethods.GIT_LOG, GitLog.serializer()) {
        put("limit", limit)
        put("skip", skip)
    }

    suspend fun gitShow(hash: String): GitCommitDetail = call(LakasirMethods.GIT_SHOW, GitCommitDetail.serializer()) {
        put("hash", hash)
    }

    suspend fun gitStage(paths: List<String>) {
        client.request(LakasirMethods.GIT_STAGE, params { putJsonArray("paths") { paths.forEach { add(it) } } })
    }

    suspend fun gitUnstage(paths: List<String>) {
        client.request(LakasirMethods.GIT_UNSTAGE, params { putJsonArray("paths") { paths.forEach { add(it) } } })
    }

    suspend fun gitCommit(message: String): GitCommitResult =
        call(LakasirMethods.GIT_COMMIT, GitCommitResult.serializer(), COMMIT_TIMEOUT_MS) {
            put("message", message)
        }

    private suspend fun <T> call(
        method: String,
        serializer: KSerializer<T>,
        timeoutMs: Long? = null,
        build: JsonObjectBuilder.() -> Unit,
    ): T {
        val params = params(build)
        val result = if (timeoutMs == null) client.request(method, params) else client.request(method, params, timeoutMs)
        return decode(result, serializer)
    }

    private fun params(build: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
        put("cwd", cwd)
        build()
    }

    companion object {
        const val HELLO_TIMEOUT_MS = 5_000L
        const val COMMIT_TIMEOUT_MS = 75_000L

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
