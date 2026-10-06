package com.kopylovis.tossling.devices.presentation

import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.CommonComponent
import com.kopylovis.tossling.sync.data.RoomDevice

internal data class DeviceScreenState(
    val device: RoomDevice? = null,
    val name: String = "",
    val isSheetVisible: Boolean = false,
)

internal interface DeviceComponent : CommonComponent {

    val state: Value<DeviceScreenState>

    fun onBackClicked()

    fun onNameChanged(name: String)

    fun onNameDone()

    fun onDisconnectClicked()

    fun onDisconnectConfirmed()

    fun onSheetDismissed()
}
