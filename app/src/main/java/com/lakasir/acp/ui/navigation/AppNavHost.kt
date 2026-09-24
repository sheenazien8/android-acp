package com.lakasir.acp.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.lakasir.acp.ui.chat.ChatPlaceholder
import com.lakasir.acp.ui.chat.ChatScreen
import com.lakasir.acp.ui.connection.ConnectionScreen
import com.lakasir.acp.ui.permission.PermissionHost
import com.lakasir.acp.ui.sessions.SessionsScreen
import com.lakasir.acp.ui.workspace.FileViewerScreen
import com.lakasir.acp.ui.workspace.git.GitCommitScreen
import com.lakasir.acp.ui.workspace.git.GitDiffScreen

@Composable
fun AppNavHost(widthSizeClass: WindowWidthSizeClass) {
    val navController = rememberNavController()
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded
    Surface(color = MaterialTheme.colorScheme.background) {
        NavHost(navController = navController, startDestination = Connections) {
            composable<Connections> {
                ConnectionScreen(onOpenProfile = { navController.navigate(Sessions(it)) { launchSingleTop = true } })
            }
            composable<Sessions> {
                SessionsScreen(
                    isExpanded = isExpanded,
                    onBack = { navController.popBackStack() },
                    onOpenSession = { navController.navigate(Chat(it)) { launchSingleTop = true } },
                    detailPane = { sessionId ->
                        if (sessionId == null) {
                            ChatPlaceholder()
                        } else {
                            ChatScreen(
                                sessionId = sessionId,
                                onBack = null,
                                onDeleted = {},
                                onOpenFile = { navController.navigate(FileViewer(sessionId, it)) },
                                onOpenDiff = { path, staged, origPath ->
                                    navController.navigate(GitDiffView(sessionId, path, staged, origPath))
                                },
                                onOpenCommit = { navController.navigate(GitCommitView(sessionId, it)) },
                            )
                        }
                    },
                )
            }
            composable<Chat> { entry ->
                val sessionId = entry.toRoute<Chat>().sessionId
                ChatScreen(
                    sessionId = sessionId,
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                    onOpenFile = { navController.navigate(FileViewer(sessionId, it)) },
                    onOpenDiff = { path, staged, origPath -> navController.navigate(GitDiffView(sessionId, path, staged, origPath)) },
                    onOpenCommit = { navController.navigate(GitCommitView(sessionId, it)) },
                )
            }
            composable<FileViewer> { entry ->
                val route = entry.toRoute<FileViewer>()
                FileViewerScreen(sessionId = route.sessionId, path = route.path, onBack = { navController.popBackStack() })
            }
            composable<GitDiffView> { entry ->
                val route = entry.toRoute<GitDiffView>()
                GitDiffScreen(route.sessionId, route.path, route.staged, route.origPath, onBack = { navController.popBackStack() })
            }
            composable<GitCommitView> { entry ->
                val route = entry.toRoute<GitCommitView>()
                GitCommitScreen(route.sessionId, route.hash, onBack = { navController.popBackStack() })
            }
        }
        PermissionHost()
    }
}
