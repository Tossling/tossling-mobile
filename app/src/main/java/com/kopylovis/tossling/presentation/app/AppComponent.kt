package com.kopylovis.tossling.presentation.app

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import com.kopylovis.tossling.core.decompose.base.CommonComponent
import com.kopylovis.tossling.presentation.main.MainComponent

interface AppComponent : BackHandlerOwner {

    val stack: Value<ChildStack<*, Child>>

    fun onBackClicked()

    fun onAlertOpened(id: String)

    sealed class Child {
        class WelcomeChild(val component: CommonComponent) : Child()
        class PairingChild(val component: CommonComponent) : Child()
        internal class MainChild(val component: MainComponent) : Child()
        class SettingsChild(val component: CommonComponent) : Child()
        class DevicesChild(val component: CommonComponent) : Child()
        class DeviceChild(val component: CommonComponent) : Child()
        class AddMacChild(val component: CommonComponent) : Child()
        class ProjectsChild(val component: CommonComponent) : Child()
        class ProjectChild(val component: CommonComponent) : Child()
        class AlertChild(val component: CommonComponent) : Child()
    }
}
