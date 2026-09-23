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
                        if (sessionId == null) ChatPlaceholder() else ChatScreen(sessionId = sessionId, onBack = null)
                    },
                )
            }
            composable<Chat> { entry ->
                ChatScreen(sessionId = entry.toRoute<Chat>().sessionId, onBack = { navController.popBackStack() })
            }
        }
        PermissionHost()
    }
}
