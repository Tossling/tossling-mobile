package com.kopylovis.tossling.mediators.mediator

import com.kopylovis.tossling.core.di.Mediator
import com.kopylovis.tossling.core.di.ModuleDependenciesProvider
import com.kopylovis.tossling.home.HomeFeature
import com.kopylovis.tossling.home.HomeFeatureApi
import com.kopylovis.tossling.home.HomeFeatureDependencies

internal class HomeMediator : Mediator<HomeFeatureApi> {

    init {
        HomeFeature.dependenciesProvider = ModuleDependenciesProvider { getDependencies() }
    }

    override fun getApi(): HomeFeatureApi = HomeFeature.api

    private fun getDependencies(): HomeFeatureDependencies = object : HomeFeatureDependencies {}
}
