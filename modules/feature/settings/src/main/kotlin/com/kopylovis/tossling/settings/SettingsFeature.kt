package com.kopylovis.tossling.settings

import com.kopylovis.tossling.core.di.ModuleDependenciesProvider
import com.kopylovis.tossling.settings.di.SettingsFeatureModule
import com.kopylovis.tossling.settings.di.settingsModule
import org.koin.core.module.Module

object SettingsFeature {

    var dependenciesProvider: ModuleDependenciesProvider<SettingsFeatureDependencies>? = null

    val koinModule: Module = settingsModule

    private val featureModule by lazy {
        SettingsFeatureModule(
            mediatorDependencies = requireNotNull(value = dependenciesProvider?.getDependencies()),
        )
    }

    val api: SettingsFeatureApi by lazy { featureModule.api }
}

interface SettingsFeatureDependencies
