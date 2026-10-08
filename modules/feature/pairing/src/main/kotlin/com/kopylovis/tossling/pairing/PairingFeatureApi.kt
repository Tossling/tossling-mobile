package com.kopylovis.tossling.pairing

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.kopylovis.tossling.core.decompose.base.CommonComponent

interface PairingFeatureApi {

    fun getWelcomeComponent(componentContext: ComponentContext): CommonComponent

    @Composable
    fun openWelcomeContent(component: CommonComponent)

    fun getPairingComponent(componentContext: ComponentContext, isReconnect: Boolean, link: String? = null, isDemo: Boolean = false): CommonComponent

    @Composable
    fun openPairingContent(component: CommonComponent)
}
