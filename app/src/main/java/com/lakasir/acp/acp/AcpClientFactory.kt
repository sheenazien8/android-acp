package com.lakasir.acp.acp

import android.annotation.SuppressLint
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

interface AcpClientFactory {
    fun create(scope: CoroutineScope, allowInsecureTls: Boolean = false): AcpClient
}

class DefaultAcpClientFactory : AcpClientFactory {
    private val secureClient: OkHttpClient by lazy { OkHttpAcpTransport.defaultClient() }
    private val insecureClient: OkHttpClient by lazy { trustAllClient(secureClient) }

    override fun create(scope: CoroutineScope, allowInsecureTls: Boolean): AcpClient =
        AcpClient(OkHttpAcpTransport(if (allowInsecureTls) insecureClient else secureClient), scope)

    companion object {
        @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier")
        private fun trustAllClient(base: OkHttpClient): OkHttpClient {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
            return base.newBuilder()
                .sslSocketFactory(context.socketFactory, trustAll)
                .hostnameVerifier { _, _ -> true }
                .build()
        }
    }
}
