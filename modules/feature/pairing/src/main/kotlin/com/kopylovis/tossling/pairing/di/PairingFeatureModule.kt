package com.kopylovis.tossling.pairing.di

import com.kopylovis.tossling.pairing.PairingFeatureApi
import com.kopylovis.tossling.pairing.PairingFeatureApiImpl
import com.kopylovis.tossling.pairing.PairingFeatureDependencies
import com.kopylovis.tossling.sync.data.ClipRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class PairingFeatureModule(
    @Suppress("unused") private val mediatorDependencies: PairingFeatureDependencies,
) : KoinComponent {

    private val repository: ClipRepository by inject()

    val api: PairingFeatureApi by lazy {
        PairingFeatureApiImpl(repository = repository)
    }
}
