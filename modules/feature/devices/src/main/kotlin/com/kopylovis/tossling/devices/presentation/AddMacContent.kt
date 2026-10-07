package com.kopylovis.tossling.devices.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.kopylovis.tossling.core.presentation.glass.CapsuleButton
import com.kopylovis.tossling.core.presentation.glass.CapsuleStyle
import com.kopylovis.tossling.core.presentation.glass.CircleBadge
import com.kopylovis.tossling.core.presentation.glass.FloatingBar
import com.kopylovis.tossling.core.presentation.glass.GlassGroup
import com.kopylovis.tossling.core.presentation.glass.GlassIconButton
import com.kopylovis.tossling.core.presentation.glass.Hairline
import com.kopylovis.tossling.core.presentation.glass.PageScaffold
import com.kopylovis.tossling.core.presentation.glass.PageTitle
import com.kopylovis.tossling.core.presentation.glass.Spinner
import com.kopylovis.tossling.core.presentation.glass.TosslingIcons
import com.kopylovis.tossling.core.presentation.theme.Tossling
import com.kopylovis.tossling.devices.R

@Composable
internal fun AddMacContent(
    component: AddMacComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val palette = Tossling.palette
    PageScaffold(
        onBack = component::onBackClicked,
        backLabel = stringResource(R.string.devices_back),
        modifier = modifier,
        bottomPadding = 140f.dp,
        overlay = {
            FloatingBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                val joined = state.joined
                if (joined == null) {
                    CapsuleButton(
                        text = stringResource(R.string.add_scan),
                        icon = TosslingIcons.Qr,
                        onClick = component::onScanClicked,
                        style = CapsuleStyle.GLASS,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    CapsuleButton(text = stringResource(R.string.add_done), onClick = component::onDoneClicked, modifier = Modifier.weight(1f))
                }
            }
        },
    ) {
        PageTitle(title = stringResource(R.string.add_title), subtitle = stringResource(R.string.add_subtitle), large = false)
        GlassGroup(modifier = Modifier.padding(top = 24f.dp)) {
            Step(number = "1", text = stringResource(R.string.add_step_invite)) {
                CommandBox(command = "tossling invite", onCopy = component::onCopyInvite)
            }
            Hairline(start = 58f.dp)
            Step(number = "2", text = stringResource(R.string.add_step_join)) {
                CommandBox(command = stringResource(R.string.add_join_example, state.host), onCopy = component::onCopyJoin)
                Text(text = stringResource(R.string.add_join_note), style = Tossling.type.footnote, color = palette.ink2)
            }
            Hairline(start = 58f.dp)
            AnimatedContent(
                targetState = state.joined,
                transitionSpec = { (scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f)) + fadeIn()) togetherWith fadeOut() },
                label = "joined",
            ) { joined ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16f.dp),
                    horizontalArrangement = Arrangement.spacedBy(14f.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (joined == null) {
                        Box(modifier = Modifier.size(28f.dp), contentAlignment = Alignment.Center) { Spinner(size = 20f.dp, stroke = 2.5f.dp) }
                        Text(text = stringResource(R.string.add_waiting), style = Tossling.type.row.copy(fontWeight = FontWeight.Normal), color = palette.ink2)
                    } else {
                        CircleBadge(icon = TosslingIcons.Check, tint = palette.onAccent, background = palette.accent, size = 28f.dp, iconSize = 14f.dp)
                        Text(text = stringResource(R.string.add_joined, joined), style = Tossling.type.row.copy(fontWeight = FontWeight.SemiBold), color = palette.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun Step(number: String, text: String, content: @Composable () -> Unit) {
    val palette = Tossling.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16f.dp),
        horizontalArrangement = Arrangement.spacedBy(14f.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28f.dp)
                .background(color = palette.accentSoft, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number, style = Tossling.type.hint.copy(fontWeight = FontWeight.SemiBold), color = palette.accentInk)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10f.dp)) {
            Text(text = text, style = Tossling.type.row, color = palette.ink, modifier = Modifier.padding(top = 3f.dp))
            content()
        }
    }
}

@Composable
private fun CommandBox(command: String, onCopy: () -> Unit) {
    val palette = Tossling.palette
    val shape = RoundedCornerShape(16f.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.glassWeak)
            .border(width = 1f.dp, color = palette.hairline, shape = shape)
            .padding(start = 14f.dp, end = 4f.dp, top = 4f.dp, bottom = 4f.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = command,
            style = Tossling.type.monoSmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = palette.ink,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6f.dp),
        )
        GlassIconButton(icon = TosslingIcons.Copy, contentDescription = stringResource(R.string.add_copy), onClick = onCopy, iconSize = 18f.dp, bare = true, tint = palette.ink2)
    }
}
