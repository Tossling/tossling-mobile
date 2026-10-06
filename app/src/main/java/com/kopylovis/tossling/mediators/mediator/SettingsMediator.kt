package com.kopylovis.tossling.mediators.mediator

import com.kopylovis.tossling.core.di.Mediator
import com.kopylovis.tossling.core.di.ModuleDependenciesProvider
import com.kopylovis.tossling.settings.SettingsFeature
import com.kopylovis.tossling.settings.SettingsFeatureApi
import com.kopylovis.tossling.settings.SettingsFeatureDependencies

internal class SettingsMediator : Mediator<SettingsFeatureApi> {

    init {
        SettingsFeature.dependenciesProvider = ModuleDependenciesProvider { getDependencies() }
    }

    override fun getApi(): SettingsFeatureApi = SettingsFeature.api

    private fun getDependencies(): SettingsFeatureDependencies = object : SettingsFeatureDependencies {}
}
