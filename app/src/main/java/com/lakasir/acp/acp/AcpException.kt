package com.lakasir.acp.acp

sealed class AcpException(message: String) : Exception(message) {
    class NotConnected : AcpException("Not connected to bridge")
    class Disconnected : AcpException("Connection lost")
    class Timeout(val method: String) : AcpException("Request '$method' timed out")
    class Rpc(val error: RpcError) : AcpException("${error.message} (${error.code})")
    class InvalidResponse(detail: String) : AcpException("Invalid response: $detail")
}
