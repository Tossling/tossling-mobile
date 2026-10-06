package com.kopylovis.tossling.core.presentation.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kopylovis.tossling.core.presentation.theme.Tossy
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class TabSpec(
    val icon: ImageVector,
    val label: String,
    val badge: Int = 0,
)

data class TabAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

private val LocalTabHighlight = staticCompositionLocalOf<(Int) -> Float> { { 0f } }

@Composable
fun TabBar(
    tabs: List<TabSpec>,
    selected: () -> Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    action: TabAction? = null,
    hidden: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val hide by animateFloatAsState(targetValue = if (hidden) 1f else 0f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f), label = "tabBarHide")
    val select = remember(selected, haptics) {
        { index: Int ->
            if (index != selected()) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            currentOnSelect(index)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = hide * (BAR_HEIGHT + BAR_BOTTOM + 48f.dp).toPx()
                alpha = 1f - hide
            }
            .navigationBarsPadding()
            .padding(start = 16f.dp, end = 16f.dp, bottom = BAR_BOTTOM)
            .height(BAR_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(10f.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidTabs(
            selectedIndex = selected,
            onTabSelected = select,
            hazeState = hazeState,
            tabsCount = tabs.size,
            modifier = Modifier.weight(1f),
        ) {
            tabs.forEachIndexed { index, tab ->
                LiquidTab(onClick = { select(index) }) {
                    TabItem(tab = tab, index = index)
                }
            }
        }
        action?.let { ActionCapsule(action = it, hazeState = hazeState) }
    }
}

@Composable
private fun TabItem(tab: TabSpec, index: Int) {
    val palette = Tossy.palette
    val highlight = LocalTabHighlight.current
    val color = lerpColor(palette.ink, palette.accentInk, highlight(index))
    Box(modifier = Modifier.size(22f.dp)) {
        Icon(imageVector = tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(22f.dp))
        if (tab.badge > 0) {
            CountBadge(
                count = tab.badge,
                modifier = Modifier
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .offset(x = 11f.dp, y = (-7f).dp),
            )
        }
    }
    Text(text = tab.label, style = Tossy.type.footnote.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = color, maxLines = 1)
}

@Composable
private fun LiquidTabs(
    selectedIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    hazeState: HazeState,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = Tossy.palette
    val capsuleShape = RoundedCornerShape(percent = 50)

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val maxWidth = constraints.maxWidth
                val fraction = if (maxWidth > 0) (offsetAnimation.value / maxWidth).fastCoerceIn(-1f, 1f) else 0f
                with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex()) }

        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    updateValue((targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f).fastCoerceIn(0f, (tabsCount - 1).toFloat()))
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                },
            )
        }

        LaunchedEffect(selectedIndex) {
            snapshotFlow { selectedIndex() }.collectLatest { index -> currentIndex = index }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                    val actual = selectedIndex()
                    if (actual != index) currentIndex = actual
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f,
                    )
                },
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                    val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, dampedDragAnimation.pressProgress)
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(elevation = 10f.dp, shape = capsuleShape, ambientColor = palette.shadow, spotColor = palette.shadow)
                .clip(capsuleShape)
                .hazeEffect(
                    state = hazeState,
                    style = HazeStyle(backgroundColor = palette.background, tint = HazeTint(palette.tabContainer), blurRadius = palette.glassBlur, noiseFactor = 0f),
                ) {
                    inputScale = HazeInputScale.Auto
                }
                .border(width = 1f.dp, color = palette.glassStroke, shape = capsuleShape)
                .then(interactiveHighlight.modifier)
                .height(64f.dp)
                .fillMaxWidth(),
        )

        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX = if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                    val velocity = dampedDragAnimation.velocity / 10f
                    val squishX = 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                    val squishY = 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    scaleX = (dampedDragAnimation.scaleX / squishX).finiteOr(1f)
                    scaleY = (dampedDragAnimation.scaleY * squishY).finiteOr(1f)
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .clip(capsuleShape)
                .background(palette.tabIndicator)
                .height(56f.dp)
                .fillMaxWidth(1f / tabsCount),
        )

        CompositionLocalProvider(
            LocalTabHighlight provides { index -> (1f - abs(dampedDragAnimation.value - index)).fastCoerceIn(0f, 1f) },
        ) {
            Row(
                Modifier
                    .graphicsLayer { translationX = panelOffset }
                    .height(64f.dp)
                    .fillMaxWidth()
                    .padding(4f.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
private fun RowScope.LiquidTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
            .fillMaxHeight()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val palette = Tossy.palette
    Box(
        modifier = modifier
            .background(color = palette.badgeRing, shape = RoundedCornerShape(11f.dp))
            .padding(2f.dp)
            .height(18f.dp)
            .widthIn(min = 18f.dp)
            .background(color = palette.badge, shape = RoundedCornerShape(9f.dp))
            .padding(horizontal = 5f.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > MAX_BADGE) "$MAX_BADGE+" else count.toString(),
            style = Tossy.type.footnote.copy(
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both),
            ),
            color = palette.onBadge,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun ActionCapsule(action: TabAction, hazeState: HazeState) {
    val palette = Tossy.palette
    val capsuleShape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 122f.dp)
            .pressable(onClick = action.onClick, pressedScale = 0.92f)
            .shadow(elevation = 10f.dp, shape = capsuleShape, ambientColor = palette.shadow, spotColor = palette.shadow)
            .clip(capsuleShape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(backgroundColor = palette.background, tint = HazeTint(palette.accent.copy(alpha = ACTION_FILL)), blurRadius = palette.glassBlur, noiseFactor = 0f),
            ) {
                inputScale = HazeInputScale.Auto
            }
            .border(width = 1f.dp, color = Color.White.copy(alpha = 0.22f), shape = capsuleShape)
            .padding(horizontal = 18f.dp),
        horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = action.icon, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(18f.dp))
        Text(text = action.label, style = Tossy.type.button, color = palette.onAccent, maxLines = 1)
    }
}

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

private val BAR_HEIGHT = 64f.dp
private val BAR_BOTTOM = 12f.dp
private const val ACTION_FILL = 0.86f
private const val MAX_BADGE = 99
