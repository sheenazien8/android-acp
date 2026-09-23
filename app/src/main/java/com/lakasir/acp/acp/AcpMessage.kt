package com.lakasir.acp.acp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

val AcpJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

data class RpcError(val code: Int, val message: String, val data: JsonElement? = null)

class AcpParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

sealed interface AcpMessage {
    data class Request(val id: JsonElement, val method: String, val params: JsonElement?) : AcpMessage
    data class Response(val id: JsonElement, val result: JsonElement) : AcpMessage
    data class ErrorResponse(val id: JsonElement?, val error: RpcError) : AcpMessage
    data class Notification(val method: String, val params: JsonElement?) : AcpMessage

    fun encode(): String = AcpJson.encodeToString(JsonObject.serializer(), toJson())

    fun toJson(): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        when (this@AcpMessage) {
            is Request -> {
                put("id", id)
                put("method", method)
                params?.let { put("params", it) }
            }
            is Response -> {
                put("id", id)
                put("result", result)
            }
            is ErrorResponse -> {
                put("id", id ?: JsonNull)
                put("error", buildJsonObject {
                    put("code", error.code)
                    put("message", error.message)
                    error.data?.let { put("data", it) }
                })
            }
            is Notification -> {
                put("method", method)
                params?.let { put("params", it) }
            }
        }
    }

    companion object {
        fun parse(text: String): AcpMessage {
            val root = try {
                AcpJson.parseToJsonElement(text)
            } catch (e: Exception) {
                throw AcpParseException("Invalid JSON: ${e.message}", e)
            }
            if (root !is JsonObject) throw AcpParseException("JSON-RPC message must be an object")

            val id = root["id"]?.takeUnless { it is JsonNull }
            val method = (root["method"] as? JsonPrimitive)?.contentOrNull
            val error = root["error"]

            return when {
                method != null && id != null -> Request(id, method, root["params"])
                method != null -> Notification(method, root["params"])
                error is JsonObject -> ErrorResponse(id, parseError(error))
                id != null && "result" in root -> Response(id, root["result"] ?: JsonNull)
                else -> throw AcpParseException("Unrecognized JSON-RPC message")
            }
        }

        private fun parseError(error: JsonObject): RpcError = RpcError(
            code = error["code"]?.jsonPrimitive?.intOrNull ?: 0,
            message = (error["message"] as? JsonPrimitive)?.contentOrNull ?: "Unknown error",
            data = error["data"],
        )
    }
}
