package com.kopylovis.tossling.notifications.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.asValueUtil
import com.kopylovis.tossling.core.decompose.base.BaseComponent
import com.kopylovis.tossling.core.presentation.dayLabel
import com.kopylovis.tossling.core.presentation.tr
import com.kopylovis.tossling.navigation.GlobalNavigator
import com.kopylovis.tossling.protocol.alerts.Alert
import com.kopylovis.tossling.protocol.alerts.Project
import com.kopylovis.tossling.sync.alerts.AlertRepository
import com.kopylovis.tossling.sync.data.SyncCoordinator
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import org.koin.core.component.inject

internal class FeedComponentImpl(
    componentContext: ComponentContext,
    private val alerts: AlertRepository,
    private val coordinator: SyncCoordinator,
) : BaseComponent(componentContext), FeedComponent {

    private val globalNavigator: GlobalNavigator by inject()
    private val filter = MutableStateFlow<String?>(null)
    private val isRefreshing = MutableStateFlow(false)
    private val isClearSheetVisible = MutableStateFlow(false)

    override val state: Value<FeedScreenState> =
        combine(alerts.alerts, alerts.projects, filter, ::feedState)
            .combine(isRefreshing) { state, refreshing -> state.copy(isRefreshing = refreshing) }
            .combine(isClearSheetVisible) { state, visible -> state.copy(isClearSheetVisible = visible) }
            .asValueUtil(initialValue = feedState(list = alerts.alerts.value, projects = alerts.projects.value, topic = null), lifecycle = lifecycle)

    override fun onFilterClicked(topic: String?) {
        filter.value = topic
    }

    override fun onAlertClicked(id: String) {
        alerts.markRead(id = id)
        globalNavigator.pushNew(configuration = GlobalNavigator.Config.Alert(id = id))
    }

    override fun onReadToggled(id: String) {
        val alert = alerts.alert(id = id) ?: return
        if (alert.isRead) alerts.markUnread(id = id) else alerts.markRead(id = id)
    }

    override fun onDeleteClicked(id: String) {
        alerts.delete(id = id)
        messenger.done(tr("Deleted", "Удалено"))
    }

    override fun onReadAllClicked() {
        alerts.markAllRead()
        messenger.done(tr("All read", "Всё прочитано"))
    }

    override fun onProjectsClicked() {
        globalNavigator.pushNew(configuration = GlobalNavigator.Config.Projects)
    }

    override fun onClearClicked() {
        isClearSheetVisible.value = true
    }

    override fun onClearConfirmed() {
        isClearSheetVisible.value = false
        alerts.clear()
        messenger.done(tr("Feed cleared", "Лента очищена"))
    }

    override fun onClearDismissed() {
        isClearSheetVisible.value = false
    }

    override fun onRefresh() {
        if (isRefreshing.value) return
        isRefreshing.value = true
        launchCoroutine(
            onError = {
                isRefreshing.value = false
                messenger.error(tr("No connection to the server", "Нет связи с сервером"))
            },
        ) {
            coordinator.fetch()
            isRefreshing.value = false
            messenger.done(tr("Updated", "Обновлено"))
        }
    }

    private fun feedState(list: List<Alert>, projects: List<Project>, topic: String?): FeedScreenState {
        val byTopic = projects.associateBy { it.topic }
        val chosen = topic?.takeIf { it in byTopic }
        val visible = list.filter { chosen == null || it.topic == chosen }
        return FeedScreenState(
            unread = list.count { !it.isRead },
            projects = projects.toImmutableList(),
            filter = chosen,
            days = visible.groupBy { dayLabel(time = it.time) }
                .map { (label, items) -> FeedDay(label = label, items = items.map { FeedItem(alert = it, project = byTopic[it.topic]) }.toImmutableList()) }
                .toImmutableList(),
            hasAlerts = list.isNotEmpty(),
        )
    }
}
