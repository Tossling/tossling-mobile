package com.kopylovis.tossling.mediators.mediator

import com.kopylovis.tossling.core.di.Mediator
import com.kopylovis.tossling.core.di.ModuleDependenciesProvider
import com.kopylovis.tossling.devices.DevicesFeature
import com.kopylovis.tossling.devices.DevicesFeatureApi
import com.kopylovis.tossling.devices.DevicesFeatureDependencies

internal class DevicesMediator : Mediator<DevicesFeatureApi> {

    init {
        DevicesFeature.dependenciesProvider = ModuleDependenciesProvider { getDependencies() }
    }

    override fun getApi(): DevicesFeatureApi = DevicesFeature.api

    private fun getDependencies(): DevicesFeatureDependencies = object : DevicesFeatureDependencies {}
}
