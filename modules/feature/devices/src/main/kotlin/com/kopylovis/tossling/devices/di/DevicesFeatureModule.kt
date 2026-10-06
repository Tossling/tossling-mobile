package com.kopylovis.tossling.devices.di

import com.kopylovis.tossling.devices.DevicesFeatureApi
import com.kopylovis.tossling.devices.DevicesFeatureApiImpl
import com.kopylovis.tossling.devices.DevicesFeatureDependencies
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.data.SyncCoordinator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class DevicesFeatureModule(
    @Suppress("unused") private val mediatorDependencies: DevicesFeatureDependencies,
) : KoinComponent {

    private val repository: ClipRepository by inject()
    private val coordinator: SyncCoordinator by inject()

    val api: DevicesFeatureApi by lazy {
        DevicesFeatureApiImpl(repository = repository, coordinator = coordinator)
    }
}
