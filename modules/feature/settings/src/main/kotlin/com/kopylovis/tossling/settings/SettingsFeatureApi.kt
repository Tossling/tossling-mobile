package com.kopylovis.tossling.settings

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.kopylovis.tossling.core.decompose.base.CommonComponent

interface SettingsFeatureApi {

    fun getSettingsComponent(componentContext: ComponentContext): CommonComponent

    @Composable
    fun openSettingsContent(component: CommonComponent)
}
