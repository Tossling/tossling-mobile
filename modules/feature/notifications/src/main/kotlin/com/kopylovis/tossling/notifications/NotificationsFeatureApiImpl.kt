package com.kopylovis.tossling.notifications

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.kopylovis.tossling.core.decompose.base.CommonComponent
import com.kopylovis.tossling.notifications.presentation.AlertComponent
import com.kopylovis.tossling.notifications.presentation.AlertComponentImpl
import com.kopylovis.tossling.notifications.presentation.AlertContent
import com.kopylovis.tossling.notifications.presentation.FeedComponent
import com.kopylovis.tossling.notifications.presentation.FeedComponentImpl
import com.kopylovis.tossling.notifications.presentation.FeedContent
import com.kopylovis.tossling.notifications.presentation.ProjectComponent
import com.kopylovis.tossling.notifications.presentation.ProjectComponentImpl
import com.kopylovis.tossling.notifications.presentation.ProjectContent
import com.kopylovis.tossling.notifications.presentation.ProjectsComponent
import com.kopylovis.tossling.notifications.presentation.ProjectsComponentImpl
import com.kopylovis.tossling.notifications.presentation.ProjectsContent
import com.kopylovis.tossling.sync.alerts.AlertRepository
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.data.SyncCoordinator

internal class NotificationsFeatureApiImpl(
    private val alerts: AlertRepository,
    private val clips: ClipRepository,
    private val coordinator: SyncCoordinator,
) : NotificationsFeatureApi {

    override fun getFeedComponent(componentContext: ComponentContext): CommonComponent =
        FeedComponentImpl(componentContext = componentContext, alerts = alerts, coordinator = coordinator)

    override fun getAlertComponent(componentContext: ComponentContext, id: String): CommonComponent =
        AlertComponentImpl(componentContext = componentContext, alerts = alerts, clips = clips, id = id)

    override fun getProjectsComponent(componentContext: ComponentContext): CommonComponent =
        ProjectsComponentImpl(componentContext = componentContext, alerts = alerts)

    override fun getProjectComponent(componentContext: ComponentContext, topic: String?): CommonComponent =
        ProjectComponentImpl(componentContext = componentContext, alerts = alerts, clips = clips, topic = topic)

    @Composable
    override fun openFeedContent(component: CommonComponent) {
        FeedContent(component = component as FeedComponent)
    }

    @Composable
    override fun openAlertContent(component: CommonComponent) {
        AlertContent(component = component as AlertComponent)
    }

    @Composable
    override fun openProjectsContent(component: CommonComponent) {
        ProjectsContent(component = component as ProjectsComponent)
    }

    @Composable
    override fun openProjectContent(component: CommonComponent) {
        ProjectContent(component = component as ProjectComponent)
    }
}
