package com.kopylovis.tossling.presentation.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.kopylovis.tossling.R
import com.kopylovis.tossling.core.presentation.glass.LocalOverlayTracker
import com.kopylovis.tossling.core.presentation.glass.OverlayTracker
import com.kopylovis.tossling.core.presentation.glass.TabAction
import com.kopylovis.tossling.core.presentation.glass.TabBar
import com.kopylovis.tossling.core.presentation.glass.TabSpec
import com.kopylovis.tossling.core.presentation.glass.TossyIcons
import com.kopylovis.tossling.mediators.MediatorManager
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
internal fun MainContent(
    component: MainComponent,
    modifier: Modifier = Modifier,
) {
    val tabState = component.tab.subscribeAsState()
    val tab = tabState.value
    val selectedIndex = remember(tabState) { { tabState.value.ordinal } }
    val unread by component.unread.subscribeAsState()
    val pages = rememberSaveableStateHolder()
    val hazeState = rememberHazeState()
    val overlays = remember { OverlayTracker() }
    CompositionLocalProvider(LocalOverlayTracker provides overlays) {
        Box(modifier = modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    val slide = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = SLIDE_STIFFNESS, visibilityThreshold = IntOffset.VisibilityThreshold)
                    slideInHorizontally(animationSpec = slide) { width -> width * direction } togetherWith
                        (slideOutHorizontally(animationSpec = slide) { width -> -(width * PARALLAX * direction).toInt() } + fadeOut(animationSpec = spring(stiffness = SLIDE_STIFFNESS), targetAlpha = LEAVING_ALPHA))
                },
                label = "tabs",
            ) { page ->
                pages.SaveableStateProvider(key = page.name) {
                    when (page) {
                        MainTab.BUFFER -> MediatorManager.homeMediator.getApi().openHomeContent(component = component.buffer)
                        MainTab.NOTIFICATIONS -> MediatorManager.notificationsMediator.getApi().openFeedContent(component = component.feed)
                    }
                }
            }
            TabBar(
                tabs = listOf(
                    TabSpec(icon = TossyIcons.Clipboard, label = stringResource(R.string.tab_buffer)),
                    TabSpec(icon = TossyIcons.Bell, label = stringResource(R.string.tab_notifications), badge = unread),
                ),
                selected = selectedIndex,
                onSelect = { index -> component.onTabClicked(tab = MainTab.entries[index]) },
                hazeState = hazeState,
                action = TabAction(icon = TossyIcons.ArrowUp, label = stringResource(R.string.tab_send), onClick = component::onSendClicked),
                hidden = overlays.open > 0,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private const val SLIDE_STIFFNESS = 520f
private const val PARALLAX = 0.3f
private const val LEAVING_ALPHA = 0.65f
