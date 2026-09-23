package com.lakasir.acp

import android.app.Application
import com.lakasir.acp.di.AppContainer

class AcpApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
