package com.kopylovis.tossling.notifications.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.kopylovis.tossling.core.presentation.glass.AddRow
import com.kopylovis.tossling.core.presentation.glass.FootNote
import com.kopylovis.tossling.core.presentation.glass.GlassGroup
import com.kopylovis.tossling.core.presentation.glass.GlassToggle
import com.kopylovis.tossling.core.presentation.glass.Hairline
import com.kopylovis.tossling.core.presentation.glass.PageScaffold
import com.kopylovis.tossling.core.presentation.glass.PageTitle
import com.kopylovis.tossling.core.presentation.glass.ProjectAvatar
import com.kopylovis.tossling.core.presentation.glass.SectionLabel
import com.kopylovis.tossling.core.presentation.glass.pressable
import com.kopylovis.tossling.core.presentation.relativeTime
import com.kopylovis.tossling.core.presentation.theme.ProjectColors
import com.kopylovis.tossling.core.presentation.theme.Tossling
import com.kopylovis.tossling.notifications.R

@Composable
internal fun ProjectsContent(
    component: ProjectsComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val palette = Tossling.palette
    PageScaffold(onBack = component::onBackClicked, backLabel = stringResource(R.string.notif_back), modifier = modifier) {
        PageTitle(title = stringResource(R.string.projects_title), subtitle = stringResource(R.string.projects_subtitle))
        SectionLabel(
            text = pluralStringResource(R.plurals.projects_count, state.rows.size, state.rows.size),
            trailing = if (state.rows.isNotEmpty()) stringResource(R.string.projects_muted) else null,
            modifier = Modifier.padding(end = 8f.dp),
        )
        GlassGroup {
            state.rows.forEachIndexed { index, row ->
                if (index > 0) Hairline(start = 70f.dp)
                val project = row.project
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 76f.dp)
                        .padding(start = 16f.dp, end = 12f.dp, top = 10f.dp, bottom = 10f.dp),
                    horizontalArrangement = Arrangement.spacedBy(8f.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .pressable(onClick = { component.onProjectClicked(topic = project.topic) }, pressedScale = 0.98f),
                        horizontalArrangement = Arrangement.spacedBy(14f.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProjectAvatar(initials = project.initials, color = ProjectColors[project.colorIndex], size = 40f.dp, iconPath = project.iconFile)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1f.dp)) {
                            Text(text = project.name, style = Tossling.type.row.copy(fontWeight = FontWeight.SemiBold), color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = project.topic, style = Tossling.type.monoSmall.copy(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal), color = palette.ink2, maxLines = 1)
                            Text(
                                text = row.last?.let { last -> "${last.titleFor(project = project.name).ifBlank { plainText(markdown = last.message) }} · ${relativeTime(time = last.time)}" }
                                    ?: stringResource(R.string.projects_no_events),
                                style = Tossling.type.footnote,
                                color = palette.ink2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 60f.dp, height = 44f.dp)
                            .pressable(onClick = { component.onMuteClicked(topic = project.topic) }, pressedScale = 0.95f),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        GlassToggle(checked = project.isMuted)
                    }
                }
            }
            if (state.rows.isNotEmpty()) Hairline(start = 70f.dp)
            AddRow(label = stringResource(R.string.projects_add), onClick = component::onAddClicked, badgeSize = 40f.dp)
        }
        FootNote(text = stringResource(R.string.projects_note))
    }
}
