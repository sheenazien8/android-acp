package com.lakasir.acp.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lakasir.acp.data.local.ConnectionProfileEntity
import com.lakasir.acp.data.repository.ConnectionState
import com.lakasir.acp.ui.components.ConnectionStatusIndicator
import com.lakasir.acp.ui.components.leftBorder
import com.lakasir.acp.ui.theme.AcpTheme

@Composable
fun ConnectionSwitcher(
    profiles: List<ConnectionProfileEntity>,
    connectionStates: Map<Long, ConnectionState>,
    currentProfileId: Long,
    onSelect: (Long) -> Unit,
    onManage: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Connections",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 12.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        LazyColumn(Modifier.weight(1f)) {
            items(profiles, key = { it.id }) { profile ->
                val isCurrent = profile.id == currentProfileId
                ListItem(
                    modifier = Modifier
                        .clickable { onSelect(profile.id) }
                        .semantics { selected = isCurrent }
                        .leftBorder(if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent),
                    colors = ListItemDefaults.colors(
                        containerColor = if (isCurrent) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
                    ),
                    headlineContent = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            "${profile.host}:${profile.port}",
                            style = AcpTheme.code.codeSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        ConnectionStatusIndicator(connectionStates[profile.id] ?: ConnectionState.Disconnected, profileId = profile.id)
                    },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        ListItem(
            modifier = Modifier.clickable(onClick = onManage),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            leadingContent = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            headlineContent = { Text("Manage connections") },
        )
    }
}
