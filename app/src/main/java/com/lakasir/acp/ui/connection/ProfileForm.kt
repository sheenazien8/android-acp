package com.lakasir.acp.ui.connection

import com.lakasir.acp.acp.AcpEndpoint
import com.lakasir.acp.data.local.ConnectionProfileEntity
import com.lakasir.acp.data.local.ConnectionScheme

data class ProfileForm(
    val id: Long = 0,
    val name: String = "",
    val scheme: String = ConnectionScheme.WS,
    val host: String = "",
    val port: String = "8080",
    val path: String = ConnectionProfileEntity.DEFAULT_PATH,
    val token: String = "",
    val savedToken: String? = null,
    val allowInsecureTls: Boolean = false,
    val cwd: String = "",
    val createdAt: Long = 0,
    val lastConnectedAt: Long? = null,
    val hostError: String? = null,
    val portError: String? = null,
    val pathError: String? = null,
    val cwdError: String? = null,
) {
    val isNew: Boolean get() = id == 0L
    val isSecure: Boolean get() = scheme == ConnectionScheme.WSS
    val hasSavedToken: Boolean get() = savedToken != null

    fun validate(): ProfileForm {
        val trimmedHost = host.trim()
        val portNumber = port.trim().toIntOrNull()
        val hostError = when {
            trimmedHost.isEmpty() -> "Host is required"
            trimmedHost.contains("://") || trimmedHost.contains(' ') || trimmedHost.contains('/') -> "Enter a bare IP or hostname"
            portNumber != null && !AcpEndpoint.isValid(trimmedHost, portNumber, "/") -> "Not a valid IP or hostname"
            else -> null
        }
        val trimmedPath = path.trim()
        return copy(
            hostError = hostError,
            portError = if (portNumber == null || portNumber !in 1..65535) "Port must be 1–65535" else null,
            pathError = if (trimmedPath.any { it.isWhitespace() || it == '?' || it == '#' }) "Path can't contain spaces, ? or #" else null,
            cwdError = if (!isAbsolutePath(cwd.trim())) "Absolute path on the bridge machine" else null,
        )
    }

    val isValid: Boolean get() = hostError == null && portError == null && pathError == null && cwdError == null

    fun toEntity(): ConnectionProfileEntity = ConnectionProfileEntity(
        id = id,
        name = name.trim().ifEmpty { host.trim() },
        host = host.trim(),
        port = port.trim().toInt(),
        cwd = cwd.trim(),
        createdAt = createdAt,
        lastConnectedAt = lastConnectedAt,
        scheme = scheme,
        path = AcpEndpoint.normalizePath(path),
        authToken = token.trim().ifEmpty { null } ?: savedToken,
        allowInsecureTls = allowInsecureTls && isSecure,
    )

    override fun toString(): String =
        "ProfileForm(id=$id, name=$name, $scheme://$host:$port$path, token=${if (token.isNotBlank() || hasSavedToken) "***" else "none"})"

    companion object {
        fun from(profile: ConnectionProfileEntity) = ProfileForm(
            id = profile.id,
            name = profile.name,
            scheme = profile.scheme,
            host = profile.host,
            port = profile.port.toString(),
            path = profile.path,
            savedToken = profile.authToken?.takeIf { it.isNotEmpty() },
            allowInsecureTls = profile.allowInsecureTls,
            cwd = profile.cwd,
            createdAt = profile.createdAt,
            lastConnectedAt = profile.lastConnectedAt,
        )

        private val windowsPath = Regex("^[A-Za-z]:[\\\\/].*")

        fun isAbsolutePath(path: String): Boolean = path.startsWith("/") || windowsPath.matches(path)
    }
}
