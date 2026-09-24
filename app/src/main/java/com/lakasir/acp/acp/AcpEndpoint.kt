package com.lakasir.acp.acp

import com.lakasir.acp.data.local.ConnectionProfileEntity
import com.lakasir.acp.data.local.ConnectionScheme
import okhttp3.HttpUrl

class AcpEndpoint private constructor(
    val scheme: String,
    val host: String,
    val port: Int,
    val path: String,
    private val token: String?,
    val allowInsecureTls: Boolean,
) {
    val isSecure: Boolean get() = scheme == ConnectionScheme.WSS

    val url: String = run {
        val validated = HttpUrl.Builder()
            .scheme(if (isSecure) "https" else "http")
            .host(host)
            .port(port)
            .encodedPath(path)
            .build()
        val hostPart = if (':' in validated.host) "[${validated.host}]" else validated.host
        "$scheme://$hostPart:$port${validated.encodedPath}"
    }

    val headers: Map<String, String> =
        token?.let { mapOf(AUTHORIZATION to "Bearer $it") }.orEmpty()

    val hasToken: Boolean get() = token != null

    override fun toString(): String = "AcpEndpoint($url, token=${if (hasToken) "***" else "none"}, insecureTls=$allowInsecureTls)"

    override fun equals(other: Any?): Boolean =
        other is AcpEndpoint && other.url == url && other.token == token && other.allowInsecureTls == allowInsecureTls

    override fun hashCode(): Int = url.hashCode()

    companion object {
        const val AUTHORIZATION = "Authorization"

        fun of(
            host: String,
            port: Int,
            scheme: String = ConnectionScheme.WS,
            path: String = AcpMethods.ENDPOINT_PATH,
            token: String? = null,
            allowInsecureTls: Boolean = false,
        ): AcpEndpoint = AcpEndpoint(
            scheme = if (scheme == ConnectionScheme.WSS) ConnectionScheme.WSS else ConnectionScheme.WS,
            host = host.trim().removePrefix("[").removeSuffix("]"),
            port = port,
            path = normalizePath(path),
            token = token?.trim()?.takeIf { it.isNotEmpty() },
            allowInsecureTls = allowInsecureTls && scheme == ConnectionScheme.WSS,
        )

        fun from(profile: ConnectionProfileEntity): AcpEndpoint = of(
            host = profile.host,
            port = profile.port,
            scheme = profile.scheme,
            path = profile.path,
            token = profile.authToken,
            allowInsecureTls = profile.allowInsecureTls,
        )

        fun normalizePath(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return AcpMethods.ENDPOINT_PATH
            val withSlash = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
            return if (withSlash.length > 1) withSlash.trimEnd('/').ifEmpty { "/" } else withSlash
        }

        fun isValid(host: String, port: Int, path: String): Boolean =
            runCatching { of(host, port, path = path).url }.isSuccess
    }
}
