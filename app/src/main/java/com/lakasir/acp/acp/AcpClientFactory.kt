package com.lakasir.acp.acp

import kotlinx.coroutines.CoroutineScope

fun interface AcpClientFactory {
    fun create(scope: CoroutineScope): AcpClient
}

class DefaultAcpClientFactory : AcpClientFactory {
    override fun create(scope: CoroutineScope): AcpClient =
        AcpClient(OkHttpAcpTransport(), scope)
}
