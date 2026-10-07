package com.kopylovis.tossling.devices.presentation

import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.CommonComponent
import com.kopylovis.tossling.protocol.RoomDevice
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data class DevicesScreenState(
    val devices: ImmutableList<RoomDevice> = persistentListOf(),
)

internal interface DevicesComponent : CommonComponent {

    val state: Value<DevicesScreenState>

    fun onBackClicked()

    fun onDeviceClicked(device: RoomDevice)

    fun onAddClicked()
}
