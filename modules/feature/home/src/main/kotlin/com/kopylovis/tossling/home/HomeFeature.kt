package com.kopylovis.tossling.home

import com.kopylovis.tossling.core.di.ModuleDependenciesProvider
import com.kopylovis.tossling.home.di.HomeFeatureModule
import com.kopylovis.tossling.home.di.homeModule
import org.koin.core.module.Module

object HomeFeature {

    var dependenciesProvider: ModuleDependenciesProvider<HomeFeatureDependencies>? = null

    val koinModule: Module = homeModule

    private val featureModule by lazy {
        HomeFeatureModule(
            mediatorDependencies = requireNotNull(value = dependenciesProvider?.getDependencies()),
        )
    }

    val api: HomeFeatureApi by lazy { featureModule.api }
}

interface HomeFeatureDependencies
