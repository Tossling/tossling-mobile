package com.kopylovis.tossling.core.presentation.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kopylovis.tossling.core.presentation.theme.Tossling

enum class CapsuleStyle { PRIMARY, SUCCESS, GLASS, DANGER, QUIET, PLAIN }

@Composable
fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CapsuleStyle = CapsuleStyle.PRIMARY,
    icon: ImageVector? = null,
    iconSize: Dp = 20f.dp,
    enabled: Boolean = true,
    height: Dp = 56f.dp,
    textColor: Color? = null,
) {
    val palette = Tossling.palette
    val shape = RoundedCornerShape(percent = 50)
    val (background, tint) = when (style) {
        CapsuleStyle.PRIMARY -> palette.accent to palette.onAccent
        CapsuleStyle.SUCCESS -> palette.success to palette.onSuccess
        CapsuleStyle.GLASS -> palette.glassWeak to palette.ink
        CapsuleStyle.DANGER -> palette.danger to palette.onDanger
        CapsuleStyle.QUIET -> palette.glassWeak to palette.ink2
        CapsuleStyle.PLAIN -> palette.glassFill to palette.ink
    }
    val content = textColor ?: tint
    val glow = style == CapsuleStyle.PRIMARY || style == CapsuleStyle.SUCCESS || style == CapsuleStyle.DANGER
    Row(
        modifier = modifier
            .height(height)
            .pressable(onClick = onClick, enabled = enabled)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .clip(shape)
            .background(color = background)
            .then(if (style == CapsuleStyle.PLAIN) Modifier.border(width = 1f.dp, color = palette.glassStroke, shape = shape) else Modifier)
            .drawWithContent {
                drawContent()
                if (glow) drawLine(color = Color.White.copy(alpha = 0.3f), start = Offset(size.height / 2f, 0.5f), end = Offset(size.width - size.height / 2f, 0.5f), strokeWidth = 1f.dp.toPx())
            }
            .padding(horizontal = 22f.dp),
        horizontalArrangement = Arrangement.spacedBy(10f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(imageVector = it, contentDescription = null, tint = content, modifier = Modifier.size(iconSize)) }
        Text(text = text, style = Tossling.type.button, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44f.dp,
    iconSize: Dp = 20f.dp,
    inPanel: Boolean = false,
    bare: Boolean = false,
    tint: Color = Tossling.palette.ink,
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(size)
            .pressable(onClick = onClick, pressedScale = if (inPanel) 0.88f else 0.9f)
            .then(
                when {
                    bare -> Modifier
                    inPanel -> Modifier.clip(shape).background(Tossling.palette.glassWeak)
                    else -> Modifier.glass(shape = shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun FloatingBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16f.dp, end = 16f.dp, bottom = 24f.dp)
            .glass(shape = RoundedCornerShape(36f.dp), level = GlassLevel.STRONG)
            .padding(8f.dp),
        horizontalArrangement = Arrangement.spacedBy(8f.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun GlassToggle(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = Tossling.palette
    val track by animateColorAsState(targetValue = if (checked) palette.accent else palette.track, animationSpec = tween(durationMillis = 250), label = "track")
    val knob by animateDpAsState(
        targetValue = if (checked) 20f.dp else 0f.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "knob",
    )
    Box(
        modifier = modifier
            .size(width = 52f.dp, height = 32f.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(track.copy(alpha = if (enabled) track.alpha else track.alpha * 0.5f))
            .padding(2f.dp),
    ) {
        Box(
            modifier = Modifier
                .offset(x = knob)
                .size(28f.dp)
                .shadow(elevation = 2f.dp, shape = CircleShape)
                .background(color = Color.White, shape = CircleShape),
        )
    }
}

@Composable
fun Spinner(
    modifier: Modifier = Modifier,
    size: Dp = 40f.dp,
    stroke: Dp = 3f.dp,
    color: Color = Tossling.palette.accent,
    track: Color = Tossling.palette.hairline,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 850, easing = LinearEasing)),
        label = "angle",
    )
    Canvas(modifier = modifier.size(size)) {
        val width = stroke.toPx()
        val inset = width / 2f
        val arcSize = Size(this.size.width - width, this.size.height - width)
        drawCircle(color = track, radius = this.size.minDimension / 2f - inset, style = Stroke(width = width))
        rotate(degrees = angle) {
            drawArc(color = color, startAngle = -90f, sweepAngle = 90f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width = width))
        }
    }
}

@Composable
fun CircleBadge(
    icon: ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    size: Dp = 56f.dp,
    iconSize: Dp = 26f.dp,
) {
    val fill by animateColorAsState(targetValue = background, animationSpec = tween(durationMillis = STATUS_FADE_MS), label = "badgeFill")
    val ink by animateColorAsState(targetValue = tint, animationSpec = tween(durationMillis = STATUS_FADE_MS), label = "badgeInk")
    Box(
        modifier = modifier
            .size(size)
            .drawBehind { drawCircle(color = fill) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = ink, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun Modifier.rowPress(onClick: () -> Unit): Modifier = pressable(onClick = onClick, pressedScale = 0.97f, role = Role.Button)

private const val DISABLED_ALPHA = 0.45f
