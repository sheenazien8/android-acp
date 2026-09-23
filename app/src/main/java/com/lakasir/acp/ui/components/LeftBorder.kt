package com.lakasir.acp.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.leftBorder(color: Color, width: Dp = 2.dp): Modifier = drawBehind {
    drawRect(color = color, topLeft = Offset.Zero, size = Size(width.toPx(), size.height))
}
