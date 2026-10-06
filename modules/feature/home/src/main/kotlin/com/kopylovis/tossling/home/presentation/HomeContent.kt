package com.kopylovis.tossling.home.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.kopylovis.tossling.core.presentation.contactTime
import com.kopylovis.tossling.core.presentation.glass.AddRow
import com.kopylovis.tossling.core.presentation.glass.CapsuleButton
import com.kopylovis.tossling.core.presentation.glass.CapsuleStyle
import com.kopylovis.tossling.core.presentation.glass.CircleBadge
import com.kopylovis.tossling.core.presentation.glass.ConfirmSheet
import com.kopylovis.tossling.core.presentation.glass.DeviceRow
import com.kopylovis.tossling.core.presentation.glass.FlowingTitle
import com.kopylovis.tossling.core.presentation.glass.GlassCard
import com.kopylovis.tossling.core.presentation.glass.GlassField
import com.kopylovis.tossling.core.presentation.glass.GlassGroup
import com.kopylovis.tossling.core.presentation.glass.GlassIconButton
import com.kopylovis.tossling.core.presentation.glass.GlassLevel
import com.kopylovis.tossling.core.presentation.glass.GlassPage
import com.kopylovis.tossling.core.presentation.glass.GlassSheet
import com.kopylovis.tossling.core.presentation.glass.GlassTopBar
import com.kopylovis.tossling.core.presentation.glass.Hairline
import com.kopylovis.tossling.core.presentation.glass.LocalHazeState
import com.kopylovis.tossling.core.presentation.glass.STATUS_FADE_MS
import com.kopylovis.tossling.core.presentation.glass.Spinner
import com.kopylovis.tossling.core.presentation.glass.StatusDot
import com.kopylovis.tossling.core.presentation.glass.StatusText
import com.kopylovis.tossling.core.presentation.glass.SwipeAction
import com.kopylovis.tossling.core.presentation.glass.SwipeRow
import com.kopylovis.tossling.core.presentation.glass.TopBarHeight
import com.kopylovis.tossling.core.presentation.glass.TossyIcons
import com.kopylovis.tossling.core.presentation.glass.TrackOverlay
import com.kopylovis.tossling.core.presentation.glass.animateStatusColor
import com.kopylovis.tossling.core.presentation.glass.glass
import com.kopylovis.tossling.core.presentation.glass.pressable
import com.kopylovis.tossling.core.presentation.glass.rememberTitleCollapse
import com.kopylovis.tossling.core.presentation.glass.rowPress
import com.kopylovis.tossling.core.presentation.relativeTime
import com.kopylovis.tossling.core.presentation.seenAgo
import com.kopylovis.tossling.core.presentation.theme.Tossy
import com.kopylovis.tossling.home.R
import com.kopylovis.tossling.sync.data.ClipItem
import com.kopylovis.tossling.sync.data.ClipKind
import com.kopylovis.tossling.sync.data.DeviceKind
import com.kopylovis.tossling.sync.data.RoomDevice
import com.kopylovis.tossling.sync.data.Transfer
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    component: HomeComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val palette = Tossy.palette
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    val collapse = rememberTitleCollapse(listState = listState)
    val seen = remember { mutableSetOf<String>() }
    val initial = remember { mutableStateOf(true) }
    LaunchedEffect(state.history) {
        if (state.history.isNotEmpty() || !initial.value) {
            seen.addAll(state.history.map { it.id })
            initial.value = false
        }
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val scope = rememberCoroutineScope()
    val title = stringResource(if (state.isRoom) R.string.home_title_room else R.string.home_title)
    val othersCount = state.devices.count { !it.isSelf }
    val shown = remember(state.history, state.query) { filterHistory(history = state.history, query = state.query) }
    var openSwipe by remember { mutableStateOf<String?>(null) }

    GlassPage(
        modifier = modifier,
        topBar = {
            GlassTopBar(
                trailing = {
                    GlassIconButton(icon = TossyIcons.Sliders, contentDescription = stringResource(R.string.home_settings), onClick = component::onSettingsClicked, iconSize = 22f.dp)
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
            PickSheet(pick = state.pick, onPick = component::onTargetPicked, onDismiss = component::onPickDismissed)
            ImagePreview(item = state.preview, onCopy = component::onPreviewCopied, onShare = component::onPreviewShared, onDismiss = component::onPreviewDismissed)
            ConfirmSheet(
                visible = state.leaving != null,
                icon = TossyIcons.KeyOff,
                title = stringResource(R.string.home_leave_title),
                text = stringResource(R.string.home_leave_text),
                confirm = stringResource(R.string.home_leave_confirm),
                cancel = stringResource(R.string.home_cancel),
                onConfirm = component::onLeaveConfirmed,
                onDismiss = component::onLeaveDismissed,
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
                contentPadding = PaddingValues(start = 16f.dp, end = 16f.dp, top = statusTop + TopBarHeight, bottom = 140f.dp + imeBottom),
            ) {
                item(key = "header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4f.dp, end = 4f.dp, bottom = 4f.dp),
                        verticalArrangement = Arrangement.spacedBy(2f.dp),
                    ) {
                        Text(text = title, style = Tossy.type.largeTitle, color = palette.ink, maxLines = 1, modifier = Modifier.alpha(0f))
                        Text(
                            text = if (state.isRoom) {
                                stringResource(R.string.home_room, pluralStringResource(R.plurals.home_devices, state.devices.count { !it.isSelf } + 1, state.devices.count { !it.isSelf } + 1))
                            } else {
                                stringResource(R.string.home_subtitle)
                            },
                            style = Tossy.type.body,
                            color = palette.ink2,
                        )
                    }
                }
                item(key = "status") {
                    if (state.isRoom) {
                        DevicesGroup(state = state, component = component, modifier = Modifier.padding(top = 20f.dp))
                    } else {
                        StatusCard(state = state, modifier = Modifier.padding(top = 20f.dp))
                    }
                }
                if (state.showsTileHint) {
                    item(key = "tile") {
                        TileHint(onClick = component::onTileHintClicked, onClose = component::onTileHintClosed, modifier = Modifier.padding(top = 12f.dp))
                    }
                }
                state.transfer?.let { transfer ->
                    item(key = "transfer") {
                        TransferCard(transfer = transfer, onCancel = component::onTransferCancelled, modifier = Modifier.padding(top = 12f.dp))
                    }
                }
                item(key = "recent") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6f.dp, end = 6f.dp, top = 28f.dp, bottom = 10f.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(text = stringResource(R.string.home_history), style = Tossy.type.title2, color = palette.ink, modifier = Modifier.weight(1f))
                        if (state.history.isNotEmpty()) {
                            Text(text = stringResource(R.string.home_history_count, state.history.size), style = Tossy.type.footnote, color = palette.ink2)
                        }
                    }
                }
                if (state.history.size > SEARCH_FROM || state.query.isNotEmpty()) {
                    item(key = "search") {
                        GlassGroup(
                            modifier = Modifier
                                .padding(bottom = 12f.dp)
                                .onFocusChanged { focus ->
                                    if (focus.hasFocus) {
                                        scope.launch {
                                            val index = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "search" }?.index
                                            if (index != null) listState.animateScrollToItem(index = index)
                                        }
                                    }
                                },
                        ) {
                            GlassField(
                                label = stringResource(R.string.home_search),
                                value = state.query,
                                onValueChange = component::onQueryChanged,
                                placeholder = stringResource(R.string.home_search_hint),
                            )
                        }
                    }
                }
                if (state.history.isEmpty()) {
                    item(key = "empty") { EmptyHistory() }
                } else if (shown.isEmpty()) {
                    item(key = "nothing") {
                        Text(
                            text = stringResource(R.string.home_nothing_found),
                            style = Tossy.type.body,
                            color = palette.ink2,
                            modifier = Modifier.padding(horizontal = 6f.dp, vertical = 12f.dp),
                        )
                    }
                } else {
                    item(key = "history") {
                        GlassGroup(modifier = Modifier.animateContentSize(animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f))) {
                            shown.forEachIndexed { index, item ->
                                key(item.id) {
                                    SwipeRow(
                                        isOpen = openSwipe == item.id,
                                        onOpenChange = { open -> openSwipe = if (open) item.id else openSwipe.takeIf { it != item.id } },
                                        actions = listOf(
                                            SwipeAction(
                                                icon = TossyIcons.Pin,
                                                label = stringResource(if (item.isPinned) R.string.home_unpin else R.string.home_pin),
                                                background = palette.accentSoft,
                                                tint = palette.accentInk,
                                                onClick = {
                                                    openSwipe = null
                                                    component.onPinToggled(item = item)
                                                },
                                            ),
                                            SwipeAction(
                                                icon = TossyIcons.Trash,
                                                label = stringResource(R.string.home_delete),
                                                background = palette.danger,
                                                tint = palette.onDanger,
                                                onClick = {
                                                    openSwipe = null
                                                    component.onDeleteClicked(item = item)
                                                },
                                            ),
                                        ),
                                    ) {
                                        HistoryRow(
                                            item = item,
                                            isRoom = othersCount > 1,
                                            isFirst = index == 0,
                                            isFresh = !initial.value && item.id !in seen,
                                            onClick = { if (openSwipe != null) openSwipe = null else component.onItemClicked(item = item) },
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
private fun DevicesGroup(state: HomeScreenState, component: HomeComponent, modifier: Modifier = Modifier) {
    val rooms = state.devices.groupBy { it.pairingId }.values.toList()
    Column(modifier = modifier) {
        rooms.forEachIndexed { roomIndex, devices ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6f.dp, top = if (roomIndex == 0) 0f.dp else 16f.dp, bottom = 4f.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (rooms.size > 1) stringResource(R.string.home_room_number, roomIndex + 1) else stringResource(R.string.home_devices_title),
                    style = Tossy.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = Tossy.palette.ink2,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.home_leave),
                    style = Tossy.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = Tossy.palette.dangerInk,
                    modifier = Modifier
                        .pressable(onClick = { component.onLeaveRoomClicked(pairingId = devices.first().pairingId) }, pressedScale = 0.94f)
                        .padding(horizontal = 10f.dp, vertical = 10f.dp),
                )
            }
            GlassGroup {
                devices.forEachIndexed { index, device ->
                    if (index > 0) Hairline(start = 74f.dp)
                    DeviceRow(
                        name = device.name,
                        isMac = device.kind == DeviceKind.MAC,
                        isOnline = device.isOnline,
                        status = if (device.isOnline) stringResource(R.string.home_online) else seenAgo(time = device.seen),
                        ownName = device.shownOwnName,
                        selfLabel = when {
                            device.isSelf -> stringResource(R.string.home_self)
                            device.isOwner -> stringResource(R.string.home_owner)
                            else -> null
                        },
                        onClick = { component.onDeviceClicked(device = device) },
                    )
                }
                if (roomIndex == rooms.lastIndex) {
                    Hairline(start = 74f.dp)
                    AddRow(label = stringResource(R.string.home_add_mac), onClick = component::onAddMacClicked)
                }
            }
        }
    }
}

@Composable
private fun PickSheet(pick: PickState?, onPick: (RoomDevice?) -> Unit, onDismiss: () -> Unit) {
    val palette = Tossy.palette
    val shown = remember { mutableStateOf<PickState?>(null) }
    if (pick != null) shown.value = pick
    GlassSheet(
        visible = pick != null,
        onDismiss = onDismiss,
        horizontalAlignment = Alignment.Start,
        contentPadding = PaddingValues(start = 16f.dp, end = 16f.dp, top = 12f.dp, bottom = 16f.dp),
    ) {
        val current = shown.value ?: return@GlassSheet
        Text(
            text = stringResource(R.string.home_pick_title),
            style = Tossy.type.title2,
            color = palette.ink,
            modifier = Modifier.padding(start = 6f.dp, end = 6f.dp, top = 18f.dp),
        )
        Text(
            text = stringResource(R.string.home_pick_preview, current.preview),
            style = Tossy.type.body,
            color = palette.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6f.dp, end = 6f.dp, top = 4f.dp, bottom = 16f.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24f.dp))
                .background(palette.glassWeak)
                .border(width = 1f.dp, color = palette.hairline, shape = RoundedCornerShape(24f.dp)),
        ) {
            PickRow(
                icon = TossyIcons.Devices,
                label = stringResource(R.string.home_pick_all),
                note = stringResource(R.string.home_pick_all_note, pluralStringResource(R.plurals.home_devices, current.targets.size, current.targets.size)),
                isPrimary = true,
                onClick = { onPick(null) },
            )
            current.targets.forEach { device ->
                Hairline(start = 68f.dp)
                PickRow(
                    icon = if (device.kind == DeviceKind.MAC) TossyIcons.Laptop else TossyIcons.Phone,
                    label = device.name,
                    note = if (device.isOnline) stringResource(R.string.home_online) else stringResource(R.string.home_pick_later, seenAgo(time = device.seen)),
                    isPrimary = false,
                    onClick = { onPick(device) },
                )
            }
        }
        Text(
            text = stringResource(R.string.home_pick_note),
            style = Tossy.type.footnote,
            color = palette.ink2,
            modifier = Modifier.padding(start = 8f.dp, end = 8f.dp, top = 10f.dp),
        )
        CapsuleButton(
            text = stringResource(R.string.home_cancel),
            onClick = onDismiss,
            style = CapsuleStyle.GLASS,
            modifier = Modifier
                .padding(top = 12f.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun PickRow(icon: ImageVector, label: String, note: String, isPrimary: Boolean, onClick: () -> Unit) {
    val palette = Tossy.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64f.dp)
            .rowPress(onClick = onClick)
            .padding(horizontal = 14f.dp, vertical = 10f.dp),
        horizontalArrangement = Arrangement.spacedBy(14f.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleBadge(
            icon = icon,
            tint = if (isPrimary) palette.onAccent else palette.accentInk,
            background = if (isPrimary) palette.accent else palette.accentSoft,
            size = 40f.dp,
            iconSize = 20f.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1f.dp)) {
            Text(text = label, style = Tossy.type.row.copy(fontWeight = FontWeight.SemiBold), color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = note, style = Tossy.type.footnote, color = palette.ink2)
        }
        Icon(imageVector = TossyIcons.ArrowUp, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(18f.dp))
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

@Composable
private fun StatusCard(state: HomeScreenState, modifier: Modifier = Modifier) {
    val palette = Tossy.palette
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18f.dp), verticalArrangement = Arrangement.spacedBy(14f.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14f.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleBadge(
                    icon = TossyIcons.Laptop,
                    tint = if (state.isOnline) palette.onAccent else palette.ink2,
                    background = if (state.isOnline) palette.accent else palette.glassWeak,
                    size = 48f.dp,
                    iconSize = 24f.dp,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2f.dp)) {
                    Text(text = state.macName, style = Tossy.type.headline, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = state.server, style = Tossy.type.monoSmall.copy(fontWeight = FontWeight.Normal), color = palette.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LinkPill(isOnline = state.isOnline)
            }
            Hairline()
            Column {
                InfoRow(
                    label = stringResource(R.string.home_delivery),
                    value = stringResource(if (state.isInstant) R.string.home_delivery_instant else R.string.home_delivery_on_open),
                )
                AnimatedVisibility(
                    visible = !state.isOnline && state.lastContact > 0,
                    enter = fadeIn(animationSpec = tween(durationMillis = STATUS_FADE_MS)) + expandVertically(animationSpec = tween(durationMillis = STATUS_FADE_MS)),
                    exit = fadeOut(animationSpec = tween(durationMillis = STATUS_FADE_MS)) + shrinkVertically(animationSpec = tween(durationMillis = STATUS_FADE_MS)),
                ) {
                    InfoRow(
                        label = stringResource(R.string.home_last_contact),
                        value = contactTime(time = state.lastContact),
                        modifier = Modifier.padding(top = 14f.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkPill(isOnline: Boolean) {
    val palette = Tossy.palette
    val ink = animateStatusColor(target = if (isOnline) palette.accentInk else palette.dangerInk)
    val fill = animateStatusColor(target = if (isOnline) palette.glassStrong else palette.dangerSoft)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14f.dp))
            .drawBehind { drawRect(color = fill) }
            .animateContentSize()
            .padding(horizontal = 10f.dp, vertical = 6f.dp),
        horizontalArrangement = Arrangement.spacedBy(6f.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(isOnline = isOnline, ring = ink)
        StatusText(
            text = stringResource(if (isOnline) R.string.home_online else R.string.home_offline),
            style = Tossy.type.footnote.copy(fontWeight = FontWeight.Medium),
            color = ink,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12f.dp)) {
        Text(text = label, style = Tossy.type.body, color = Tossy.palette.ink2)
        Text(
            text = value,
            style = Tossy.type.body.copy(fontWeight = FontWeight.Medium),
            color = Tossy.palette.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TileHint(onClick: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val palette = Tossy.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(22f.dp), level = GlassLevel.WEAK, blur = 20f.dp, withShadow = false)
            .rowPress(onClick = onClick)
            .padding(start = 16f.dp, end = 8f.dp, top = 12f.dp, bottom = 12f.dp),
        horizontalArrangement = Arrangement.spacedBy(12f.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = TossyIcons.Tile, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(20f.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = palette.accentInk)) { append(stringResource(R.string.home_tile_action)) }
                withStyle(SpanStyle(color = palette.ink2)) { append(stringResource(R.string.home_tile_rest)) }
            },
            style = Tossy.type.hint,
            modifier = Modifier.weight(1f),
        )
        GlassIconButton(icon = TossyIcons.Close, contentDescription = stringResource(R.string.home_tile_close), onClick = onClose, iconSize = 14f.dp, bare = true, tint = palette.ink2)
    }
}

@Composable
private fun TransferCard(transfer: Transfer, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val palette = Tossy.palette
    val context = LocalContext.current
    val progress by animateFloatAsState(targetValue = transfer.percent / 100f, label = "transfer")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(22f.dp), level = GlassLevel.WEAK, blur = 20f.dp, withShadow = false)
            .padding(start = 16f.dp, end = 8f.dp, top = 12f.dp, bottom = 12f.dp),
        horizontalArrangement = Arrangement.spacedBy(12f.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (transfer.incoming) TossyIcons.ArrowDown else TossyIcons.ArrowUp,
            contentDescription = null,
            tint = palette.accentInk,
            modifier = Modifier.size(20f.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6f.dp)) {
            Text(
                text = stringResource(if (transfer.incoming) R.string.home_transfer_receive else R.string.home_transfer_send, transfer.name),
                style = Tossy.type.body.copy(fontWeight = FontWeight.Medium),
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4f.dp)
                    .clip(CircleShape)
                    .background(palette.hairline),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(palette.accent),
                )
            }
            Text(
                text = stringResource(
                    R.string.home_transfer_progress,
                    transfer.percent,
                    Formatter.formatShortFileSize(context, transfer.done),
                    Formatter.formatShortFileSize(context, transfer.total),
                ),
                style = Tossy.type.footnote,
                color = palette.ink2,
            )
        }
        if (!transfer.incoming) {
            GlassIconButton(icon = TossyIcons.Close, contentDescription = stringResource(R.string.home_transfer_cancel), onClick = onCancel, iconSize = 14f.dp, bare = true, tint = palette.ink2)
        }
    }
}

@Composable
private fun EmptyHistory() {
    val palette = Tossy.palette
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28f.dp, vertical = 36f.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8f.dp),
        ) {
            CircleBadge(icon = TossyIcons.Clipboard, tint = palette.accentInk, background = palette.accentSoft, iconSize = 24f.dp, modifier = Modifier.padding(bottom = 8f.dp))
            Text(text = stringResource(R.string.home_history_empty_title), style = Tossy.type.headline, color = palette.ink)
            Text(
                text = stringResource(R.string.home_history_empty),
                style = Tossy.type.body,
                color = palette.ink2,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280f.dp),
            )
        }
    }
}

private fun filterHistory(history: List<ClipItem>, query: String): List<ClipItem> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val matching = if (words.isEmpty()) {
        history
    } else {
        history.filter { item ->
            val haystack = listOfNotNull(item.text, item.name, item.device, item.mime).joinToString(separator = " ").lowercase()
            words.all { it in haystack }
        }
    }
    return matching.filter { it.isPinned } + matching.filterNot { it.isPinned }
}

private const val SEARCH_FROM = 6

@Composable
private fun HistoryRow(
    item: ClipItem,
    isRoom: Boolean,
    isFirst: Boolean,
    isFresh: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Tossy.palette
    val entrance = remember { Animatable(if (isFresh) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (entrance.value < 1f) entrance.animateTo(1f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 300f))
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val progress = entrance.value
                translationY = (1f - progress) * -60f.dp.toPx()
                val scale = 0.9f + 0.1f * progress
                scaleX = scale
                scaleY = scale
                alpha = progress.coerceIn(0f, 1f)
            },
    ) {
        if (!isFirst) Hairline(start = 56f.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .rowPress(onClick = onClick)
                .padding(horizontal = 16f.dp, vertical = 14f.dp),
            horizontalArrangement = Arrangement.spacedBy(12f.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28f.dp)
                    .background(color = palette.accentSoft, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (item.incoming) TossyIcons.ArrowDown else TossyIcons.ArrowUp,
                    contentDescription = null,
                    tint = palette.accentInk,
                    modifier = Modifier.size(14f.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6f.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = when {
                            item.incoming && item.device.isNotEmpty() -> stringResource(R.string.home_from, item.device)
                            item.incoming -> stringResource(R.string.home_from_mac)
                            item.toAll && isRoom -> stringResource(R.string.home_to_all)
                            item.device.isNotEmpty() -> stringResource(R.string.home_to, item.device)
                            else -> stringResource(R.string.home_to_mac)
                        },
                        style = Tossy.type.footnote,
                        color = palette.ink2,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.isPinned) {
                        Icon(imageVector = TossyIcons.Pin, contentDescription = null, tint = palette.accentInk, modifier = Modifier.padding(end = 6f.dp).size(14f.dp))
                    }
                    Text(text = relativeTime(time = item.time), style = Tossy.type.footnote, color = palette.ink2)
                }
                when (item.kind) {
                    ClipKind.TEXT -> Text(
                        text = item.text.orEmpty(),
                        style = Tossy.type.body,
                        color = palette.ink,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    ClipKind.IMAGE -> Thumbnail(path = item.file)
                    ClipKind.FILE -> FileChip(name = item.name ?: "file", size = item.size)
                }
            }
        }
    }
}

@Composable
private fun FileChip(name: String, size: Long) {
    val palette = Tossy.palette
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14f.dp))
            .background(palette.glassWeak)
            .border(width = 1f.dp, color = palette.hairline, shape = RoundedCornerShape(14f.dp))
            .padding(horizontal = 12f.dp, vertical = 10f.dp),
        horizontalArrangement = Arrangement.spacedBy(10f.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = TossyIcons.Document, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(22f.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = Tossy.type.body.copy(fontWeight = FontWeight.Medium), color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (size > 0) Text(text = Formatter.formatShortFileSize(context, size), style = Tossy.type.footnote, color = palette.ink2)
        }
    }
}

@Composable
private fun Thumbnail(path: String?) {
    val palette = Tossy.palette
    val context = LocalContext.current
    val density = LocalDensity.current
    val bounds = remember(path) { path?.let(::imageBounds) }
    val label = remember(path) {
        path?.let(::File)?.let { file -> "${file.extension.uppercase()} · ${Formatter.formatShortFileSize(context, file.length())}" }.orEmpty()
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val aspect = bounds?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height } ?: DEFAULT_ASPECT
        val height = (maxWidth / aspect).coerceIn(THUMBNAIL_MIN, THUMBNAIL_MAX)
        val target = IntSize(width = constraints.maxWidth, height = with(density) { height.roundToPx() })
        val bitmap by produceState<ImageBitmap?>(initialValue = null, path, target) {
            value = path?.let { file -> withContext(Dispatchers.IO) { decodeImage(path = file, target = target, fill = true) } }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(14f.dp))
                .background(palette.glassWeak)
                .border(width = 1f.dp, color = palette.hairline, shape = RoundedCornerShape(14f.dp)),
        ) {
            bitmap?.let { image ->
                Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
            }
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = Tossy.type.monoSmall.copy(fontSize = Tossy.type.footnote.fontSize * 0.85f),
                    color = palette.ink,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8f.dp)
                        .clip(RoundedCornerShape(8f.dp))
                        .background(palette.glassStrong)
                        .padding(horizontal = 8f.dp, vertical = 3f.dp),
                )
            }
        }
    }
}

@Composable
private fun ImagePreview(item: ClipItem?, onCopy: () -> Unit, onShare: () -> Unit, onDismiss: () -> Unit) {
    val palette = Tossy.palette
    val shown = remember { mutableStateOf<ClipItem?>(null) }
    if (item != null) shown.value = item
    val visible = item != null
    if (visible) BackHandler(onBack = onDismiss)
    TrackOverlay(visible = visible)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = PREVIEW_FADE_MS)) + scaleIn(animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f), initialScale = 0.94f),
        exit = fadeOut(animationSpec = tween(durationMillis = PREVIEW_FADE_MS)) + scaleOut(animationSpec = tween(durationMillis = PREVIEW_FADE_MS), targetScale = 0.96f),
    ) {
        val current = shown.value ?: return@AnimatedVisibility
        val hazeState = LocalHazeState.current
        CompositionLocalProvider(LocalHazeState provides null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeEffect(
                                state = hazeState,
                                style = HazeStyle(
                                    backgroundColor = palette.background,
                                    tint = HazeTint(color = palette.background.copy(alpha = PREVIEW_TINT)),
                                    blurRadius = PREVIEW_BLUR,
                                    noiseFactor = 0f,
                                ),
                            )
                        } else {
                            Modifier.background(palette.background)
                        },
                    )
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            ) {
                ZoomableImage(path = current.file, modifier = Modifier.fillMaxSize())
                GlassIconButton(
                    icon = TossyIcons.Close,
                    contentDescription = stringResource(R.string.home_preview_close),
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16f.dp),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16f.dp),
                    horizontalArrangement = Arrangement.spacedBy(12f.dp),
                ) {
                    CapsuleButton(
                        text = stringResource(R.string.home_preview_share),
                        onClick = onShare,
                        style = CapsuleStyle.PLAIN,
                        icon = TossyIcons.External,
                        modifier = Modifier.weight(1f),
                    )
                    CapsuleButton(
                        text = stringResource(R.string.home_preview_copy),
                        onClick = onCopy,
                        icon = TossyIcons.Copy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(path: String?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = modifier) {
        val target = IntSize(width = constraints.maxWidth * PREVIEW_DETAIL, height = constraints.maxHeight * PREVIEW_DETAIL)
        val bitmap by produceState<ImageBitmap?>(initialValue = null, path, target) {
            value = path?.let { file -> withContext(Dispatchers.IO) { decodeImage(path = file, target = target, fill = false) } }
        }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        fun clamp(value: Offset, zoom: Float): Offset {
            val maxX = width * (zoom - 1f) / 2f
            val maxY = height * (zoom - 1f) / 2f
            return Offset(x = value.x.coerceIn(-maxX, maxX), y = value.y.coerceIn(-maxY, maxY))
        }
        bitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { tap ->
                                val from = scale
                                val fromOffset = offset
                                val to = if (from > 1.01f) 1f else DOUBLE_TAP_ZOOM
                                val toOffset = if (to == 1f) Offset.Zero else clamp(value = (Offset(width / 2f, height / 2f) - tap) * (to - 1f), zoom = to)
                                scope.launch {
                                    animate(initialValue = 0f, targetValue = 1f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)) { progress, _ ->
                                        scale = from + (to - from) * progress
                                        offset = fromOffset + (toOffset - fromOffset) * progress
                                    }
                                }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                            scale = next
                            offset = if (next <= 1f) Offset.Zero else clamp(value = offset + pan, zoom = next)
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
    }
}

private fun imageBounds(path: String): IntSize? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    return if (bounds.outWidth > 0 && bounds.outHeight > 0) IntSize(width = bounds.outWidth, height = bounds.outHeight) else null
}

private fun decodeImage(path: String, target: IntSize, fill: Boolean): ImageBitmap? {
    val source = imageBounds(path = path) ?: return null
    if (target.width <= 0 || target.height <= 0) return null
    val horizontal = target.width.toFloat() / source.width
    val vertical = target.height.toFloat() / source.height
    val need = (if (fill) maxOf(horizontal, vertical) else minOf(horizontal, vertical)).coerceAtMost(1f)
    var sample = 1
    while (1f / (sample * 2) >= need) sample *= 2
    val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val width = (source.width * need).roundToInt().coerceAtLeast(1)
    val height = (source.height * need).roundToInt().coerceAtLeast(1)
    if (!fill || decoded.width <= width) return decoded.asImageBitmap()
    val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
    if (scaled !== decoded) decoded.recycle()
    return scaled.asImageBitmap()
}

private const val DEFAULT_ASPECT = 16f / 10f
private val THUMBNAIL_MIN = 96f.dp
private val THUMBNAIL_MAX = 240f.dp
private const val PREVIEW_FADE_MS = 220
private const val PREVIEW_TINT = 0.82f
private val PREVIEW_BLUR = 40f.dp
private const val PREVIEW_DETAIL = 2
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val MAX_ZOOM = 6f

