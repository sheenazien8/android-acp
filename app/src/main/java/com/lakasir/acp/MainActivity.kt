package com.lakasir.acp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lakasir.acp.ui.navigation.AppNavHost
import com.lakasir.acp.ui.theme.AcpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AcpTheme {
                AppNavHost()
            }
        }
    }
}
