package com.kopylovis.tossling.notifications.presentation

import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.CommonComponent
import com.kopylovis.tossling.protocol.alerts.Alert
import com.kopylovis.tossling.protocol.alerts.Project
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data class ProjectRow(
    val project: Project,
    val last: Alert?,
)

internal data class ProjectsScreenState(
    val rows: ImmutableList<ProjectRow> = persistentListOf(),
)

internal interface ProjectsComponent : CommonComponent {

    val state: Value<ProjectsScreenState>

    fun onBackClicked()

    fun onProjectClicked(topic: String)

    fun onMuteClicked(topic: String)

    fun onAddClicked()
}
