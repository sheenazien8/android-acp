package com.lakasir.acp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.lakasir.acp.ui.connection.ConnectionScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Connections) {
        composable<Connections> {
            ConnectionScreen(onOpenProfile = { navController.navigate(Sessions(it)) { launchSingleTop = true } })
        }
        composable<Sessions> { entry ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sessions for profile ${entry.toRoute<Sessions>().profileId}")
            }
        }
    }
}
