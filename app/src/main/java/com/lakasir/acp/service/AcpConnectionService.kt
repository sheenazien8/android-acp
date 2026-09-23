package com.lakasir.acp.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.lakasir.acp.AcpApp
import com.lakasir.acp.data.repository.PendingPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AcpConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repository by lazy { (application as AcpApp).container.repository }
    private val notifications by lazy { ConnectionNotifications(this) }
    private var summary = ""

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            repository.retryNow()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
        scope.launch {
            combine(repository.activeProfileIds, repository.connectionStates, ConnectionNotifications::summary)
                .collect {
                    summary = it
                    startInForeground()
                }
        }
        scope.launch {
            var previous = emptyList<PendingPermission>()
            repository.pendingPermissions.collect { current ->
                if (!isAppVisible()) (current - previous.toSet()).forEach(notifications::notifyPermission)
                val waiting = current.map { it.localSessionId }.toSet()
                previous.map { it.localSessionId }.filterNot { it in waiting }.forEach(notifications::cancelPermission)
                previous = current
            }
        }
        scope.launch {
            repository.completedTurns.collect { if (!isAppVisible()) notifications.notifyTurn(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (intent?.action == ACTION_DISCONNECT_ALL) repository.disconnect()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            ConnectionNotifications.CONNECTION_ID,
            notifications.connection(summary, disconnectAllIntent()),
            type,
        )
    }

    private fun disconnectAllIntent(): PendingIntent = PendingIntent.getService(
        this,
        0,
        Intent(this, AcpConnectionService::class.java).setAction(ACTION_DISCONNECT_ALL),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun isAppVisible(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    companion object {
        private const val ACTION_DISCONNECT_ALL = "com.lakasir.acp.action.DISCONNECT_ALL"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AcpConnectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AcpConnectionService::class.java))
        }
    }
}
