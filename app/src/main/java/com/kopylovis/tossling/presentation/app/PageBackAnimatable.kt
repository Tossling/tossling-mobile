package com.kopylovis.tossling.presentation.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.PredictiveBackAnimatable
import com.arkivanov.essenty.backhandler.BackEvent

@OptIn(ExperimentalDecomposeApi::class)
internal class PageBackAnimatable(initialEvent: BackEvent) : PredictiveBackAnimatable {

    private val progress = Animatable(initialEvent.progress)
    private val leaving = Animatable(0F)
    private var edge by mutableStateOf(initialEvent.swipeEdge)

    override val exitModifier: Modifier
        get() = Modifier.composed {
            var width by remember { mutableStateOf(0F) }
            val density = LocalDensity.current
            val drag = progress.value
            val gone = leaving.value
            val scale = 1F - drag / 10F
            val shift = with(density) {
                val pinned = (width - width * scale) / 2F - 8.dp.toPx() * drag
                when (edge) {
                    BackEvent.SwipeEdge.RIGHT -> -pinned
                    else -> pinned
                }
            }
            val corner = with(density) { (32.dp * (drag * 8F).coerceAtMost(1F)).toPx() }
            onPlaced { width = it.size.width.toFloat() }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = shift + (width - shift) * gone,
                    shape = RoundedCornerShape(corner),
                    clip = true,
                    compositingStrategy = CompositingStrategy.Offscreen,
                )
        }

    override val enterModifier: Modifier
        get() = Modifier.drawWithContent {
            drawContent()
            drawRect(color = Color.Black.copy(alpha = 0.2F * (progress.value * 4F).coerceAtMost(1F) * (1F - leaving.value)))
        }

    override suspend fun animate(event: BackEvent) {
        edge = event.swipeEdge
        progress.animateTo(event.progress)
    }

    override suspend fun finish() {
        leaving.animateTo(targetValue = 1F, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
    }

    override suspend fun cancel() {
        progress.animateTo(targetValue = 0F)
    }
}
