package com.kopylovis.tossling.notifications

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.kopylovis.tossling.core.decompose.base.CommonComponent

interface NotificationsFeatureApi {

    fun getFeedComponent(componentContext: ComponentContext): CommonComponent

    fun getAlertComponent(componentContext: ComponentContext, id: String): CommonComponent

    fun getProjectsComponent(componentContext: ComponentContext): CommonComponent

    fun getProjectComponent(componentContext: ComponentContext, topic: String?): CommonComponent

    @Composable
    fun openFeedContent(component: CommonComponent)

    @Composable
    fun openAlertContent(component: CommonComponent)

    @Composable
    fun openProjectsContent(component: CommonComponent)

    @Composable
    fun openProjectContent(component: CommonComponent)
}
