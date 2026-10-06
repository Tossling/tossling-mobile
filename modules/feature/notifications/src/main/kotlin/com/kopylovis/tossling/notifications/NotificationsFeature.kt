package com.kopylovis.tossling.notifications

import com.kopylovis.tossling.core.di.ModuleDependenciesProvider
import com.kopylovis.tossling.notifications.di.NotificationsFeatureModule
import com.kopylovis.tossling.notifications.di.notificationsModule
import org.koin.core.module.Module

object NotificationsFeature {

    var dependenciesProvider: ModuleDependenciesProvider<NotificationsFeatureDependencies>? = null

    val koinModule: Module = notificationsModule

    private val featureModule by lazy {
        NotificationsFeatureModule(
            mediatorDependencies = requireNotNull(value = dependenciesProvider?.getDependencies()),
        )
    }

    val api: NotificationsFeatureApi by lazy { featureModule.api }
}

interface NotificationsFeatureDependencies
