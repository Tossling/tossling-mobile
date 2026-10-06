package com.kopylovis.tossling.home

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.kopylovis.tossling.core.decompose.base.CommonComponent
import com.kopylovis.tossling.home.presentation.HomeComponent
import com.kopylovis.tossling.home.presentation.HomeComponentImpl
import com.kopylovis.tossling.home.presentation.HomeContent
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.data.SyncSettings
import com.kopylovis.tossling.sync.entry.ClipSender

internal class HomeFeatureApiImpl(
    private val repository: ClipRepository,
    private val sender: ClipSender,
    private val settings: SyncSettings,
) : HomeFeatureApi {

    override fun getHomeComponent(componentContext: ComponentContext): CommonComponent =
        HomeComponentImpl(componentContext = componentContext, repository = repository, sender = sender, settings = settings)

    override fun requestSend(component: CommonComponent) {
        (component as HomeComponent).onSendClicked()
    }

    @Composable
    override fun openHomeContent(component: CommonComponent) {
        HomeContent(component = component as HomeComponent)
    }
}
