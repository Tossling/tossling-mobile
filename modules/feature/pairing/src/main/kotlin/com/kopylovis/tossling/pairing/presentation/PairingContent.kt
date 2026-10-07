package com.kopylovis.tossling.pairing.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.kopylovis.tossling.core.presentation.glass.CapsuleButton
import com.kopylovis.tossling.core.presentation.glass.CapsuleStyle
import com.kopylovis.tossling.core.presentation.glass.CircleBadge
import com.kopylovis.tossling.core.presentation.glass.ConfirmSheet
import com.kopylovis.tossling.core.presentation.glass.FloatingBar
import com.kopylovis.tossling.core.presentation.glass.GlassCard
import com.kopylovis.tossling.core.presentation.glass.GlassIconButton
import com.kopylovis.tossling.core.presentation.glass.GlassScreen
import com.kopylovis.tossling.core.presentation.glass.Hairline
import com.kopylovis.tossling.core.presentation.glass.Spinner
import com.kopylovis.tossling.core.presentation.glass.TosslingIcons
import com.kopylovis.tossling.core.presentation.glass.glass
import com.kopylovis.tossling.core.presentation.theme.Tossling
import com.kopylovis.tossling.pairing.R
import com.kopylovis.tossling.protocol.PairingProblem
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun PairingContent(
    component: PairingComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val context = LocalContext.current
    val scan = {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode -> component.onScanned(raw = barcode.rawValue.orEmpty()) }
            .addOnFailureListener { component.onScanFailed() }
        Unit
    }
    val stage = state.stage

    GlassScreen(modifier = modifier) {
        if (stage is PairingStage.Paired || stage is PairingStage.Connecting) {
            TossScene(stage = stage)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(start = 16f.dp, end = 16f.dp, top = 16f.dp),
            ) {
                GlassIconButton(icon = TosslingIcons.Back, contentDescription = stringResource(R.string.pairing_back), onClick = component::onBackClicked)
                Column(modifier = Modifier.padding(start = 4f.dp, end = 4f.dp, top = 20f.dp), verticalArrangement = Arrangement.spacedBy(4f.dp)) {
                    Text(text = stringResource(R.string.pairing_title), style = Tossling.type.title, color = Tossling.palette.ink)
                    Text(text = stringResource(R.string.pairing_subtitle), style = Tossling.type.body, color = Tossling.palette.ink2)
                }
                Spacer(modifier = Modifier.height(24f.dp))
                AnimatedContent(
                    targetState = stage,
                    contentKey = { it::class },
                    transitionSpec = {
                        (fadeIn(tween(durationMillis = 200)) + scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f)))
                            .togetherWith(fadeOut(tween(durationMillis = 150)))
                    },
                    label = "stage",
                ) { current ->
                    when (current) {
                        is PairingStage.Idle -> Steps(onCopyClicked = component::onCopyCommandClicked)
                        is PairingStage.Connecting -> Unit
                        is PairingStage.Switching -> Steps(onCopyClicked = component::onCopyCommandClicked)
                        is PairingStage.Failed -> Failed(stage = current)
                        is PairingStage.Paired -> Unit
                    }
                }
            }
        }
        FloatingBar(modifier = Modifier.align(Alignment.BottomCenter)) {
            when (stage) {
                is PairingStage.Idle -> CapsuleButton(text = stringResource(R.string.pairing_scan), icon = TosslingIcons.Qr, onClick = scan, modifier = Modifier.weight(1f))
                is PairingStage.Connecting, is PairingStage.Switching -> CapsuleButton(text = stringResource(R.string.pairing_cancel), style = CapsuleStyle.GLASS, onClick = component::onCancelClicked, modifier = Modifier.weight(1f))
                is PairingStage.Failed -> {
                    CapsuleButton(text = stringResource(R.string.pairing_cancel), style = CapsuleStyle.GLASS, onClick = component::onBackClicked)
                    CapsuleButton(text = stringResource(R.string.pairing_scan_again), onClick = scan, modifier = Modifier.weight(1f))
                }

                is PairingStage.Paired -> CapsuleButton(text = stringResource(R.string.pairing_done), onClick = component::onDoneClicked, style = CapsuleStyle.SUCCESS, icon = TosslingIcons.Check, modifier = Modifier.weight(1f))
            }
        }
        val switching = stage as? PairingStage.Switching
        ConfirmSheet(
            visible = switching != null,
            icon = TosslingIcons.Devices,
            title = stringResource(R.string.pairing_switch_title, switching?.to.orEmpty()),
            text = stringResource(R.string.pairing_switch_text, switching?.from?.ifEmpty { null } ?: stringResource(R.string.pairing_switch_nobody)),
            confirm = stringResource(R.string.pairing_switch_confirm),
            cancel = stringResource(R.string.pairing_cancel),
            onConfirm = component::onSwitchConfirmed,
            onDismiss = component::onCancelClicked,
        )
    }
}

@Composable
private fun Steps(onCopyClicked: () -> Unit) {
    val palette = Tossling.palette
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(modifier = Modifier.padding(start = 16f.dp, end = 16f.dp, top = 18f.dp, bottom = 16f.dp), horizontalArrangement = Arrangement.spacedBy(14f.dp)) {
                StepNumber(number = 1)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10f.dp)) {
                    Text(text = stringResource(R.string.pairing_step_mac), style = Tossling.type.row, color = palette.ink, modifier = Modifier.padding(top = 3f.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16f.dp))
                            .background(palette.glassWeak)
                            .border(width = 1f.dp, color = palette.hairline, shape = RoundedCornerShape(16f.dp))
                            .padding(start = 14f.dp, end = 4f.dp, top = 4f.dp, bottom = 4f.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = PairingComponentImpl.COMMAND, style = Tossling.type.mono, color = palette.ink, modifier = Modifier.weight(1f))
                        GlassIconButton(
                            icon = TosslingIcons.Copy,
                            contentDescription = stringResource(R.string.pairing_copy),
                            onClick = onCopyClicked,
                            iconSize = 18f.dp,
                            bare = true,
                            tint = palette.ink2,
                        )
                    }
                }
            }
            Hairline(start = 58f.dp)
            StepRow(number = 2, text = stringResource(R.string.pairing_step_qr))
            Hairline(start = 58f.dp)
            StepRow(number = 3, text = stringResource(R.string.pairing_step_scan))
        }
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(modifier = Modifier.padding(16f.dp), horizontalArrangement = Arrangement.spacedBy(14f.dp), verticalAlignment = Alignment.CenterVertically) {
        StepNumber(number = number)
        Text(text = text, style = Tossling.type.row, color = Tossling.palette.ink)
    }
}

@Composable
private fun StepNumber(number: Int) {
    Box(
        modifier = Modifier
            .size(28f.dp)
            .background(color = Tossling.palette.accentSoft, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = number.toString(), style = Tossling.type.hint.copy(fontWeight = FontWeight.SemiBold), color = Tossling.palette.accentInk)
    }
}

@Composable
private fun Failed(stage: PairingStage.Failed) {
    val palette = Tossling.palette
    val (title, body) = when (stage.problem) {
        PairingProblem.NOT_TOSSLING -> stringResource(R.string.pairing_error_not_tossling) to stringResource(R.string.pairing_error_not_tossling_text)
        PairingProblem.TOKEN -> stringResource(R.string.pairing_error_token) to stringResource(R.string.pairing_error_token_text)
        PairingProblem.NETWORK -> stringResource(R.string.pairing_error_network) to
            stringResource(R.string.pairing_error_network_text, stage.server.ifEmpty { stringResource(R.string.pairing_server) })
    }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22f.dp, end = 22f.dp, top = 28f.dp, bottom = 24f.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10f.dp),
        ) {
            CircleBadge(icon = TosslingIcons.Alert, tint = palette.dangerInk, background = palette.dangerSoft, modifier = Modifier.padding(bottom = 6f.dp))
            Text(text = title, style = Tossling.type.headline, color = palette.ink, textAlign = TextAlign.Center)
            Text(text = body, style = Tossling.type.body, color = palette.ink2, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300f.dp))
            if (stage.problem != PairingProblem.NETWORK) {
                Text(
                    text = PairingComponentImpl.COMMAND,
                    style = Tossling.type.mono.copy(fontSize = Tossling.type.monoSmall.fontSize),
                    color = palette.ink,
                    modifier = Modifier
                        .padding(top = 6f.dp)
                        .clip(RoundedCornerShape(12f.dp))
                        .background(palette.glassWeak)
                        .border(width = 1f.dp, color = palette.hairline, shape = RoundedCornerShape(12f.dp))
                        .padding(horizontal = 14f.dp, vertical = 8f.dp),
                )
            }
        }
    }
}

@Composable
private fun TossScene(stage: PairingStage) {
    val palette = Tossling.palette
    val toss = remember { Animatable(0f) }
    val landed = remember { Animatable(0f) }
    val isDone = stage is PairingStage.Paired
    LaunchedEffect(isDone) {
        if (!isDone) {
            while (true) {
                toss.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = TOSS_MS, easing = FastOutSlowInEasing))
                toss.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = TOSS_MS, easing = FastOutSlowInEasing))
            }
        }
        toss.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = (TOSS_MS * (1f - toss.value)).toInt().coerceAtLeast(250), easing = FastOutSlowInEasing))
        landed.animateTo(targetValue = 1f, animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 32f.dp, end = 32f.dp, bottom = 120f.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(width = 280f.dp, height = 150f.dp)) {
            val arcColor = lerp(palette.accentInk.copy(alpha = 0.35f), palette.success.copy(alpha = 0.8f), landed.value.coerceIn(0f, 1f))
            Canvas(modifier = Modifier.fillMaxSize()) {
                val unit = size.width / ARC_WIDTH
                val path = Path().apply {
                    moveTo(ARC_START.x * unit, ARC_START.y * unit)
                    quadraticTo(ARC_CONTROL.x * unit, ARC_CONTROL.y * unit, ARC_END.x * unit, ARC_END.y * unit)
                }
                drawPath(path = path, color = arcColor, style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 9f.dp.toPx()))))
            }
            Device(icon = TosslingIcons.Phone, modifier = Modifier.offset(x = 0f.dp, y = 50f.dp))
            Device(
                icon = TosslingIcons.Laptop,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = 50f.dp)
                    .graphicsLayer {
                        val bump = 1f + 0.08f * sin(PI.toFloat() * landed.value.coerceIn(0f, 1f))
                        scaleX = bump
                        scaleY = bump
                    },
            )
            val ball = arcPoint(t = toss.value)
            Box(
                modifier = Modifier
                    .offset(x = (ball.x - BALL / 2).dp, y = (ball.y - BALL / 2).dp)
                    .size(BALL.dp)
                    .graphicsLayer { alpha = 1f - landed.value.coerceIn(0f, 1f) }
                    .background(color = palette.accentSoft, shape = CircleShape)
                    .padding(5f.dp)
                    .background(color = palette.accent, shape = CircleShape),
            )
            val top = arcPoint(t = 0.5f)
            Box(
                modifier = Modifier
                    .offset(x = (top.x - CHECK / 2).dp, y = (top.y - CHECK / 2).dp)
                    .size(CHECK.dp)
                    .graphicsLayer {
                        scaleX = landed.value
                        scaleY = landed.value
                        alpha = landed.value.coerceIn(0f, 1f)
                    }
                    .background(color = palette.success, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = TosslingIcons.Check, contentDescription = null, tint = palette.onSuccess, modifier = Modifier.size(20f.dp))
            }
        }
        Spacer(modifier = Modifier.height(30f.dp))
        AnimatedContent(
            targetState = stage as? PairingStage.Paired,
            transitionSpec = { fadeIn(tween(durationMillis = 250)).togetherWith(fadeOut(tween(durationMillis = 150))) },
            contentKey = { it != null },
            label = "tossText",
        ) { paired ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(if (paired != null) R.string.pairing_paired_title else R.string.pairing_connecting),
                    style = Tossling.type.title,
                    color = palette.ink,
                )
                Spacer(modifier = Modifier.height(12f.dp))
                Text(
                    text = if (paired != null) stringResource(R.string.pairing_paired_text, paired.macName) else stringResource(R.string.pairing_connecting_note),
                    style = Tossling.type.row.copy(fontWeight = FontWeight.Normal),
                    color = palette.ink2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 300f.dp),
                )
                val server = (stage as? PairingStage.Connecting)?.server ?: paired?.server.orEmpty()
                if (server.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(18f.dp))
                    Text(
                        text = if (paired != null) stringResource(R.string.pairing_paired_key, server) else server,
                        style = Tossling.type.monoSmall,
                        color = palette.ink2,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14f.dp))
                            .background(palette.glassWeak)
                            .border(width = 1f.dp, color = palette.glassStroke, shape = RoundedCornerShape(14f.dp))
                            .padding(horizontal = 14f.dp, vertical = 8f.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Device(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(80f.dp)
            .glass(shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Tossling.palette.ink, modifier = Modifier.size(30f.dp))
    }
}

private const val ARC_WIDTH = 280f
private const val TOSS_MS = 900
private const val BALL = 26f
private const val CHECK = 38f
private val ARC_START = Offset(84f, 84f)
private val ARC_CONTROL = Offset(140f, 10f)
private val ARC_END = Offset(196f, 84f)

private fun arcPoint(t: Float): Offset {
    val u = 1f - t
    return Offset(
        x = u * u * ARC_START.x + 2f * u * t * ARC_CONTROL.x + t * t * ARC_END.x,
        y = u * u * ARC_START.y + 2f * u * t * ARC_CONTROL.y + t * t * ARC_END.y,
    )
}
