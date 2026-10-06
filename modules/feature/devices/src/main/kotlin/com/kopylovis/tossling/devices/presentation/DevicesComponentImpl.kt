package com.kopylovis.tossling.devices.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnResume
import com.kopylovis.tossling.core.decompose.base.BaseComponent
import com.kopylovis.tossling.navigation.GlobalNavigator
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.data.RoomDevice
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.component.inject

internal class DevicesComponentImpl(
    componentContext: ComponentContext,
    private val repository: ClipRepository,
) : BaseComponent(componentContext), DevicesComponent {

    private val globalNavigator: GlobalNavigator by inject()

    override val state: Value<DevicesScreenState> =
        repository.devices.map { devices -> DevicesScreenState(devices = devices.toImmutableList()) }.asValue()

    init {
        lifecycle.doOnResume { launchCoroutine(onError = {}) { repository.ping() } }
    }

    override fun onBackClicked() {
        globalNavigator.pop()
    }

    override fun onDeviceClicked(device: RoomDevice) {
        globalNavigator.pushNew(configuration = GlobalNavigator.Config.Device(pairingId = device.pairingId, deviceId = device.id))
    }

    override fun onAddClicked() {
        globalNavigator.pushNew(configuration = GlobalNavigator.Config.AddMac)
    }
}
