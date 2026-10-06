package com.kopylovis.tossling.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.kopylovis.tossling.core.presentation.glass.GlassGroup
import com.kopylovis.tossling.core.presentation.glass.Hairline
import com.kopylovis.tossling.core.presentation.glass.LinkRow
import com.kopylovis.tossling.core.presentation.glass.PageScaffold
import com.kopylovis.tossling.core.presentation.glass.PageTitle
import com.kopylovis.tossling.core.presentation.glass.SectionLabel
import com.kopylovis.tossling.core.presentation.glass.ToggleRow
import com.kopylovis.tossling.settings.R

@Composable
internal fun SettingsContent(
    component: SettingsComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    PageScaffold(onBack = component::onBackClicked, backLabel = stringResource(R.string.settings_back), modifier = modifier) {
        PageTitle(title = stringResource(R.string.settings_title))
        SectionLabel(text = stringResource(R.string.settings_group_clipboard))
        GlassGroup(lite = true) {
            ToggleRow(
                label = stringResource(R.string.settings_pause),
                note = stringResource(R.string.settings_pause_note),
                checked = state.isPaused,
                onClick = component::onPauseClicked,
            )
            Hairline(start = 16f.dp)
            ToggleRow(
                label = stringResource(R.string.settings_images),
                note = stringResource(R.string.settings_images_note),
                checked = state.sendsImages,
                onClick = component::onImagesClicked,
            )
            Hairline(start = 16f.dp)
            ToggleRow(
                label = stringResource(R.string.settings_instant),
                note = stringResource(
                    when {
                        !state.isInstantAvailable -> R.string.settings_instant_unavailable
                        state.isServerPushless -> R.string.settings_instant_server
                        else -> R.string.settings_instant_note
                    },
                ),
                checked = state.isInstant,
                enabled = state.isInstantAvailable,
                onClick = component::onInstantClicked,
            )
            Hairline(start = 16f.dp)
            ToggleRow(
                label = stringResource(R.string.settings_live),
                note = stringResource(R.string.settings_live_note),
                checked = state.isLive,
                onClick = component::onLiveClicked,
            )
        }
        SectionLabel(text = stringResource(R.string.settings_group_notifications))
        GlassGroup(lite = true) {
            LinkRow(label = stringResource(R.string.settings_projects), value = state.projects.toString(), onClick = component::onProjectsClicked)
            Hairline(start = 16f.dp)
            ToggleRow(
                label = stringResource(R.string.settings_quiet),
                note = stringResource(R.string.settings_quiet_note),
                checked = state.isQuietHours,
                onClick = component::onQuietHoursClicked,
            )
        }
        SectionLabel(text = stringResource(R.string.settings_group_devices))
        GlassGroup(lite = true) {
            LinkRow(label = stringResource(R.string.settings_devices), value = state.devices.toString(), onClick = component::onDevicesClicked)
        }
        SectionLabel(text = stringResource(R.string.settings_group_about))
        GlassGroup(lite = true) {
            LinkRow(label = stringResource(R.string.settings_about_note), value = state.version, onClick = {})
        }
    }
}
