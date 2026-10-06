package com.kopylovis.tossling.core.presentation.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kopylovis.tossling.core.presentation.messages.AppMessage
import com.kopylovis.tossling.core.presentation.messages.AppMessageKind
import com.kopylovis.tossling.core.presentation.theme.Tossy

@Composable
fun Island(
    message: AppMessage?,
    modifier: Modifier = Modifier,
) {
    val palette = Tossy.palette
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 8f.dp),
        enter = scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f)) +
            slideInVertically(animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f)) { -it / 2 } +
            fadeIn(animationSpec = tween(durationMillis = 250)),
        exit = scaleOut(targetScale = 0.6f) + slideOutVertically { -it / 2 } + fadeOut(animationSpec = tween(durationMillis = 200)),
    ) {
        val shown = remember(message) { message } ?: return@AnimatedVisibility
        Row(
            modifier = Modifier
                .height(44f.dp)
                .widthIn(min = 180f.dp)
                .clip(RoundedCornerShape(22f.dp))
                .background(palette.island)
                .padding(horizontal = 20f.dp),
            horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (shown.kind) {
                AppMessageKind.BUSY -> Spinner(size = 14f.dp, stroke = 2f.dp, color = Color.White, track = Color.White.copy(alpha = 0.25f))
                AppMessageKind.DONE -> Icon(imageVector = TossyIcons.Check, contentDescription = null, tint = Color(0xFF96C0FE), modifier = Modifier.size(14f.dp))
                AppMessageKind.ERROR -> Icon(imageVector = TossyIcons.Alert, contentDescription = null, tint = Color(0xFFFF9E96), modifier = Modifier.size(16f.dp))
                AppMessageKind.DEVICE -> Icon(imageVector = TossyIcons.Laptop, contentDescription = null, tint = Color.White, modifier = Modifier.size(18f.dp))
                AppMessageKind.INFO -> Box(
                    modifier = Modifier
                        .size(8f.dp)
                        .clip(RoundedCornerShape(4f.dp))
                        .background(Color.White.copy(alpha = 0.7f)),
                )
            }
            Text(text = shown.text, style = Tossy.type.hint.copy(fontWeight = FontWeight.Medium), color = Color.White, maxLines = 1)
        }
    }
}

@Composable
fun GlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    contentPadding: PaddingValues = PaddingValues(start = 20f.dp, end = 20f.dp, top = 12f.dp, bottom = 20f.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = Tossy.palette
    if (visible) BackHandler(onBack = onDismiss)
    TrackOverlay(visible = visible)
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(durationMillis = 250)), exit = fadeOut(tween(durationMillis = 200))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f)) { it },
            exit = slideOutVertically(animationSpec = tween(durationMillis = 220)) { it },
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(8f.dp)
                    .fillMaxWidth()
                    .glass(shape = RoundedCornerShape(40f.dp), level = GlassLevel.STRONG, blur = 32f.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(contentPadding),
                horizontalAlignment = horizontalAlignment,
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36f.dp, height = 5f.dp)
                        .clip(RoundedCornerShape(3f.dp))
                        .background(palette.hairline),
                )
                content()
            }
        }
    }
}

@Composable
fun ConfirmSheet(
    visible: Boolean,
    icon: ImageVector,
    title: String,
    text: String,
    confirm: String,
    cancel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = Tossy.palette
    GlassSheet(visible = visible, onDismiss = onDismiss) {
        Spacer(modifier = Modifier.height(20f.dp))
        CircleBadge(icon = icon, tint = palette.dangerInk, background = palette.dangerSoft)
        Spacer(modifier = Modifier.height(14f.dp))
        Text(text = title, style = Tossy.type.title2, color = palette.ink, textAlign = TextAlign.Center)
        Text(
            text = text,
            style = Tossy.type.body,
            color = palette.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 8f.dp, bottom = 22f.dp)
                .widthIn(max = 320f.dp),
        )
        CapsuleButton(text = confirm, onClick = onConfirm, style = CapsuleStyle.DANGER, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(10f.dp))
        CapsuleButton(text = cancel, onClick = onDismiss, style = CapsuleStyle.GLASS, modifier = Modifier.fillMaxWidth())
    }
}

data class MenuAction(
    val icon: ImageVector,
    val label: String,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun GlassMenu(
    visible: Boolean,
    actions: List<MenuAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Tossy.palette
    if (visible) {
        BackHandler(onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(initialScale = 0.6f, transformOrigin = TransformOrigin(1f, 0f), animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f)) + fadeIn(),
        exit = scaleOut(targetScale = 0.8f, transformOrigin = TransformOrigin(1f, 0f)) + fadeOut(),
    ) {
        Column(
            modifier = Modifier
                .width(256f.dp)
                .glass(shape = RoundedCornerShape(24f.dp), level = GlassLevel.STRONG),
        ) {
            actions.forEachIndexed { index, action ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16f.dp)
                            .fillMaxWidth()
                            .height(1f.dp)
                            .background(palette.hairline),
                    )
                }
                val color = if (action.danger) palette.dangerInk else palette.ink
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52f.dp)
                        .clickable(onClick = {
                            onDismiss()
                            action.onClick()
                        })
                        .padding(horizontal = 16f.dp),
                    horizontalArrangement = Arrangement.spacedBy(12f.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = action.icon, contentDescription = null, tint = color, modifier = Modifier.size(20f.dp))
                    Text(text = action.label, style = Tossy.type.row.copy(fontWeight = FontWeight.Normal), color = color)
                }
            }
        }
    }
}

@Composable
fun GlassGroup(
    modifier: Modifier = Modifier,
    lite: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28f.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (lite) Modifier.glassLite(shape = shape) else Modifier.glass(shape = shape))
            .clip(shape),
        content = content,
    )
}

@Composable
fun Hairline(
    modifier: Modifier = Modifier,
    start: Dp = 0f.dp,
) {
    Box(
        modifier = modifier
            .padding(start = start)
            .fillMaxWidth()
            .height(1f.dp)
            .background(Tossy.palette.hairline),
    )
}
