package com.kopylovis.tossling.mediators.mediator

import com.kopylovis.tossling.core.di.Mediator
import com.kopylovis.tossling.core.di.ModuleDependenciesProvider
import com.kopylovis.tossling.notifications.NotificationsFeature
import com.kopylovis.tossling.notifications.NotificationsFeatureApi
import com.kopylovis.tossling.notifications.NotificationsFeatureDependencies

internal class NotificationsMediator : Mediator<NotificationsFeatureApi> {

    init {
        NotificationsFeature.dependenciesProvider = ModuleDependenciesProvider { getDependencies() }
    }

    override fun getApi(): NotificationsFeatureApi = NotificationsFeature.api

    private fun getDependencies(): NotificationsFeatureDependencies = object : NotificationsFeatureDependencies {}
}
