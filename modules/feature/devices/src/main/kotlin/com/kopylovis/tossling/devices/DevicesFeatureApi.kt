package com.kopylovis.tossling.devices

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.kopylovis.tossling.core.decompose.base.CommonComponent

interface DevicesFeatureApi {

    fun getDevicesComponent(componentContext: ComponentContext): CommonComponent

    fun getDeviceComponent(componentContext: ComponentContext, pairingId: String, deviceId: String): CommonComponent

    fun getAddMacComponent(componentContext: ComponentContext): CommonComponent

    @Composable
    fun openDevicesContent(component: CommonComponent)

    @Composable
    fun openDeviceContent(component: CommonComponent)

    @Composable
    fun openAddMacContent(component: CommonComponent)
}
