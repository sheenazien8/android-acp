package com.lakasir.acp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.lakasir.acp.ui.connection.ConnectionScreen
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
                    detailPane = { sessionId -> ChatPlaceholder(sessionId) },
                )
            }
            composable<Chat> { entry ->
                ChatPlaceholder(entry.toRoute<Chat>().sessionId)
            }
        }
    }
}

@Composable
private fun ChatPlaceholder(sessionId: Long?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(if (sessionId == null) "Select a session" else "Chat $sessionId")
    }
}
