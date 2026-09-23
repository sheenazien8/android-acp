package com.lakasir.acp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.lakasir.acp.data.repository.ConnectionState
import com.lakasir.acp.ui.theme.AcpTheme

@Composable
fun ConnectionStatusIndicator(
    state: ConnectionState,
    modifier: Modifier = Modifier,
    profileId: Long? = null,
) {
    val scoped = if (profileId != null && state.profileId != profileId) ConnectionState.Disconnected else state
    val (color, label) = statusAppearance(scoped)
    Row(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .clearAndSetSemantics { contentDescription = "Connection status: $label" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun statusAppearance(state: ConnectionState): Pair<Color, String> = when (state) {
    is ConnectionState.Connected -> MaterialTheme.colorScheme.primary to "Connected"
    is ConnectionState.Connecting -> AcpTheme.extended.warning to "Connecting"
    is ConnectionState.Error -> MaterialTheme.colorScheme.error to "Retrying"
    ConnectionState.Disconnected -> MaterialTheme.colorScheme.outline to "Offline"
}
