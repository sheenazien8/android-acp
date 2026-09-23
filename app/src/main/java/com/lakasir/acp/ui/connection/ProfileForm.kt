package com.lakasir.acp.ui.connection

import com.lakasir.acp.data.local.ConnectionProfileEntity

data class ProfileForm(
    val id: Long = 0,
    val name: String = "",
    val host: String = "",
    val port: String = "8080",
    val cwd: String = "",
    val createdAt: Long = 0,
    val lastConnectedAt: Long? = null,
    val hostError: String? = null,
    val portError: String? = null,
    val cwdError: String? = null,
) {
    val isNew: Boolean get() = id == 0L

    fun validate(): ProfileForm {
        val trimmedHost = host.trim()
        val portNumber = port.trim().toIntOrNull()
        return copy(
            hostError = when {
                trimmedHost.isEmpty() -> "Host is required"
                trimmedHost.contains("://") || trimmedHost.contains(' ') -> "Enter a bare IP or hostname"
                else -> null
            },
            portError = if (portNumber == null || portNumber !in 1..65535) "Port must be 1–65535" else null,
            cwdError = if (!isAbsolutePath(cwd.trim())) "Absolute path on the bridge machine" else null,
        )
    }

    val isValid: Boolean get() = hostError == null && portError == null && cwdError == null

    fun toEntity(): ConnectionProfileEntity = ConnectionProfileEntity(
        id = id,
        name = name.trim().ifEmpty { host.trim() },
        host = host.trim(),
        port = port.trim().toInt(),
        cwd = cwd.trim(),
        createdAt = createdAt,
        lastConnectedAt = lastConnectedAt,
    )

    companion object {
        fun from(profile: ConnectionProfileEntity) = ProfileForm(
            id = profile.id,
            name = profile.name,
            host = profile.host,
            port = profile.port.toString(),
            cwd = profile.cwd,
            createdAt = profile.createdAt,
            lastConnectedAt = profile.lastConnectedAt,
        )

        private val windowsPath = Regex("^[A-Za-z]:[\\\\/].*")

        fun isAbsolutePath(path: String): Boolean = path.startsWith("/") || windowsPath.matches(path)
    }
}
