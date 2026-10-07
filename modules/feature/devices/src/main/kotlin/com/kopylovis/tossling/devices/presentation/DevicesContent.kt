package com.kopylovis.tossling.devices.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.kopylovis.tossling.core.presentation.glass.AddRow
import com.kopylovis.tossling.core.presentation.glass.DeviceRow
import com.kopylovis.tossling.core.presentation.glass.FootNote
import com.kopylovis.tossling.core.presentation.glass.GlassGroup
import com.kopylovis.tossling.core.presentation.glass.Hairline
import com.kopylovis.tossling.core.presentation.glass.PageScaffold
import com.kopylovis.tossling.core.presentation.glass.PageTitle
import com.kopylovis.tossling.core.presentation.seenAgo
import com.kopylovis.tossling.devices.R
import com.kopylovis.tossling.protocol.DeviceKind

@Composable
internal fun DevicesContent(
    component: DevicesComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    PageScaffold(onBack = component::onBackClicked, backLabel = stringResource(R.string.devices_back), modifier = modifier) {
        PageTitle(title = stringResource(R.string.devices_title), subtitle = stringResource(R.string.devices_subtitle))
        GlassGroup(modifier = Modifier.padding(top = 24f.dp)) {
            state.devices.forEachIndexed { index, device ->
                if (index > 0) Hairline(start = 74f.dp)
                DeviceRow(
                    name = device.name,
                    isComputer = device.kind == DeviceKind.COMPUTER,
                    isOnline = device.isOnline,
                    status = if (device.isOnline) stringResource(R.string.devices_online) else seenAgo(time = device.seen),
                    ownName = device.shownOwnName,
                    selfLabel = if (device.isSelf) stringResource(R.string.devices_self) else null,
                    onClick = { component.onDeviceClicked(device = device) },
                )
            }
            Hairline(start = 74f.dp)
            AddRow(label = stringResource(R.string.devices_add_mac), onClick = component::onAddClicked)
        }
        FootNote(text = stringResource(R.string.devices_note))
    }
}
