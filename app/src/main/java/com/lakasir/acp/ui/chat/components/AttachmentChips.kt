package com.lakasir.acp.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lakasir.acp.ui.theme.AcpTheme

@Composable
fun AttachmentInputChips(paths: List<String>, onRemove: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(paths, key = { it }) { path ->
            InputChip(
                selected = false,
                onClick = { onRemove(path) },
                label = { Text(path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp)) },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.semantics { contentDescription = "Remove attachment $path" },
            )
        }
    }
}

@Composable
fun AttachmentList(paths: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        paths.forEach { path ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = "Attached file",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    path,
                    style = AcpTheme.code.codeSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
