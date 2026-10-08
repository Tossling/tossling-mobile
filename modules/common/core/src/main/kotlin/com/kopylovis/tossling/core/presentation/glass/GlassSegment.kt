package com.kopylovis.tossling.core.presentation.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.kopylovis.tossling.core.presentation.theme.Tossling

@Composable
fun Modifier.glassSegment(isFirst: Boolean, isLast: Boolean): Modifier {
    val palette = Tossling.palette
    val radius = SEGMENT_RADIUS
    val shape = RoundedCornerShape(
        topStart = if (isFirst) radius else 0f.dp,
        topEnd = if (isFirst) radius else 0f.dp,
        bottomStart = if (isLast) radius else 0f.dp,
        bottomEnd = if (isLast) radius else 0f.dp,
    )
    return drawBehind {
        val corner = radius.toPx()
        val reach = corner * 2f
        val pad = SHADOW_BLUR.toPx() * 2f
        val group = RoundRect(
            left = 0f,
            top = if (isFirst) 0f else -reach,
            right = size.width,
            bottom = if (isLast) size.height else size.height + reach,
            cornerRadius = CornerRadius(corner),
        )
        val outline = Path().apply { addRoundRect(group) }
        clipRect(left = -pad, top = if (isFirst) -pad else 0f, right = size.width + pad, bottom = if (isLast) size.height + pad else size.height) {
            clipPath(path = outline, clipOp = ClipOp.Difference) {
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply { color = Color.Black }
                    paint.asFrameworkPaint().setShadowLayer(SHADOW_BLUR.toPx(), 0f, SHADOW_DROP.toPx(), palette.shadow.toArgb())
                    canvas.drawRoundRect(group.left, group.top, group.right, group.bottom, corner, corner, paint)
                }
            }
        }
        clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
            drawPath(path = outline, color = palette.glassFill)
            drawPath(path = outline, color = palette.glassStroke, style = Stroke(width = 1f.dp.toPx()))
        }
        if (isFirst) {
            drawLine(
                color = Color.White.copy(alpha = palette.highlightAlpha),
                start = Offset(corner, 0.5f),
                end = Offset(size.width - corner, 0.5f),
                strokeWidth = 1f.dp.toPx(),
            )
        }
    }.clip(shape)
}

private val SEGMENT_RADIUS = 28f.dp
private val SHADOW_BLUR = 10f.dp
private val SHADOW_DROP = 4f.dp
