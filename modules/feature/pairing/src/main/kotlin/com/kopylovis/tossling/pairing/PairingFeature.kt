package com.kopylovis.tossling.pairing

import com.kopylovis.tossling.core.di.ModuleDependenciesProvider
import com.kopylovis.tossling.pairing.di.PairingFeatureModule
import com.kopylovis.tossling.pairing.di.pairingModule
import org.koin.core.module.Module

object PairingFeature {

    var dependenciesProvider: ModuleDependenciesProvider<PairingFeatureDependencies>? = null

    val koinModule: Module = pairingModule

    private val featureModule by lazy {
        PairingFeatureModule(
            mediatorDependencies = requireNotNull(value = dependenciesProvider?.getDependencies()),
        )
    }

    val api: PairingFeatureApi by lazy { featureModule.api }
}

interface PairingFeatureDependencies
