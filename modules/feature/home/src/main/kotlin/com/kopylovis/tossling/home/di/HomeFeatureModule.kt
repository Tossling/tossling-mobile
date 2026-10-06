package com.kopylovis.tossling.home.di

import com.kopylovis.tossling.home.HomeFeatureApi
import com.kopylovis.tossling.home.HomeFeatureApiImpl
import com.kopylovis.tossling.home.HomeFeatureDependencies
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.data.SyncSettings
import com.kopylovis.tossling.sync.entry.ClipSender
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class HomeFeatureModule(
    @Suppress("unused") private val mediatorDependencies: HomeFeatureDependencies,
) : KoinComponent {

    private val repository: ClipRepository by inject()
    private val sender: ClipSender by inject()
    private val settings: SyncSettings by inject()

    val api: HomeFeatureApi by lazy {
        HomeFeatureApiImpl(repository = repository, sender = sender, settings = settings)
    }
}
