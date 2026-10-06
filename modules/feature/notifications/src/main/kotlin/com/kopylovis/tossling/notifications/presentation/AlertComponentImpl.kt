package com.kopylovis.tossling.notifications.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.BaseComponent
import com.kopylovis.tossling.core.presentation.tr
import com.kopylovis.tossling.navigation.GlobalNavigator
import com.kopylovis.tossling.sync.alerts.AlertRepository
import com.kopylovis.tossling.sync.data.ClipRepository
import org.koin.core.component.inject

internal class AlertComponentImpl(
    componentContext: ComponentContext,
    private val alerts: AlertRepository,
    private val clips: ClipRepository,
    private val id: String,
) : BaseComponent(componentContext), AlertComponent {

    private val globalNavigator: GlobalNavigator by inject()

    override val state: Value<AlertScreenState> =
        combine(alerts.alerts, alerts.projects) { list, projects ->
            val alert = list.firstOrNull { it.id == id }
            AlertScreenState(alert = alert, project = alert?.let { found -> projects.firstOrNull { it.topic == found.topic } })
        }.asValue()

    init {
        alerts.markRead(id = id)
    }

    override fun onBackClicked() {
        globalNavigator.pop()
    }

    override fun onDeleteClicked() {
        alerts.delete(id = id)
        messenger.done(tr("Deleted", "Удалено"))
        globalNavigator.pop()
    }

    override fun onCopyClicked() {
        val alert = state.value.alert ?: return
        clips.copyText(text = listOf(alert.title, plainText(markdown = alert.message)).filter { it.isNotBlank() }.joinToString(separator = "\n"))
        messenger.done(tr("Copied", "Скопировано"))
    }
}
