package com.lakasir.acp.data.repository

import com.lakasir.acp.acp.AcpException
import com.lakasir.acp.acp.BridgeFeatures
import com.lakasir.acp.acp.JsonRpcErrorCode
import com.lakasir.acp.acp.LakasirErrorCode
import com.lakasir.acp.acp.WorkspaceApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

sealed interface WorkspaceAvailability {
    data object Offline : WorkspaceAvailability
    data object Checking : WorkspaceAvailability
    data object Unsupported : WorkspaceAvailability
    data class Ready(val cwd: String, val features: BridgeFeatures) : WorkspaceAvailability
}

sealed interface WorkspaceResult<out T> {
    data class Success<T>(val value: T) : WorkspaceResult<T>
    data class Failure(val message: String, val code: Int? = null) : WorkspaceResult<Nothing> {
        val isConflict: Boolean get() = code == LakasirErrorCode.CONFLICT
        val isNotFound: Boolean get() = code == LakasirErrorCode.NOT_FOUND
    }
}

class WorkspaceRepository(private val repository: AcpRepository) {

    private val attachments = MutableStateFlow<Map<Long, List<String>>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    fun availability(sessionId: Long): Flow<WorkspaceAvailability> =
        repository.observeSession(sessionId).flatMapLatest { session ->
            if (session == null) return@flatMapLatest flowOf(WorkspaceAvailability.Offline)
            val profileId = session.connectionProfileId
            combine(
                repository.observeProfile(profileId),
                repository.connectionStates,
                repository.bridgeFeatures,
            ) { profile, states, features ->
                val connection = states[profileId]
                when {
                    profile == null || connection !is ConnectionState.Connected -> WorkspaceAvailability.Offline
                    profileId !in features -> WorkspaceAvailability.Checking
                    features[profileId]?.fs != true -> WorkspaceAvailability.Unsupported
                    else -> WorkspaceAvailability.Ready(profile.cwd, features.getValue(profileId)!!)
                }
            }
        }.distinctUntilChanged()

    fun changes(sessionId: Long): Flow<Long> = repository.workspaceChanges.filter { it == sessionId }

    suspend fun <T> run(sessionId: Long, block: suspend WorkspaceApi.() -> T): WorkspaceResult<T> {
        val api = repository.workspace(sessionId)
            ?: return WorkspaceResult.Failure(errorMessage(AcpException.NotConnected()))
        return try {
            WorkspaceResult.Success(api.block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: AcpException) {
            WorkspaceResult.Failure(errorMessage(e), (e as? AcpException.Rpc)?.error?.code)
        }
    }

    fun attachments(sessionId: Long): Flow<List<String>> =
        attachments.map { it[sessionId].orEmpty() }.distinctUntilChanged()

    fun attach(sessionId: Long, path: String): AttachResult {
        var result = AttachResult.Added
        attachments.update { all ->
            val current = all[sessionId].orEmpty()
            result = addAttachmentResult(current, path)
            if (result == AttachResult.Added) all + (sessionId to current + path) else all
        }
        return result
    }

    fun detach(sessionId: Long, path: String) {
        attachments.update { all -> all + (sessionId to all[sessionId].orEmpty().filterNot { it == path }) }
    }

    fun detachUnder(sessionId: Long, path: String) {
        attachments.update { all ->
            all + (sessionId to all[sessionId].orEmpty().filterNot { it == path || it.startsWith("$path/") })
        }
    }

    fun takeAttachments(sessionId: Long): List<String> {
        var taken = emptyList<String>()
        attachments.update { all ->
            taken = all[sessionId].orEmpty()
            all - sessionId
        }
        return taken
    }

    enum class AttachResult { Added, AlreadyAttached, LimitReached }

    companion object {
        const val MAX_ATTACHMENTS = 10

        fun addAttachmentResult(current: List<String>, path: String): AttachResult = when {
            path in current -> AttachResult.AlreadyAttached
            current.size >= MAX_ATTACHMENTS -> AttachResult.LimitReached
            else -> AttachResult.Added
        }

        fun errorMessage(e: AcpException): String = when (e) {
            is AcpException.Rpc -> when (e.error.code) {
                LakasirErrorCode.CONFLICT -> "The file changed on the bridge machine"
                LakasirErrorCode.OUTSIDE_WORKSPACE -> "That path is outside the workspace"
                LakasirErrorCode.NOT_FOUND -> "Not found. It may have been moved or deleted."
                LakasirErrorCode.ALREADY_EXISTS -> "A file or folder with that name already exists"
                JsonRpcErrorCode.METHOD_NOT_FOUND -> "This bridge doesn't support file access"
                else -> e.error.message
            }
            is AcpException.NotConnected, is AcpException.Disconnected -> "Not connected to the bridge"
            is AcpException.Timeout -> "The bridge didn't answer in time"
            is AcpException.InvalidResponse -> "Unexpected answer from the bridge"
        }
    }
}
