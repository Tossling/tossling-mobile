package com.kopylovis.tossling.core.presentation.glass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kopylovis.tossling.core.presentation.theme.Tossling

@Composable
fun PageScaffold(
    onBack: () -> Unit,
    backLabel: String,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 48f.dp,
    trailing: (@Composable () -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    GlassPage(
        modifier = modifier,
        topBar = {
            GlassTopBar(
                leading = { GlassIconButton(icon = TosslingIcons.Back, contentDescription = backLabel, onClick = onBack) },
                trailing = { trailing?.invoke() },
            )
        },
        overlay = overlay,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16f.dp, end = 16f.dp, top = statusTop + TopBarHeight, bottom = bottomPadding + navBottom),
            content = content,
        )
    }
}

@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null, large: Boolean = true) {
    Column(modifier = modifier.padding(start = 4f.dp, end = 4f.dp, top = 20f.dp), verticalArrangement = Arrangement.spacedBy(4f.dp)) {
        Text(text = title, style = if (large) Tossling.type.largeTitle else Tossling.type.title, color = Tossling.palette.ink)
        subtitle?.let { Text(text = it, style = Tossling.type.body, color = Tossling.palette.ink2) }
    }
}

@Composable
fun DeviceRow(
    name: String,
    isMac: Boolean,
    isOnline: Boolean,
    status: String,
    selfLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ownName: String? = null,
) {
    val palette = Tossling.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76f.dp)
            .clickable(onClick = onClick)
            .padding(start = 16f.dp, end = 12f.dp, top = 12f.dp, bottom = 12f.dp),
        horizontalArrangement = Arrangement.spacedBy(14f.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleBadge(
            icon = if (isMac) TosslingIcons.Laptop else TosslingIcons.Phone,
            tint = if (isOnline) palette.onAccent else palette.ink2,
            background = if (isOnline) palette.accent else palette.glassWeak,
            size = 44f.dp,
            iconSize = 22f.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2f.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8f.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = Tossling.type.row.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                selfLabel?.let { SelfChip(text = it) }
            }
            ownName?.let {
                Text(
                    text = it,
                    style = Tossling.type.footnote.copy(fontSize = 12.sp),
                    color = palette.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusLine(isOnline = isOnline, text = status)
        }
        Icon(imageVector = TosslingIcons.ChevronRight, contentDescription = null, tint = palette.ink2, modifier = Modifier.size(16f.dp))
    }
}

@Composable
fun SelfChip(text: String) {
    val palette = Tossling.palette
    val shape = RoundedCornerShape(9f.dp)
    Text(
        text = text,
        style = Tossling.type.footnote.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        color = palette.ink2,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(palette.glassWeak)
            .border(width = 1f.dp, color = palette.hairline, shape = shape)
            .padding(horizontal = 8f.dp, vertical = 1f.dp),
    )
}

@Composable
fun StatusLine(isOnline: Boolean, text: String, modifier: Modifier = Modifier) {
    val palette = Tossling.palette
    val color = animateStatusColor(target = if (isOnline) palette.accentInk else palette.ink2)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6f.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(isOnline = isOnline, ring = color)
        StatusText(text = text, style = Tossling.type.footnote.copy(fontWeight = FontWeight.Medium), color = color)
    }
}

@Composable
fun StatusDot(isOnline: Boolean, ring: Color, modifier: Modifier = Modifier) {
    val fill = Tossling.palette.accent
    val ringColor = animateStatusColor(target = ring)
    val progress by animateFloatAsState(
        targetValue = if (isOnline) 1f else 0f,
        animationSpec = spring(dampingRatio = DOT_DAMPING, stiffness = Spring.StiffnessMediumLow),
        label = "statusDot",
    )
    Box(
        modifier = modifier
            .size(7f.dp)
            .drawBehind {
                val stroke = DOT_STROKE.toPx()
                val shown = progress.coerceIn(0f, 1f)
                if (shown < 1f) {
                    drawCircle(color = ringColor, radius = size.minDimension / 2f - stroke / 2f, style = Stroke(width = stroke), alpha = 1f - shown)
                }
                if (progress > 0f) {
                    drawCircle(color = fill, radius = size.minDimension / 2f * progress, alpha = shown)
                }
            },
    )
}

@Composable
fun StatusText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(animationSpec = tween(durationMillis = STATUS_FADE_MS)) + slideInVertically(animationSpec = tween(durationMillis = STATUS_FADE_MS)) { it / 2 }) togetherWith
                (fadeOut(animationSpec = tween(durationMillis = STATUS_FADE_MS / 2)) + slideOutVertically(animationSpec = tween(durationMillis = STATUS_FADE_MS)) { -it / 2 }) using
                SizeTransform(clip = false)
        },
        contentAlignment = Alignment.CenterStart,
        label = "statusText",
        modifier = modifier,
    ) { value ->
        Text(text = value, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun animateStatusColor(target: Color): Color =
    animateColorAsState(targetValue = target, animationSpec = tween(durationMillis = STATUS_FADE_MS), label = "statusColor").value

const val STATUS_FADE_MS = 320
private const val DOT_DAMPING = 0.5f
private val DOT_STROKE = 1.5f.dp
