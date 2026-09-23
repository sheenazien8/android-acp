package com.lakasir.acp.di

import android.content.Context
import com.lakasir.acp.acp.AcpClient
import com.lakasir.acp.acp.OkHttpAcpTransport
import com.lakasir.acp.data.local.AppDatabase
import com.lakasir.acp.data.repository.AcpRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val acpClient: AcpClient by lazy { AcpClient(OkHttpAcpTransport(), appScope) }
    val repository: AcpRepository by lazy { AcpRepository(database, acpClient, appScope) }
}
