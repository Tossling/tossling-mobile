package com.kopylovis.tossling.devices

import com.kopylovis.tossling.core.di.ModuleDependenciesProvider
import com.kopylovis.tossling.devices.di.DevicesFeatureModule
import com.kopylovis.tossling.devices.di.devicesModule
import org.koin.core.module.Module

object DevicesFeature {

    var dependenciesProvider: ModuleDependenciesProvider<DevicesFeatureDependencies>? = null

    val koinModule: Module = devicesModule

    private val featureModule by lazy {
        DevicesFeatureModule(
            mediatorDependencies = requireNotNull(value = dependenciesProvider?.getDependencies()),
        )
    }

    val api: DevicesFeatureApi by lazy { featureModule.api }
}

interface DevicesFeatureDependencies
