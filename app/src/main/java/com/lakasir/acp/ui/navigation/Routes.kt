package com.lakasir.acp.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object Connections

@Serializable
data class Sessions(val profileId: Long)

@Serializable
data class Chat(val sessionId: Long)
