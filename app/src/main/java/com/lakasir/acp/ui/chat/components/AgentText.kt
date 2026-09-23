package com.lakasir.acp.ui.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lakasir.acp.ui.chat.TextSegment
import com.lakasir.acp.ui.chat.TextSegments

@Composable
fun AgentText(text: String, streaming: Boolean, modifier: Modifier = Modifier) {
    val segments = remember(text) { TextSegments.split(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEachIndexed { index, segment ->
            val isLast = index == segments.lastIndex
            when (segment) {
                is TextSegment.Prose -> SelectionContainer {
                    if (streaming && isLast) {
                        StreamingText(segment.text, MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurface)
                    } else {
                        Text(segment.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                is TextSegment.Code -> CodeBox(segment.text, caption = segment.language)
            }
        }
    }
}

@Composable
private fun StreamingText(text: String, style: TextStyle, color: Color) {
    var settledLength by remember { mutableIntStateOf(text.length) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(text) {
        if (text.length > settledLength) {
            alpha.snapTo(0.2f)
            alpha.animateTo(1f, tween(durationMillis = 220))
        }
        settledLength = text.length
    }
    val settled = settledLength.coerceAtMost(text.length)
    val annotated = buildAnnotatedString {
        append(text.substring(0, settled))
        withStyle(SpanStyle(color = color.copy(alpha = alpha.value))) { append(text.substring(settled)) }
    }
    Text(annotated, style = style, color = color)
}
