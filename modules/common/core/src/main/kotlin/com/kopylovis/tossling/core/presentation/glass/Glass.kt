package com.kopylovis.tossling.core.presentation.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kopylovis.tossling.core.presentation.theme.Tossling
import com.kopylovis.tossling.core.presentation.theme.TosslingPalette
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun GlassScreen(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        TosslingBackground()
        CompositionLocalProvider(LocalHazeState provides null) {
            content()
        }
    }
}

@Composable
fun TosslingBackground(modifier: Modifier = Modifier) {
    val palette = Tossling.palette
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        Blob(color = palette.blob1, size = 420f.dp, radius = 60f.dp, alignment = Alignment.TopStart, offset = DpOffset((-140f).dp, (-60f).dp))
        Blob(color = palette.blob2, size = 380f.dp, radius = 70f.dp, alignment = Alignment.TopEnd, offset = DpOffset(160f.dp, 260f.dp))
        Blob(color = palette.blob3, size = 420f.dp, radius = 70f.dp, alignment = Alignment.BottomStart, offset = DpOffset((-80f).dp, 120f.dp))
    }
}

@Composable
private fun BoxScope.Blob(
    color: Color,
    size: Dp,
    radius: Dp,
    alignment: Alignment,
    offset: DpOffset,
) {
    val outer = size / 2 + radius
    val core = ((size / 2 - radius) / outer).coerceAtLeast(0f)
    val edge = (size / 2) / outer
    Box(
        modifier = Modifier
            .align(alignment)
            .offset(x = offset.x - radius, y = offset.y - radius)
            .size(size + radius * 2)
            .background(
                brush = Brush.radialGradient(
                    0f to color,
                    core to color,
                    edge to color.copy(alpha = color.alpha * 0.5f),
                    1f to color.copy(alpha = 0f),
                ),
            ),
    )
}

enum class GlassLevel { FILL, STRONG, WEAK, HEADER }

@Composable
fun Modifier.glass(
    shape: Shape,
    level: GlassLevel = GlassLevel.FILL,
    blur: Dp? = null,
    withShadow: Boolean = true,
    withStroke: Boolean = true,
): Modifier {
    val palette = Tossling.palette
    val state = LocalHazeState.current
    val fill = palette.fillFor(level = level)
    val shadowed = if (withShadow) shadow(elevation = GLASS_ELEVATION, shape = shape, ambientColor = palette.shadow, spotColor = palette.shadow) else this
    val clipped = shadowed.clip(shape)
    val surface = if (state == null) {
        clipped.background(color = fill)
    } else {
        clipped.hazeEffect(
            state = state,
            style = HazeStyle(
                backgroundColor = palette.background,
                tint = HazeTint(color = fill),
                blurRadius = blur ?: palette.glassBlur,
                noiseFactor = 0f,
            ),
        ) {
            inputScale = HazeInputScale.Auto
        }
    }
    val stroked = if (withStroke) surface.border(width = 1f.dp, color = palette.glassStroke, shape = shape) else surface
    return stroked.drawWithContent {
        drawContent()
        drawLine(
            color = Color.White.copy(alpha = palette.highlightAlpha),
            start = Offset(size.height.coerceAtMost(28f.dp.toPx()), 0.5f),
            end = Offset(size.width - size.height.coerceAtMost(28f.dp.toPx()), 0.5f),
            strokeWidth = 1f.dp.toPx(),
        )
    }
}

@Composable
fun Modifier.glassLite(shape: Shape, withShadow: Boolean = true): Modifier {
    val palette = Tossling.palette
    val shadowed = if (withShadow) shadow(elevation = 6f.dp, shape = shape, ambientColor = palette.shadow, spotColor = palette.shadow) else this
    return shadowed
        .background(color = palette.glassLite, shape = shape)
        .border(width = 1f.dp, color = palette.glassStroke, shape = shape)
        .drawWithContent {
            drawContent()
            drawLine(
                color = Color.White.copy(alpha = palette.highlightAlpha),
                start = Offset(size.height.coerceAtMost(28f.dp.toPx()), 0.5f),
                end = Offset(size.width - size.height.coerceAtMost(28f.dp.toPx()), 0.5f),
                strokeWidth = 1f.dp.toPx(),
            )
        }
}

fun TosslingPalette.fillFor(level: GlassLevel): Color = when (level) {
    GlassLevel.FILL -> glassFill
    GlassLevel.STRONG -> glassStrong
    GlassLevel.WEAK -> glassWeak
    GlassLevel.HEADER -> header
}

@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    pressedScale: Float = 0.95f,
    enabled: Boolean = true,
    role: Role = Role.Button,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = PRESS_DAMPING, stiffness = PRESS_STIFFNESS),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28f.dp),
    level: GlassLevel = GlassLevel.FILL,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.glass(shape = shape, level = level), content = content)
}

private val GLASS_ELEVATION = 10f.dp
private const val PRESS_DAMPING = 0.55f
private const val PRESS_STIFFNESS = 600f
val SpringBouncy = spring<Float>(dampingRatio = PRESS_DAMPING, stiffness = Spring.StiffnessMediumLow)
