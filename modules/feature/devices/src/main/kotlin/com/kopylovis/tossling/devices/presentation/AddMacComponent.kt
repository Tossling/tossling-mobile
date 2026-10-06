package com.kopylovis.tossling.devices.presentation

import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.CommonComponent

internal data class AddMacScreenState(
    val host: String = "",
    val joined: String? = null,
)

internal interface AddMacComponent : CommonComponent {

    val state: Value<AddMacScreenState>

    fun onBackClicked()

    fun onCopyInvite()

    fun onCopyJoin()

    fun onScanClicked()

    fun onDoneClicked()
}
