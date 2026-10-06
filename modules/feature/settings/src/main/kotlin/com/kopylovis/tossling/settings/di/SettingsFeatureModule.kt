package com.kopylovis.tossling.settings.di

import com.kopylovis.tossling.settings.SettingsFeatureApi
import com.kopylovis.tossling.settings.SettingsFeatureApiImpl
import com.kopylovis.tossling.settings.SettingsFeatureDependencies
import com.kopylovis.tossling.sync.data.SyncCoordinator
import com.kopylovis.tossling.sync.data.SyncSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class SettingsFeatureModule(
    @Suppress("unused") private val mediatorDependencies: SettingsFeatureDependencies,
) : KoinComponent {

    private val coordinator: SyncCoordinator by inject()
    private val settings: SyncSettings by inject()

    val api: SettingsFeatureApi by lazy {
        SettingsFeatureApiImpl(coordinator = coordinator, settings = settings)
    }
}
