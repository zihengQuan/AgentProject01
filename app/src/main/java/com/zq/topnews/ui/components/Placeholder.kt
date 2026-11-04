package com.zq.topnews.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Gradients used in place of remote images, so the demo works fully offline.
 * Each tone is a pair of related colours picked to feel like a news photo.
 */
private val tones: List<List<Color>> = listOf(
    listOf(Color(0xFF1B5E20), Color(0xFF66BB6A)),
    listOf(Color(0xFF0D47A1), Color(0xFF42A5F5)),
    listOf(Color(0xFF4A148C), Color(0xFFAB47BC)),
    listOf(Color(0xFFB71C1C), Color(0xFFEF5350)),
    listOf(Color(0xFFE65100), Color(0xFFFFA726)),
    listOf(Color(0xFF004D40), Color(0xFF26A69A)),
    listOf(Color(0xFF283593), Color(0xFF7986CB)),
    listOf(Color(0xFF37474F), Color(0xFF90A4AE)),
)

fun toneBrush(tone: Int): Brush {
    val colors = tones[tone.mod(tones.size)]
    return Brush.linearGradient(colors)
}

/**
 * A picture placeholder: a soft gradient with two translucent circles and an
 * optional label in the bottom-start corner.
 */
@Composable
fun ToneBox(
    tone: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    cornerRadius: Int = 8,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(toneBrush(tone)),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 14.dp, y = (-18).dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = 0.12f))
                .padding(34.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-10).dp, y = 14.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(26.dp),
        )
        if (label != null) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.32f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        content()
    }
}
