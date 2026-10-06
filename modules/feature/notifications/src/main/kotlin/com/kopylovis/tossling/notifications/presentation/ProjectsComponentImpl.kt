package com.kopylovis.tossling.notifications.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.BaseComponent
import com.kopylovis.tossling.core.presentation.tr
import com.kopylovis.tossling.navigation.GlobalNavigator
import com.kopylovis.tossling.sync.alerts.AlertRepository
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.component.inject

internal class ProjectsComponentImpl(
    componentContext: ComponentContext,
    private val alerts: AlertRepository,
) : BaseComponent(componentContext), ProjectsComponent {

    private val globalNavigator: GlobalNavigator by inject()

    override val state: Value<ProjectsScreenState> =
        combine(alerts.projects, alerts.alerts) { projects, list ->
            val latest = list.groupBy { it.topic }.mapValues { (_, items) -> items.maxByOrNull { it.time } }
            ProjectsScreenState(rows = projects.map { ProjectRow(project = it, last = latest[it.topic]) }.toImmutableList())
        }.asValue()

    init {
        launchCoroutine(onError = {}) { alerts.syncProjects(force = true) }
    }

    override fun onBackClicked() {
        globalNavigator.pop()
    }

    override fun onProjectClicked(topic: String) {
        globalNavigator.pushNew(configuration = GlobalNavigator.Config.Project(topic = topic))
    }

    override fun onMuteClicked(topic: String) {
        val project = alerts.project(topic = topic) ?: return
        alerts.updateProject(topic = topic, isMuted = !project.isMuted)
        messenger.info(if (project.isMuted) tr("Sound on", "Звук включён") else tr("Muted", "Без звука"))
    }

    override fun onAddClicked() {
        globalNavigator.pushNew(configuration = GlobalNavigator.Config.Project(topic = null))
    }
}
