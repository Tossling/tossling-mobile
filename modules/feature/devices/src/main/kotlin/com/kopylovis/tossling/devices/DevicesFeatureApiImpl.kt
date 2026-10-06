package com.kopylovis.tossling.devices

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.kopylovis.tossling.core.decompose.base.CommonComponent
import com.kopylovis.tossling.devices.presentation.AddMacComponent
import com.kopylovis.tossling.devices.presentation.AddMacComponentImpl
import com.kopylovis.tossling.devices.presentation.AddMacContent
import com.kopylovis.tossling.devices.presentation.DeviceComponent
import com.kopylovis.tossling.devices.presentation.DeviceComponentImpl
import com.kopylovis.tossling.devices.presentation.DeviceContent
import com.kopylovis.tossling.devices.presentation.DevicesComponent
import com.kopylovis.tossling.devices.presentation.DevicesComponentImpl
import com.kopylovis.tossling.devices.presentation.DevicesContent
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.data.SyncCoordinator

internal class DevicesFeatureApiImpl(
    private val repository: ClipRepository,
    private val coordinator: SyncCoordinator,
) : DevicesFeatureApi {

    override fun getDevicesComponent(componentContext: ComponentContext): CommonComponent =
        DevicesComponentImpl(componentContext = componentContext, repository = repository)

    override fun getDeviceComponent(componentContext: ComponentContext, pairingId: String, deviceId: String): CommonComponent =
        DeviceComponentImpl(componentContext = componentContext, repository = repository, pairingId = pairingId, deviceId = deviceId)

    override fun getAddMacComponent(componentContext: ComponentContext): CommonComponent =
        AddMacComponentImpl(componentContext = componentContext, repository = repository, coordinator = coordinator)

    @Composable
    override fun openDevicesContent(component: CommonComponent) {
        DevicesContent(component = component as DevicesComponent)
    }

    @Composable
    override fun openDeviceContent(component: CommonComponent) {
        DeviceContent(component = component as DeviceComponent)
    }

    @Composable
    override fun openAddMacContent(component: CommonComponent) {
        AddMacContent(component = component as AddMacComponent)
    }
}
