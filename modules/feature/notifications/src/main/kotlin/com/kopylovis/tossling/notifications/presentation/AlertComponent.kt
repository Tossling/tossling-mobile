package com.kopylovis.tossling.notifications.presentation

import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.CommonComponent
import com.kopylovis.tossling.protocol.alerts.Alert
import com.kopylovis.tossling.protocol.alerts.Project

internal data class AlertScreenState(
    val alert: Alert? = null,
    val project: Project? = null,
)

internal interface AlertComponent : CommonComponent {

    val state: Value<AlertScreenState>

    fun onBackClicked()

    fun onDeleteClicked()

    fun onCopyClicked()
}
