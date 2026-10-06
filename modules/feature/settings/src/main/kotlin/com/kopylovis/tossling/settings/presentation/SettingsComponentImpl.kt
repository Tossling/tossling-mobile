package com.kopylovis.tossling.settings.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.asValueUtil
import com.kopylovis.tossling.core.decompose.base.BaseComponent
import com.kopylovis.tossling.core.presentation.tr
import com.kopylovis.tossling.navigation.GlobalNavigator
import com.kopylovis.tossling.settings.BuildConfig
import com.kopylovis.tossling.sync.alerts.AlertRepository
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.data.SyncCoordinator
import com.kopylovis.tossling.sync.data.SyncSettings
import kotlinx.coroutines.flow.combine
import org.koin.core.component.inject

internal class SettingsComponentImpl(
    componentContext: ComponentContext,
    private val coordinator: SyncCoordinator,
    private val settings: SyncSettings,
) : BaseComponent(componentContext), SettingsComponent {

    private val globalNavigator: GlobalNavigator by inject()
    private val alerts: AlertRepository by inject()
    private val clips: ClipRepository by inject()

    override val state: Value<SettingsScreenState> =
        combine(settings.isPaused, settings.sendsImages, settings.isInstant, settings.isQuietHours) { paused, images, instant, quiet ->
            SettingsScreenState(
                isPaused = paused,
                sendsImages = images,
                isInstant = instant && coordinator.isPushAvailable,
                isInstantAvailable = coordinator.isPushAvailable,
                isQuietHours = quiet,
                version = BuildConfig.APP_VERSION,
            )
        }
            .combine(settings.isLive) { state, live -> state.copy(isLive = live) }
            .combine(settings.serverPush) { state, serverPush -> state.copy(isServerPushless = serverPush == false) }
            .combine(alerts.projects) { state, projects -> state.copy(projects = projects.size) }
            .combine(clips.devices) { state, devices -> state.copy(devices = devices.size) }
            .asValueUtil(initialValue = SettingsScreenState(version = BuildConfig.APP_VERSION), lifecycle = lifecycle)

    override fun onPauseClicked() {
        val paused = !settings.isPaused.value
        settings.setPaused(value = paused)
        messenger.info(if (paused) tr("Sending is paused", "Отправка на паузе") else tr("Sending is on", "Отправка включена"))
    }

    override fun onImagesClicked() {
        settings.setSendsImages(value = !settings.sendsImages.value)
    }

    override fun onInstantClicked() {
        if (!coordinator.isPushAvailable) {
            messenger.info(tr("Firebase is not set up in this build", "В этой сборке не настроен Firebase"))
            return
        }
        launchCoroutine { coordinator.setInstant(value = !settings.isInstant.value) }
    }

    override fun onLiveClicked() {
        coordinator.setLive(value = !settings.isLive.value)
    }

    override fun onQuietHoursClicked() {
        settings.setQuietHours(value = !settings.isQuietHours.value)
    }

    override fun onProjectsClicked() {
        globalNavigator.pushNew(configuration = GlobalNavigator.Config.Projects)
    }

    override fun onDevicesClicked() {
        globalNavigator.pushNew(configuration = GlobalNavigator.Config.Devices)
    }

    override fun onBackClicked() {
        globalNavigator.pop()
    }
}
