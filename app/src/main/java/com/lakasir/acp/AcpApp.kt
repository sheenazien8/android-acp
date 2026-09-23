package com.lakasir.acp

import android.app.Application
import com.lakasir.acp.di.AppContainer
import com.lakasir.acp.service.AcpConnectionService
import com.lakasir.acp.service.ConnectionNotifications
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AcpApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ConnectionNotifications(this).createChannels()
        container.appScope.launch {
            container.repository.activeProfileIds
                .map { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { active ->
                    if (active) AcpConnectionService.start(this@AcpApp) else AcpConnectionService.stop(this@AcpApp)
                }
        }
    }
}
