package com.kopylovis.tossling.settings

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.kopylovis.tossling.core.decompose.base.CommonComponent
import com.kopylovis.tossling.settings.presentation.SettingsComponent
import com.kopylovis.tossling.settings.presentation.SettingsComponentImpl
import com.kopylovis.tossling.settings.presentation.SettingsContent
import com.kopylovis.tossling.sync.data.SyncCoordinator
import com.kopylovis.tossling.sync.data.SyncSettings

internal class SettingsFeatureApiImpl(
    private val coordinator: SyncCoordinator,
    private val settings: SyncSettings,
) : SettingsFeatureApi {

    override fun getSettingsComponent(componentContext: ComponentContext): CommonComponent =
        SettingsComponentImpl(componentContext = componentContext, coordinator = coordinator, settings = settings)

    @Composable
    override fun openSettingsContent(component: CommonComponent) {
        SettingsContent(component = component as SettingsComponent)
    }
}
