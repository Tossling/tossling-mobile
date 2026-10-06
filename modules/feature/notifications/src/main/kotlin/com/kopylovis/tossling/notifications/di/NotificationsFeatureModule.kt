package com.kopylovis.tossling.notifications.di

import com.kopylovis.tossling.notifications.NotificationsFeatureApi
import com.kopylovis.tossling.notifications.NotificationsFeatureApiImpl
import com.kopylovis.tossling.notifications.NotificationsFeatureDependencies
import com.kopylovis.tossling.sync.alerts.AlertRepository
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.data.SyncCoordinator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class NotificationsFeatureModule(
    @Suppress("unused") private val mediatorDependencies: NotificationsFeatureDependencies,
) : KoinComponent {

    private val alerts: AlertRepository by inject()
    private val clips: ClipRepository by inject()
    private val coordinator: SyncCoordinator by inject()

    val api: NotificationsFeatureApi by lazy {
        NotificationsFeatureApiImpl(alerts = alerts, clips = clips, coordinator = coordinator)
    }
}
