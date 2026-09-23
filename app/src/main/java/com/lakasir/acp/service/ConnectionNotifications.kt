package com.lakasir.acp.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lakasir.acp.MainActivity
import com.lakasir.acp.R
import com.lakasir.acp.data.repository.CompletedTurn
import com.lakasir.acp.data.repository.ConnectionState
import com.lakasir.acp.data.repository.PendingPermission

class ConnectionNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_CONNECTION, "Active connections", NotificationManager.IMPORTANCE_LOW)
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_AGENT, "Agent activity", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun connection(summary: String, disconnectAll: PendingIntent): Notification =
        NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ACP connections active")
            .setContentText(summary)
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Disconnect all", disconnectAll)
            .build()

    fun notifyPermission(pending: PendingPermission) {
        val toolTitle = pending.request.toolCall.title ?: pending.request.toolCall.kind ?: "Tool call"
        notify(
            permissionId(pending.localSessionId),
            agentNotification("Permission needed · ${pending.sessionTitle}", toolTitle),
        )
    }

    fun cancelPermission(localSessionId: Long) {
        manager.cancel(permissionId(localSessionId))
    }

    fun notifyTurn(turn: CompletedTurn) {
        val text = turn.error ?: "Agent finished responding"
        notify(turnId(turn.localSessionId), agentNotification(turn.sessionTitle, text))
    }

    private fun agentNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_AGENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

    private fun notify(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.notify(id, notification)
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun permissionId(localSessionId: Long) = PERMISSION_ID_BASE + localSessionId.toInt()

    private fun turnId(localSessionId: Long) = TURN_ID_BASE + localSessionId.toInt()

    companion object {
        const val CONNECTION_ID = 1
        private const val PERMISSION_ID_BASE = 10_000
        private const val TURN_ID_BASE = 20_000_000
        private const val CHANNEL_CONNECTION = "connection"
        private const val CHANNEL_AGENT = "agent"

        fun summary(activeProfileIds: Set<Long>, states: Map<Long, ConnectionState>): String {
            val connected = activeProfileIds.count { states[it] is ConnectionState.Connected }
            val reconnecting = activeProfileIds.size - connected
            return when {
                reconnecting == 0 -> "$connected connected"
                connected == 0 -> "$reconnecting reconnecting"
                else -> "$connected connected · $reconnecting reconnecting"
            }
        }
    }
}
