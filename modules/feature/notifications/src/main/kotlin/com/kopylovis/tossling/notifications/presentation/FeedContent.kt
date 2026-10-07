package com.kopylovis.tossling.notifications.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.kopylovis.tossling.core.presentation.clockTime
import com.kopylovis.tossling.core.presentation.glass.CircleBadge
import com.kopylovis.tossling.core.presentation.glass.ConfirmSheet
import com.kopylovis.tossling.core.presentation.glass.FilterChip
import com.kopylovis.tossling.core.presentation.glass.FlowingTitle
import com.kopylovis.tossling.core.presentation.glass.GlassCard
import com.kopylovis.tossling.core.presentation.glass.GlassGroup
import com.kopylovis.tossling.core.presentation.glass.GlassIconButton
import com.kopylovis.tossling.core.presentation.glass.GlassLevel
import com.kopylovis.tossling.core.presentation.glass.GlassMenu
import com.kopylovis.tossling.core.presentation.glass.GlassPage
import com.kopylovis.tossling.core.presentation.glass.GlassTopBar
import com.kopylovis.tossling.core.presentation.glass.Hairline
import com.kopylovis.tossling.core.presentation.glass.MenuAction
import com.kopylovis.tossling.core.presentation.glass.ProjectAvatar
import com.kopylovis.tossling.core.presentation.glass.SectionLabel
import com.kopylovis.tossling.core.presentation.glass.Spinner
import com.kopylovis.tossling.core.presentation.glass.SwipeAction
import com.kopylovis.tossling.core.presentation.glass.SwipeRow
import com.kopylovis.tossling.core.presentation.glass.TopBarHeight
import com.kopylovis.tossling.core.presentation.glass.TosslingIcons
import com.kopylovis.tossling.core.presentation.glass.glass
import com.kopylovis.tossling.core.presentation.glass.pressable
import com.kopylovis.tossling.core.presentation.glass.rememberTitleCollapse
import com.kopylovis.tossling.core.presentation.theme.ProjectColors
import com.kopylovis.tossling.core.presentation.theme.Tossling
import com.kopylovis.tossling.notifications.R
import com.kopylovis.tossling.protocol.alerts.projectInitials

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedContent(
    component: FeedComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val palette = Tossling.palette
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    var isMenuOpen by remember { mutableStateOf(false) }
    var openSwipe by remember { mutableStateOf<String?>(null) }
    val collapse = rememberTitleCollapse(listState = listState)
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val title = stringResource(R.string.notif_title)
    val filtered = state.projects.firstOrNull { it.topic == state.filter }

    GlassPage(
        modifier = modifier,
        topBar = {
            GlassTopBar(
                trailing = {
                    GlassIconButton(icon = TosslingIcons.More, contentDescription = stringResource(R.string.notif_menu), onClick = { isMenuOpen = true })
                },
            )
            FlowingTitle(
                text = title,
                collapse = collapse,
                expandedStart = 20f.dp,
                expandedTop = statusTop + TopBarHeight,
                pull = { with(density) { pullState.distanceFraction.coerceIn(0f, 1.6f) * 56f.dp.toPx() } },
            )
        },
        overlay = {
            GlassMenu(
                visible = isMenuOpen,
                onDismiss = { isMenuOpen = false },
                actions = listOf(
                    MenuAction(icon = TosslingIcons.DoneAll, label = stringResource(R.string.notif_read_all), onClick = component::onReadAllClicked),
                    MenuAction(icon = TosslingIcons.Projects, label = stringResource(R.string.notif_projects), onClick = component::onProjectsClicked),
                    MenuAction(icon = TosslingIcons.Trash, label = stringResource(R.string.notif_clear), danger = true, onClick = component::onClearClicked),
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = statusTop + TopBarHeight + 4f.dp, end = 16f.dp),
            )
            ConfirmSheet(
                visible = state.isClearSheetVisible,
                icon = TosslingIcons.Trash,
                title = stringResource(R.string.notif_clear_title),
                text = stringResource(R.string.notif_clear_text),
                confirm = stringResource(R.string.notif_clear),
                cancel = stringResource(R.string.notif_cancel),
                onConfirm = component::onClearConfirmed,
                onDismiss = component::onClearDismissed,
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullToRefresh(isRefreshing = state.isRefreshing, state = pullState, onRefresh = component::onRefresh),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = pullState.distanceFraction.coerceIn(0f, 1.6f) * 56f.dp.toPx() },
                contentPadding = PaddingValues(top = statusTop + TopBarHeight, bottom = 140f.dp),
            ) {
                item(key = "header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20f.dp, end = 20f.dp, bottom = 4f.dp),
                        verticalArrangement = Arrangement.spacedBy(2f.dp),
                    ) {
                        Text(text = title, style = Tossling.type.largeTitle, color = palette.ink, maxLines = 1, modifier = Modifier.alpha(0f))
                        Text(
                            text = if (state.unread > 0) pluralStringResource(R.plurals.notif_unread_count, state.unread, state.unread) else stringResource(R.string.notif_all_read),
                            style = Tossling.type.body,
                            color = palette.ink2,
                        )
                    }
                }
                if (state.projects.isNotEmpty()) {
                    item(key = "chips") {
                        LazyRow(
                            modifier = Modifier.padding(top = 18f.dp),
                            contentPadding = PaddingValues(horizontal = 16f.dp),
                            horizontalArrangement = Arrangement.spacedBy(8f.dp),
                        ) {
                            item(key = "all") {
                                FilterChip(label = stringResource(R.string.notif_all), isSelected = state.filter == null, onClick = { component.onFilterClicked(topic = null) })
                            }
                            items(items = state.projects, key = { it.topic }) { project ->
                                FilterChip(
                                    label = project.name,
                                    isSelected = state.filter == project.topic,
                                    dot = ProjectColors[project.colorIndex],
                                    onClick = { component.onFilterClicked(topic = project.topic) },
                                )
                            }
                        }
                    }
                }
                if (state.days.isEmpty()) {
                    item(key = "empty") {
                        EmptyFeed(
                            title = if (state.hasAlerts && filtered != null) stringResource(R.string.notif_empty_project_title, filtered.name) else stringResource(R.string.notif_empty_title),
                            text = if (state.hasAlerts && filtered != null) stringResource(R.string.notif_empty_project_text) else stringResource(R.string.notif_empty_text),
                            onProjects = component::onProjectsClicked,
                        )
                    }
                }
                state.days.forEach { day ->
                    item(key = "day-${day.label}") {
                        SectionLabel(text = day.label, modifier = Modifier.padding(horizontal = 16f.dp))
                    }
                    item(key = "group-${day.label}") {
                        GlassGroup(lite = true, modifier = Modifier.padding(horizontal = 16f.dp)) {
                            day.items.forEachIndexed { index, item ->
                                key(item.alert.id) {
                                    if (index > 0) Hairline(start = 64f.dp)
                                    SwipeRow(
                                        isOpen = openSwipe == item.alert.id,
                                        onOpenChange = { open -> openSwipe = if (open) item.alert.id else openSwipe.takeIf { it != item.alert.id } },
                                        actions = listOf(
                                            SwipeAction(
                                                icon = TosslingIcons.Done,
                                                label = stringResource(if (item.alert.isRead) R.string.notif_unread else R.string.notif_read),
                                                background = palette.accentSoft,
                                                tint = palette.accentInk,
                                                onClick = {
                                                    openSwipe = null
                                                    component.onReadToggled(id = item.alert.id)
                                                },
                                            ),
                                            SwipeAction(
                                                icon = TosslingIcons.Trash,
                                                label = stringResource(R.string.notif_delete),
                                                background = palette.danger,
                                                tint = palette.onDanger,
                                                onClick = {
                                                    openSwipe = null
                                                    component.onDeleteClicked(id = item.alert.id)
                                                },
                                            ),
                                        ),
                                    ) {
                                        AlertRow(
                                            item = item,
                                            onClick = {
                                                if (openSwipe != null) openSwipe = null else component.onAlertClicked(id = item.alert.id)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            PullSpinner(fraction = pullState.distanceFraction, isRefreshing = state.isRefreshing, top = statusTop + 14f.dp)
        }
    }
}

@Composable
private fun AlertRow(item: FeedItem, onClick: () -> Unit) {
    val palette = Tossling.palette
    val alert = item.alert
    val project = item.project
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (alert.isUrgent) palette.urgentRow else Color.Transparent)
            .pressable(onClick = onClick, pressedScale = 0.99f)
            .padding(horizontal = 16f.dp, vertical = 14f.dp),
        horizontalArrangement = Arrangement.spacedBy(12f.dp),
    ) {
        ProjectAvatar(
            initials = project?.initials ?: projectInitials(name = alert.topic),
            color = ProjectColors[project?.colorIndex ?: 0],
            size = 36f.dp,
            iconPath = project?.iconFile,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3f.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8f.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = project?.name ?: alert.topic,
                    style = Tossling.type.footnote,
                    color = palette.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                PriorityChip(priority = alert.priority)
                Text(text = clockTime(time = alert.time), style = Tossling.type.footnote, color = palette.ink2)
                if (!alert.isRead) {
                    Box(
                        modifier = Modifier
                            .size(8f.dp)
                            .background(color = palette.accent, shape = CircleShape),
                    )
                }
            }
            val title = alert.titleFor(project = project?.name.orEmpty())
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = Tossling.type.row.copy(lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
                    color = if (alert.priority <= 2) palette.ink2 else palette.ink,
                )
            }
            Text(
                text = plainText(markdown = alert.message),
                style = Tossling.type.body,
                color = palette.ink2,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun PriorityChip(priority: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    val palette = Tossling.palette
    val (text, background, ink) = when {
        priority >= 5 -> Triple(stringResource(R.string.notif_urgent), palette.danger, palette.onDanger)
        priority == 4 -> Triple(stringResource(R.string.notif_important), palette.importantSoft, palette.importantInk)
        !large -> return
        priority <= 2 -> Triple(stringResource(R.string.notif_priority_quiet), palette.glassWeak, palette.ink2)
        else -> Triple(stringResource(R.string.notif_priority_normal), palette.glassWeak, palette.ink)
    }
    Text(
        text = text,
        style = Tossling.type.footnote.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        color = ink,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(if (large) 10f.dp else 9f.dp))
            .background(background)
            .padding(horizontal = if (large) 9f.dp else 8f.dp, vertical = if (large) 2f.dp else 1f.dp),
    )
}

@Composable
private fun EmptyFeed(title: String, text: String, onProjects: () -> Unit) {
    val palette = Tossling.palette
    GlassCard(
        modifier = Modifier
            .padding(start = 16f.dp, end = 16f.dp, top = 24f.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28f.dp, vertical = 36f.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8f.dp),
        ) {
            CircleBadge(icon = TosslingIcons.Bell, tint = palette.accentInk, background = palette.accentSoft, iconSize = 24f.dp, modifier = Modifier.padding(bottom = 8f.dp))
            Text(text = title, style = Tossling.type.headline, color = palette.ink, textAlign = TextAlign.Center)
            Text(text = text, style = Tossling.type.body, color = palette.ink2, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 290f.dp))
            Text(
                text = stringResource(R.string.notif_projects),
                style = Tossling.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = palette.accentInk,
                modifier = Modifier
                    .padding(top = 10f.dp)
                    .pressable(onClick = onProjects, pressedScale = 0.94f)
                    .glass(shape = RoundedCornerShape(22f.dp), level = GlassLevel.WEAK, withShadow = false)
                    .padding(horizontal = 18f.dp, vertical = 11f.dp),
            )
        }
    }
}

@Composable
private fun PullSpinner(fraction: Float, isRefreshing: Boolean, top: Dp) {
    val alpha = if (isRefreshing) 1f else (fraction / 0.9f).coerceIn(0f, 1f)
    if (alpha <= 0f) return
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .padding(top = top)
                .graphicsLayer { this.alpha = alpha }
                .size(40f.dp)
                .glass(shape = CircleShape, level = GlassLevel.STRONG),
            contentAlignment = Alignment.Center,
        ) {
            Spinner(size = 20f.dp, stroke = 2.5f.dp)
        }
    }
}
