package com.kopylovis.tossling.notifications.presentation

import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.CommonComponent
import com.kopylovis.tossling.sync.alerts.Alert
import com.kopylovis.tossling.sync.alerts.Project
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data class FeedItem(
    val alert: Alert,
    val project: Project?,
)

internal data class FeedDay(
    val label: String,
    val items: ImmutableList<FeedItem>,
)

internal data class FeedScreenState(
    val unread: Int = 0,
    val projects: ImmutableList<Project> = persistentListOf(),
    val filter: String? = null,
    val days: ImmutableList<FeedDay> = persistentListOf(),
    val hasAlerts: Boolean = false,
    val isRefreshing: Boolean = false,
    val isClearSheetVisible: Boolean = false,
)

internal interface FeedComponent : CommonComponent {

    val state: Value<FeedScreenState>

    fun onFilterClicked(topic: String?)

    fun onAlertClicked(id: String)

    fun onReadToggled(id: String)

    fun onDeleteClicked(id: String)

    fun onReadAllClicked()

    fun onProjectsClicked()

    fun onClearClicked()

    fun onClearConfirmed()

    fun onClearDismissed()

    fun onRefresh()
}
