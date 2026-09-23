package com.lakasir.acp.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
private fun Swatch(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(20.dp).background(color, MaterialTheme.shapes.small))
        Text(name, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TokenSheet() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Design tokens", style = MaterialTheme.typography.titleMedium)
            Swatch("primary", MaterialTheme.colorScheme.primary)
            Swatch("surfaceVariant", MaterialTheme.colorScheme.surfaceVariant)
            Swatch("onSurfaceVariant", MaterialTheme.colorScheme.onSurfaceVariant)
            Swatch("outline", MaterialTheme.colorScheme.outline)
            Swatch("diffAdded", AcpTheme.extended.diffAdded)
            Swatch("diffRemoved", AcpTheme.extended.diffRemoved)
            Text("Agent prose uses the sans family.", style = MaterialTheme.typography.bodyMedium)
            Text("val code = \"monospace\"", style = AcpTheme.code.codeMedium)
        }
    }
}

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TokenSheetDarkPreview() {
    AcpTheme(darkTheme = true) { TokenSheet() }
}

@Preview(name = "Light")
@Composable
private fun TokenSheetLightPreview() {
    AcpTheme(darkTheme = false) { TokenSheet() }
}
