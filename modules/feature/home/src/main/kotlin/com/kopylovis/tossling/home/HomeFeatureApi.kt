package com.kopylovis.tossling.home

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.kopylovis.tossling.core.decompose.base.CommonComponent

interface HomeFeatureApi {

    fun getHomeComponent(componentContext: ComponentContext): CommonComponent

    fun requestSend(component: CommonComponent)

    @Composable
    fun openHomeContent(component: CommonComponent)
}
