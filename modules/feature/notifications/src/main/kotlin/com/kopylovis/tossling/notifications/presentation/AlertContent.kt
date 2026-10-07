package com.kopylovis.tossling.notifications.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.kopylovis.tossling.core.presentation.exactTime
import com.kopylovis.tossling.core.presentation.glass.CapsuleButton
import com.kopylovis.tossling.core.presentation.glass.CapsuleStyle
import com.kopylovis.tossling.core.presentation.glass.FloatingBar
import com.kopylovis.tossling.core.presentation.glass.GlassCard
import com.kopylovis.tossling.core.presentation.glass.GlassGroup
import com.kopylovis.tossling.core.presentation.glass.GlassIconButton
import com.kopylovis.tossling.core.presentation.glass.Hairline
import com.kopylovis.tossling.core.presentation.glass.PageScaffold
import com.kopylovis.tossling.core.presentation.glass.ProjectAvatar
import com.kopylovis.tossling.core.presentation.glass.TosslingIcons
import com.kopylovis.tossling.core.presentation.glass.ValueRow
import com.kopylovis.tossling.core.presentation.theme.ProjectColors
import com.kopylovis.tossling.core.presentation.theme.Tossling
import com.kopylovis.tossling.notifications.R
import com.kopylovis.tossling.protocol.alerts.projectInitials

@Composable
internal fun AlertContent(
    component: AlertComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val palette = Tossling.palette
    val uriHandler = LocalUriHandler.current
    val alert = state.alert
    val project = state.project
    PageScaffold(
        onBack = component::onBackClicked,
        backLabel = stringResource(R.string.notif_back),
        modifier = modifier,
        bottomPadding = 140f.dp,
        trailing = {
            GlassIconButton(icon = TosslingIcons.Trash, contentDescription = stringResource(R.string.notif_delete), onClick = component::onDeleteClicked)
        },
        overlay = {
            if (alert != null) {
                FloatingBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                    val link = alert.click
                    if (link != null) {
                        CapsuleButton(text = stringResource(R.string.notif_copy_text), onClick = component::onCopyClicked, style = CapsuleStyle.GLASS, modifier = Modifier.weight(1f))
                        CapsuleButton(
                            text = stringResource(R.string.notif_open_link),
                            icon = TosslingIcons.External,
                            iconSize = 14f.dp,
                            onClick = { runCatching { uriHandler.openUri(link) } },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        CapsuleButton(text = stringResource(R.string.notif_copy_text), onClick = component::onCopyClicked, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
    ) {
        if (alert == null) return@PageScaffold
        val name = project?.name ?: alert.topic
        Row(
            modifier = Modifier.padding(start = 4f.dp, end = 4f.dp, top = 22f.dp),
            horizontalArrangement = Arrangement.spacedBy(10f.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProjectAvatar(initials = project?.initials ?: projectInitials(name = name), color = ProjectColors[project?.colorIndex ?: 0], size = 28f.dp, iconPath = project?.iconFile)
            Text(text = name, style = Tossling.type.body.copy(fontWeight = FontWeight.Medium), color = palette.ink2)
            PriorityChip(priority = alert.priority, large = true)
        }
        Text(
            text = alert.titleFor(project = name).ifBlank { name },
            style = Tossling.type.title,
            color = palette.ink,
            modifier = Modifier.padding(start = 4f.dp, end = 4f.dp, top = 8f.dp),
        )
        GlassCard(modifier = Modifier.padding(top = 20f.dp)) {
            MarkdownBody(markdown = alert.message, isMarkdown = alert.isMarkdown, modifier = Modifier.padding(18f.dp))
        }
        GlassGroup(lite = true, modifier = Modifier.padding(top = 12f.dp)) {
            ValueRow(label = stringResource(R.string.notif_detail_project), value = name)
            Hairline(start = 16f.dp)
            ValueRow(label = stringResource(R.string.notif_detail_channel), value = alert.topic, mono = true)
            Hairline(start = 16f.dp)
            ValueRow(label = stringResource(R.string.notif_detail_priority), value = priorityLabel(priority = alert.priority))
            Hairline(start = 16f.dp)
            ValueRow(label = stringResource(R.string.notif_detail_time), value = exactTime(time = alert.time))
        }
    }
}

@Composable
private fun priorityLabel(priority: Int): String {
    val word = when {
        priority >= 5 -> stringResource(R.string.notif_urgent)
        priority == 4 -> stringResource(R.string.notif_important)
        priority <= 2 -> stringResource(R.string.notif_priority_quiet)
        else -> stringResource(R.string.notif_priority_normal)
    }
    return "$priority · $word"
}
