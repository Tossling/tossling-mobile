package com.kopylovis.tossling.devices.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.kopylovis.tossling.core.presentation.contactTime
import com.kopylovis.tossling.core.presentation.glass.CapsuleButton
import com.kopylovis.tossling.core.presentation.glass.CapsuleStyle
import com.kopylovis.tossling.core.presentation.glass.CircleBadge
import com.kopylovis.tossling.core.presentation.glass.ConfirmSheet
import com.kopylovis.tossling.core.presentation.glass.FootNote
import com.kopylovis.tossling.core.presentation.glass.GlassField
import com.kopylovis.tossling.core.presentation.glass.GlassGroup
import com.kopylovis.tossling.core.presentation.glass.Hairline
import com.kopylovis.tossling.core.presentation.glass.PageScaffold
import com.kopylovis.tossling.core.presentation.glass.StatusDot
import com.kopylovis.tossling.core.presentation.glass.StatusText
import com.kopylovis.tossling.core.presentation.glass.TosslingIcons
import com.kopylovis.tossling.core.presentation.glass.ValueRow
import com.kopylovis.tossling.core.presentation.glass.animateStatusColor
import com.kopylovis.tossling.core.presentation.longDate
import com.kopylovis.tossling.core.presentation.seenAgo
import com.kopylovis.tossling.core.presentation.theme.Tossling
import com.kopylovis.tossling.devices.R
import com.kopylovis.tossling.sync.data.DeviceKind
import com.kopylovis.tossling.sync.data.RoomDevice

@Composable
internal fun DeviceContent(
    component: DeviceComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val device = state.device
    PageScaffold(
        onBack = component::onBackClicked,
        backLabel = stringResource(R.string.devices_back),
        modifier = modifier,
        overlay = {
            if (device != null) {
                ConfirmSheet(
                    visible = state.isSheetVisible,
                    icon = TosslingIcons.KeyOff,
                    title = if (device.isOwner && !device.isSelf) stringResource(R.string.devices_sheet_owner_title) else stringResource(R.string.devices_sheet_title, device.name),
                    text = stringResource(
                        when {
                            !device.isRoom -> R.string.devices_sheet_legacy
                            device.isSelf -> R.string.devices_sheet_self
                            device.isOwner -> R.string.devices_sheet_owner
                            else -> R.string.devices_sheet_other
                        },
                    ),
                    confirm = stringResource(
                        when {
                            !device.isRoom -> R.string.devices_sheet_legacy_confirm
                            device.isSelf || device.isOwner -> R.string.devices_sheet_self_confirm
                            else -> R.string.devices_sheet_other_confirm
                        },
                    ),
                    cancel = stringResource(R.string.devices_cancel),
                    onConfirm = component::onDisconnectConfirmed,
                    onDismiss = component::onSheetDismissed,
                )
            }
        },
    ) {
        if (device == null) return@PageScaffold
        DeviceHeader(device = device)
        GlassGroup(modifier = Modifier.padding(top = 24f.dp)) {
            GlassField(
                label = stringResource(R.string.devices_name),
                value = state.name,
                onValueChange = component::onNameChanged,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.onFocusChanged { focus -> if (!focus.isFocused) component.onNameDone() },
            )
            if (!device.isSelf || device.ownName != device.name) {
                Hairline(start = 16f.dp)
                ValueRow(label = stringResource(if (device.isSelf) R.string.devices_system_name else R.string.devices_own_name), value = device.ownName)
            }
            Hairline(start = 16f.dp)
            ValueRow(label = stringResource(R.string.devices_server), value = device.host, mono = true)
            Hairline(start = 16f.dp)
            ValueRow(
                label = stringResource(R.string.devices_last_contact),
                value = if (device.isOnline) stringResource(R.string.devices_now) else contactTime(time = device.seen),
            )
            if (device.since > 0) {
                Hairline(start = 16f.dp)
                ValueRow(label = stringResource(R.string.devices_since), value = longDate(time = device.since))
            }
        }
        FootNote(text = stringResource(if (device.isSelf) R.string.devices_name_shared else R.string.devices_name_local))
        if (device.isOwner && !device.isSelf) FootNote(text = stringResource(R.string.devices_owner_note))
        if (!device.isSelf && !(device.isRoom && device.isOwner)) CapsuleButton(
            text = stringResource(
                when {
                    !device.isRoom -> R.string.devices_unpair
                    device.isSelf || device.isOwner -> R.string.devices_leave
                    device.kind == DeviceKind.MAC -> R.string.devices_remove_mac
                    else -> R.string.devices_remove_phone
                },
            ),
            onClick = component::onDisconnectClicked,
            style = CapsuleStyle.PLAIN,
            textColor = Tossling.palette.dangerInk,
            modifier = Modifier
                .padding(top = 24f.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun DeviceHeader(device: RoomDevice) {
    val palette = Tossling.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12f.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10f.dp),
    ) {
        CircleBadge(
            icon = if (device.kind == DeviceKind.MAC) TosslingIcons.Laptop else TosslingIcons.Phone,
            tint = if (device.isOnline) palette.onAccent else palette.ink2,
            background = if (device.isOnline) palette.accent else palette.glassWeak,
            size = 80f.dp,
            iconSize = 36f.dp,
        )
        Text(text = device.name, style = Tossling.type.title, color = palette.ink, textAlign = TextAlign.Center)
        if (device.hasAlias) {
            Text(text = device.ownName, style = Tossling.type.hint, color = palette.ink2, textAlign = TextAlign.Center)
        }
        if (device.isSelf) {
            Text(text = stringResource(R.string.devices_self_title), style = Tossling.type.hint, color = palette.ink2)
        } else if (device.isOwner) {
            Text(text = stringResource(R.string.devices_owner_title), style = Tossling.type.hint, color = palette.ink2)
        }
        val ink = animateStatusColor(target = if (device.isOnline) palette.accentInk else palette.ink2)
        val fill = animateStatusColor(target = if (device.isOnline) palette.accentSoft else palette.glassWeak)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14f.dp))
                .drawBehind { drawRect(color = fill) }
                .animateContentSize()
                .padding(horizontal = 12f.dp, vertical = 6f.dp),
            horizontalArrangement = Arrangement.spacedBy(6f.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(isOnline = device.isOnline, ring = ink)
            StatusText(
                text = if (device.isOnline) stringResource(R.string.devices_online) else seenAgo(time = device.seen),
                style = Tossling.type.hint.copy(fontWeight = FontWeight.Medium),
                color = ink,
            )
        }
    }
}

