package com.kopylovis.tossling.pairing

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.kopylovis.tossling.core.decompose.base.CommonComponent
import com.kopylovis.tossling.pairing.presentation.PairingComponent
import com.kopylovis.tossling.pairing.presentation.PairingComponentImpl
import com.kopylovis.tossling.pairing.presentation.PairingContent
import com.kopylovis.tossling.pairing.presentation.WelcomeComponent
import com.kopylovis.tossling.pairing.presentation.WelcomeComponentImpl
import com.kopylovis.tossling.pairing.presentation.WelcomeContent
import com.kopylovis.tossling.sync.data.ClipRepository

internal class PairingFeatureApiImpl(
    private val repository: ClipRepository,
) : PairingFeatureApi {

    override fun getWelcomeComponent(componentContext: ComponentContext): CommonComponent =
        WelcomeComponentImpl(componentContext = componentContext, repository = repository)

    @Composable
    override fun openWelcomeContent(component: CommonComponent) {
        WelcomeContent(component = component as WelcomeComponent)
    }

    override fun getPairingComponent(componentContext: ComponentContext, isReconnect: Boolean): CommonComponent =
        PairingComponentImpl(componentContext = componentContext, repository = repository, isReconnect = isReconnect)

    @Composable
    override fun openPairingContent(component: CommonComponent) {
        PairingContent(component = component as PairingComponent)
    }
}
