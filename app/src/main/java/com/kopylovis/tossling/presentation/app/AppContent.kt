package com.kopylovis.tossling.presentation.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kopylovis.tossling.R
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.experimental.stack.ChildStack
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.PredictiveBackParams
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.stackAnimation
import com.kopylovis.tossling.core.presentation.glass.Island
import com.kopylovis.tossling.core.presentation.messages.AppMessage
import com.kopylovis.tossling.core.presentation.messages.AppMessageKind
import com.kopylovis.tossling.core.presentation.messages.AppMessenger
import com.kopylovis.tossling.core.presentation.theme.Tossling
import com.kopylovis.tossling.mediators.MediatorManager
import com.kopylovis.tossling.presentation.main.MainContent
import com.kopylovis.tossling.sync.data.ClipRepository
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@OptIn(ExperimentalDecomposeApi::class)
@Composable
internal fun AppContent(
    component: AppComponent,
    modifier: Modifier = Modifier,
) {
    val messenger: AppMessenger = koinInject()
    var island by remember { mutableStateOf<AppMessage?>(null) }

    val repository: ClipRepository = koinInject()
    val joinedText = stringResource(R.string.room_joined)
    LaunchedEffect(messenger) {
        messenger.messages.collect { message -> island = message }
    }
    LaunchedEffect(repository) {
        repository.joined.collect { name -> messenger.device(joinedText.format(name)) }
    }
    LaunchedEffect(island) {
        val shown = island ?: return@LaunchedEffect
        if (shown.kind == AppMessageKind.BUSY) {
            delay(BUSY_TIMEOUT_MS)
        } else {
            delay(if (shown.kind == AppMessageKind.ERROR || shown.kind == AppMessageKind.DEVICE) ERROR_MS else SHOWN_MS)
        }
        if (island == shown) island = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Tossling.palette.background),
    ) {
        ChildStack(
            stack = component.stack,
            modifier = Modifier.fillMaxSize(),
            animation = stackAnimation(
                animator = slide(),
                predictiveBackParams = {
                    PredictiveBackParams(
                        backHandler = component.backHandler,
                        onBack = component::onBackClicked,
                        animatable = ::PageBackAnimatable,
                    )
                },
            ),
        ) { created ->
            when (val child = created.instance) {
                is AppComponent.Child.WelcomeChild ->
                    MediatorManager.pairingMediator.getApi().openWelcomeContent(component = child.component)

                is AppComponent.Child.PairingChild ->
                    MediatorManager.pairingMediator.getApi().openPairingContent(component = child.component)

                is AppComponent.Child.MainChild -> MainContent(component = child.component)

                is AppComponent.Child.SettingsChild ->
                    MediatorManager.settingsMediator.getApi().openSettingsContent(component = child.component)

                is AppComponent.Child.DevicesChild ->
                    MediatorManager.devicesMediator.getApi().openDevicesContent(component = child.component)

                is AppComponent.Child.DeviceChild ->
                    MediatorManager.devicesMediator.getApi().openDeviceContent(component = child.component)

                is AppComponent.Child.AddMacChild ->
                    MediatorManager.devicesMediator.getApi().openAddMacContent(component = child.component)

                is AppComponent.Child.ProjectsChild ->
                    MediatorManager.notificationsMediator.getApi().openProjectsContent(component = child.component)

                is AppComponent.Child.ProjectChild ->
                    MediatorManager.notificationsMediator.getApi().openProjectContent(component = child.component)

                is AppComponent.Child.AlertChild ->
                    MediatorManager.notificationsMediator.getApi().openAlertContent(component = child.component)
            }
        }
        Island(message = island, modifier = Modifier.align(Alignment.TopCenter))
    }
}

private const val SHOWN_MS = 1_500L
private const val ERROR_MS = 3_000L
private const val BUSY_TIMEOUT_MS = 20_000L
