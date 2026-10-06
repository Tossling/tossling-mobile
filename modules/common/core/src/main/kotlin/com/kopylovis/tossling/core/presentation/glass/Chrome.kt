package com.kopylovis.tossling.core.presentation.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kopylovis.tossling.core.presentation.theme.Tossy
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt

val TopBarHeight = 56f.dp

class OverlayTracker {
    var open by mutableIntStateOf(0)
}

val LocalOverlayTracker = staticCompositionLocalOf<OverlayTracker?> { null }

@Composable
fun TrackOverlay(visible: Boolean) {
    val tracker = LocalOverlayTracker.current ?: return
    if (!visible) return
    DisposableEffect(tracker) {
        tracker.open += 1
        onDispose { tracker.open -= 1 }
    }
}

@Composable
fun GlassPage(
    modifier: Modifier = Modifier,
    topBar: @Composable BoxScope.() -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = Tossy.palette
    val hazeState = rememberHazeState()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
        ) {
            TossyBackground()
            CompositionLocalProvider(LocalHazeState provides null) {
                content()
            }
        }
        EdgeBlur(
            hazeState = hazeState,
            fromTop = true,
            tint = palette.background.copy(alpha = TOP_TINT),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusTop + TopBarHeight + TOP_EDGE_EXTRA),
        )
        EdgeBlur(
            hazeState = hazeState,
            fromTop = false,
            tint = palette.background.copy(alpha = BOTTOM_TINT),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(navBottom + BOTTOM_EDGE),
        )
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            topBar()
            overlay()
        }
    }
}

@Composable
fun EdgeBlur(
    hazeState: HazeState,
    fromTop: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val palette = Tossy.palette
    Box(
        modifier = modifier
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(backgroundColor = palette.background, tint = null, blurRadius = EDGE_BLUR, noiseFactor = 0f),
            ) {
                inputScale = HazeInputScale.Auto
                progressive = HazeProgressive.verticalGradient(
                    easing = LinearEasing,
                    startIntensity = if (fromTop) 1f else 0f,
                    endIntensity = if (fromTop) 0f else 1f,
                    preferPerformance = true,
                )
            }
            .background(
                brush = Brush.verticalGradient(
                    colors = if (fromTop) listOf(tint, tint.copy(alpha = 0f)) else listOf(tint.copy(alpha = 0f), tint),
                ),
            ),
    )
}

@Composable
fun BoxScope.GlassTopBar(
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16f.dp)
            .height(TopBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp), verticalAlignment = Alignment.CenterVertically, content = leading)
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
fun rememberTitleCollapse(listState: LazyListState): () -> Float {
    val distance = with(LocalDensity.current) { COLLAPSE_DISTANCE.toPx() }
    return remember(listState, distance) {
        {
            if (listState.firstVisibleItemIndex > 0) 1f else (listState.firstVisibleItemScrollOffset / distance).coerceIn(0f, 1f)
        }
    }
}

@Composable
fun BoxScope.FlowingTitle(
    text: String,
    collapse: () -> Float,
    expandedStart: Dp,
    expandedTop: Dp,
    pull: () -> Float = { 0f },
) {
    val palette = Tossy.palette
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val style = Tossy.type.largeTitle
    val collapsedScale = COLLAPSED_SIZE / style.fontSize.value
    val expandedX = with(density) { expandedStart.toPx() }
    val expandedY = with(density) { expandedTop.toPx() }
    val collapsedCenterY = with(density) { (statusTop + TopBarHeight / 2).toPx() }
    var titleSize by remember { mutableStateOf(IntSize.Zero) }
    var containerWidth by remember { mutableIntStateOf(0) }
    Box(
        modifier = Modifier
            .matchParentSize()
            .onSizeChanged { containerWidth = it.width },
    ) {
        Text(
            text = text,
            style = style,
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .onSizeChanged { titleSize = it }
                .offset {
                    val progress = collapse()
                    val scale = 1f + (collapsedScale - 1f) * progress
                    val collapsedX = (containerWidth - titleSize.width * scale) / 2f
                    val collapsedY = collapsedCenterY - titleSize.height * scale / 2f
                    val expandedPullY = expandedY + pull()
                    IntOffset(
                        x = (expandedX + (collapsedX - expandedX) * progress).roundToInt(),
                        y = (expandedPullY + (collapsedY - expandedPullY) * progress).roundToInt(),
                    )
                }
                .graphicsLayer {
                    val scale = 1f + (collapsedScale - 1f) * collapse()
                    transformOrigin = TransformOrigin(pivotFractionX = 0f, pivotFractionY = 0f)
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

private val TOP_EDGE_EXTRA = 20f.dp
private val BOTTOM_EDGE = 40f.dp
private const val TOP_TINT = 0.55f
private val EDGE_BLUR = 18f.dp
private const val BOTTOM_TINT = 0.2f
private val COLLAPSE_DISTANCE = 44f.dp
private const val COLLAPSED_SIZE = 17f
