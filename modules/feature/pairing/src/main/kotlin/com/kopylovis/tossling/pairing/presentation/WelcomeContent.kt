package com.kopylovis.tossling.pairing.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.kopylovis.tossling.core.presentation.glass.CapsuleButton
import com.kopylovis.tossling.core.presentation.glass.CapsuleStyle
import com.kopylovis.tossling.core.presentation.glass.GlassGroup
import com.kopylovis.tossling.core.presentation.glass.FloatingBar
import com.kopylovis.tossling.core.presentation.glass.GlassScreen
import com.kopylovis.tossling.core.presentation.glass.TossyIcons
import com.kopylovis.tossling.core.presentation.glass.TossyLogo
import com.kopylovis.tossling.core.presentation.theme.Tossy
import com.kopylovis.tossling.pairing.R
import com.kopylovis.tossling.sync.data.PairingProblem
import com.kopylovis.tossling.sync.data.SavedRooms

@Composable
internal fun WelcomeContent(
    component: WelcomeComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val saved = state.saved
    GlassScreen(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 36f.dp, end = 36f.dp, bottom = 120f.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TossyLogo(
                modifier = Modifier
                    .shadow(elevation = 18f.dp, shape = RoundedCornerShape(34f.dp), ambientColor = Tossy.palette.shadow, spotColor = Tossy.palette.shadow)
                    .clip(RoundedCornerShape(34f.dp)),
            )
            Spacer(modifier = Modifier.height(26f.dp))
            Text(text = stringResource(R.string.app_title), style = Tossy.type.largeTitle, color = Tossy.palette.ink)
            Spacer(modifier = Modifier.height(14f.dp))
            Text(
                text = stringResource(R.string.welcome_lead),
                style = Tossy.type.lead,
                color = Tossy.palette.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320f.dp),
            )
            Spacer(modifier = Modifier.height(14f.dp))
            Text(
                text = stringResource(R.string.welcome_note),
                style = Tossy.type.body,
                color = Tossy.palette.ink2,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300f.dp),
            )
            if (saved != null) {
                Spacer(modifier = Modifier.height(28f.dp))
                SavedRoomCard(saved = saved, problem = state.problem)
                CapsuleButton(
                    text = stringResource(R.string.welcome_pair_other),
                    onClick = component::onPairClicked,
                    style = CapsuleStyle.PLAIN,
                    modifier = Modifier.padding(top = 6f.dp),
                )
            }
        }
        FloatingBar(modifier = Modifier.align(Alignment.BottomCenter)) {
            if (saved != null) {
                CapsuleButton(
                    text = stringResource(R.string.welcome_restore),
                    icon = TossyIcons.Laptop,
                    onClick = component::onRestoreClicked,
                    enabled = !state.isRestoring,
                    modifier = Modifier.weight(1f),
                )
            } else {
                CapsuleButton(
                    text = stringResource(R.string.welcome_pair),
                    icon = TossyIcons.Laptop,
                    onClick = component::onPairClicked,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SavedRoomCard(saved: SavedRooms, problem: PairingProblem?) {
    val palette = Tossy.palette
    GlassGroup(modifier = Modifier.widthIn(max = 360f.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 20f.dp, vertical = 18f.dp),
            verticalArrangement = Arrangement.spacedBy(6f.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = stringResource(R.string.welcome_saved_title), style = Tossy.type.footnote, color = palette.ink2, textAlign = TextAlign.Center)
            Text(text = stringResource(R.string.welcome_saved_room, saved.title), style = Tossy.type.lead.copy(fontWeight = FontWeight.SemiBold), color = palette.ink, textAlign = TextAlign.Center)
            Text(text = saved.host, style = Tossy.type.footnote, color = palette.ink2, textAlign = TextAlign.Center)
            Text(
                text = when (problem) {
                    null -> stringResource(R.string.welcome_saved_note)
                    PairingProblem.TOKEN -> stringResource(R.string.welcome_restore_token)
                    PairingProblem.NETWORK -> stringResource(R.string.welcome_restore_network, saved.host)
                    PairingProblem.NOT_TOSSY -> stringResource(R.string.welcome_restore_broken)
                },
                style = Tossy.type.body,
                color = if (problem == null) palette.ink2 else palette.dangerInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4f.dp),
            )
        }
    }
}
