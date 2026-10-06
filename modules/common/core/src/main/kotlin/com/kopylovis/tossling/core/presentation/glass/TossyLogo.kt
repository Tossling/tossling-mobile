package com.kopylovis.tossling.core.presentation.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LogoTop = Color(0xFFD0CAFD)
private val LogoBottom = Color(0xFF9BD4F0)
private val LogoPeach = Color(0xFFFFD4B0)
private val LogoBall = Color(0xFF3067B8)

@Composable
fun TossyLogo(
    modifier: Modifier = Modifier,
    size: Dp = 112f.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.width / VIEWPORT
        scale(scale = unit, pivot = Offset.Zero) {
            drawRect(brush = Brush.linearGradient(colors = listOf(LogoTop, LogoBottom), start = Offset.Zero, end = Offset(VIEWPORT, VIEWPORT)), size = Size(VIEWPORT, VIEWPORT))
            drawCircle(color = LogoPeach.copy(alpha = 0.85f), radius = 30f, center = Offset(84f, 88f))
            drawRoundRect(color = Color.White.copy(alpha = 0.55f), topLeft = Offset(26f, 26f), size = Size(56f, 56f), cornerRadius = CornerRadius(18f))
            drawRoundRect(color = Color.White.copy(alpha = 0.95f), topLeft = Offset(26f, 26f), size = Size(56f, 56f), cornerRadius = CornerRadius(18f), style = Stroke(width = 1.5f))
            val arc = Path().apply {
                moveTo(38f, 68f)
                quadraticTo(50f, 32f, 68f, 46f)
            }
            drawPath(
                path = arc,
                color = LogoBall,
                style = Stroke(width = 3.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 7f))),
            )
            drawCircle(color = LogoBall, radius = 7f, center = Offset(70f, 44f))
        }
    }
}

private const val VIEWPORT = 108f
