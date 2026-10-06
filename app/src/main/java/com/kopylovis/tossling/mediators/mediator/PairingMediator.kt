package com.kopylovis.tossling.mediators.mediator

import com.kopylovis.tossling.core.di.Mediator
import com.kopylovis.tossling.core.di.ModuleDependenciesProvider
import com.kopylovis.tossling.pairing.PairingFeature
import com.kopylovis.tossling.pairing.PairingFeatureApi
import com.kopylovis.tossling.pairing.PairingFeatureDependencies

internal class PairingMediator : Mediator<PairingFeatureApi> {

    init {
        PairingFeature.dependenciesProvider = ModuleDependenciesProvider { getDependencies() }
    }

    override fun getApi(): PairingFeatureApi = PairingFeature.api

    private fun getDependencies(): PairingFeatureDependencies = object : PairingFeatureDependencies {}
}
