package com.lakasir.acp.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object Connections

@Serializable
data class Sessions(val profileId: Long)

@Serializable
data class Chat(val sessionId: Long)

@Serializable
data class FileViewer(val sessionId: Long, val path: String)

@Serializable
data class GitDiffView(val sessionId: Long, val path: String, val staged: Boolean, val origPath: String? = null)

@Serializable
data class GitCommitView(val sessionId: Long, val hash: String)
