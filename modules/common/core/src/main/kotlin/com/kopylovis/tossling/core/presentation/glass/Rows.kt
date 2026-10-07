package com.kopylovis.tossling.core.presentation.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kopylovis.tossling.core.presentation.theme.Tossling
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6f.dp, end = 6f.dp, top = 24f.dp, bottom = 8f.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text = text, style = Tossling.type.body.copy(fontWeight = FontWeight.SemiBold), color = Tossling.palette.ink2, modifier = Modifier.weight(1f))
        trailing?.let { Text(text = it, style = Tossling.type.footnote.copy(fontWeight = FontWeight.Medium), color = Tossling.palette.ink2) }
    }
}

@Composable
fun FootNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Tossling.type.footnote,
        color = Tossling.palette.ink2,
        modifier = modifier.padding(start = 6f.dp, end = 6f.dp, top = 10f.dp),
    )
}

@Composable
fun ValueRow(label: String, value: String, modifier: Modifier = Modifier, mono: Boolean = false) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52f.dp)
            .padding(horizontal = 16f.dp, vertical = 8f.dp),
        horizontalArrangement = Arrangement.spacedBy(12f.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = Tossling.type.body, color = Tossling.palette.ink2)
        Text(
            text = value,
            style = if (mono) Tossling.type.monoSmall else Tossling.type.body.copy(fontWeight = FontWeight.Medium),
            color = Tossling.palette.ink,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56f.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16f.dp, vertical = 12f.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14f.dp),
    ) {
        RowTexts(label = label, note = note, modifier = Modifier.weight(1f))
        GlassToggle(checked = checked, enabled = enabled)
    }
}

@Composable
fun LinkRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    note: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56f.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16f.dp, vertical = 12f.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14f.dp),
    ) {
        RowTexts(label = label, note = note, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp), verticalAlignment = Alignment.CenterVertically) {
            value?.let { Text(text = it, style = Tossling.type.body, color = Tossling.palette.ink2) }
            Icon(imageVector = TosslingIcons.ChevronRight, contentDescription = null, tint = Tossling.palette.ink2, modifier = Modifier.size(16f.dp))
        }
    }
}

@Composable
fun AddRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 44f.dp,
) {
    val palette = Tossling.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60f.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16f.dp, vertical = 8f.dp),
        horizontalArrangement = Arrangement.spacedBy(14f.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleBadge(icon = TosslingIcons.Plus, tint = palette.accentInk, background = palette.accentSoft, size = badgeSize, iconSize = 18f.dp)
        Text(text = label, style = Tossling.type.row, color = palette.accentInk)
    }
}

@Composable
fun RowTexts(label: String, note: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2f.dp)) {
        Text(text = label, style = Tossling.type.row, color = Tossling.palette.ink)
        note?.let { Text(text = it, style = Tossling.type.footnote, color = Tossling.palette.ink2) }
    }
}

@Composable
fun GlassField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    mono: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val palette = Tossling.palette
    val style: TextStyle = if (mono) Tossling.type.mono else Tossling.type.row.copy(fontSize = 17.sp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 66f.dp)
            .padding(horizontal = 16f.dp, vertical = 10f.dp),
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
    ) {
        Text(text = label, style = Tossling.type.footnote, color = palette.ink2)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = style.copy(color = if (enabled) palette.ink else palette.ink2),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty()) Text(text = placeholder, style = style, color = palette.ink2.copy(alpha = 0.5f))
                    field()
                }
            },
        )
    }
}

@Composable
fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
) {
    val palette = Tossling.palette
    val shape = RoundedCornerShape(22f.dp)
    Row(
        modifier = modifier
            .height(44f.dp)
            .pressable(onClick = onClick, pressedScale = 0.93f)
            .clip(shape)
            .background(if (isSelected) palette.accent else palette.glassWeak)
            .then(if (isSelected) Modifier else Modifier.border(width = 1f.dp, color = palette.glassStroke, shape = shape))
            .padding(horizontal = 16f.dp),
        horizontalArrangement = Arrangement.spacedBy(8f.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dot?.let { color ->
            Box(
                modifier = Modifier
                    .size(10f.dp)
                    .border(width = 1.5f.dp, color = Color.White.copy(alpha = 0.7f), shape = CircleShape)
                    .padding(1.5f.dp)
                    .background(color = color, shape = CircleShape),
            )
        }
        Text(text = label, style = Tossling.type.body.copy(fontWeight = FontWeight.Medium), color = if (isSelected) palette.onAccent else palette.ink, maxLines = 1)
    }
}

@Composable
fun ProjectAvatar(
    initials: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36f.dp,
    iconPath: String? = null,
) {
    val icon = remember(iconPath) { iconPath?.takeIf { it.isNotEmpty() }?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(CircleShape))
        return
    }
    val fontSize = when {
        size >= 48f.dp -> 20.sp
        size <= 28f.dp -> 12.sp
        initials.length > 1 -> 13.sp
        else -> 15.sp
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .drawWithContent {
                drawContent()
                drawLine(color = Color.White.copy(alpha = 0.3f), start = Offset(this.size.width * 0.25f, 0.5f), end = Offset(this.size.width * 0.75f, 0.5f), strokeWidth = 1f.dp.toPx())
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initials, style = Tossling.type.body.copy(fontSize = fontSize, fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1)
    }
}

data class SwipeAction(
    val icon: ImageVector,
    val label: String,
    val background: Color,
    val tint: Color,
    val onClick: () -> Unit,
)

@Composable
fun SwipeRow(
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    actions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val actionWidth = 80f.dp
    val openPx = with(density) { (actionWidth * actions.size).toPx() }
    val thresholdPx = with(density) { 60f.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(isOpen) {
        offset.animateTo(if (isOpen) -openPx else 0f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f))
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { (-offset.value).coerceAtLeast(0f).toDp() }),
                horizontalArrangement = Arrangement.End,
            ) {
                actions.forEach { action -> SwipeButton(action = action, width = actionWidth) }
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(openPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val open = offset.value < -thresholdPx
                            scope.launch { offset.animateTo(if (open) -openPx else 0f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f)) }
                            onOpenChange(open)
                        },
                        onDragCancel = { scope.launch { offset.animateTo(if (isOpen) -openPx else 0f) } },
                    ) { change, delta ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-openPx, 0f)) }
                    }
                },
        ) {
            content()
        }
    }
}

@Composable
private fun RowScope.SwipeButton(action: SwipeAction, width: Dp) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(width)
            .background(action.background)
            .clickable(onClick = action.onClick)
            .graphicsLayer { clip = true },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4f.dp, Alignment.CenterVertically),
    ) {
        Icon(imageVector = action.icon, contentDescription = null, tint = action.tint, modifier = Modifier.size(20f.dp))
        Text(text = action.label, style = Tossling.type.footnote.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = action.tint, maxLines = 1)
    }
}
