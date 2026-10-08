package com.kopylovis.tossling.presentation.app

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnCreate
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.kopylovis.tossling.core.decompose.base.BaseComponent
import com.kopylovis.tossling.mediators.MediatorManager
import com.kopylovis.tossling.navigation.GlobalNavigator
import com.kopylovis.tossling.navigation.delegates.toDelegate
import com.kopylovis.tossling.presentation.main.MainComponentImpl
import com.kopylovis.tossling.presentation.main.MainTab
import com.kopylovis.tossling.sync.data.ClipRepository
import org.koin.core.component.inject

internal class AppComponentImpl(
    componentContext: ComponentContext,
) : BaseComponent(componentContext), AppComponent {

    private val navigation = StackNavigation<GlobalNavigator.Config>()
    private val globalNavigator: GlobalNavigator by inject()
    private val repository: ClipRepository by inject()

    private val _stack = childStack(
        source = navigation,
        serializer = GlobalNavigator.Config.serializer(),
        initialConfiguration = if (repository.isPaired.value) GlobalNavigator.Config.Main else GlobalNavigator.Config.Welcome,
        handleBackButton = false,
        childFactory = ::child,
    )

    override val stack: Value<ChildStack<*, AppComponent.Child>> = _stack

    init {
        lifecycle.doOnCreate {
            globalNavigator.setDelegate(delegate = navigation.toDelegate())
        }
        lifecycle.doOnDestroy {
            globalNavigator.onDestroy()
        }
    }

    private fun child(
        config: GlobalNavigator.Config,
        componentContext: ComponentContext,
    ): AppComponent.Child = when (config) {
        is GlobalNavigator.Config.Welcome ->
            AppComponent.Child.WelcomeChild(
                component = MediatorManager.pairingMediator.getApi().getWelcomeComponent(componentContext = componentContext),
            )

        is GlobalNavigator.Config.Pairing ->
            AppComponent.Child.PairingChild(
                component = MediatorManager.pairingMediator.getApi().getPairingComponent(componentContext = componentContext, isReconnect = config.isReconnect),
            )

        is GlobalNavigator.Config.Main ->
            AppComponent.Child.MainChild(component = MainComponentImpl(componentContext = componentContext))

        is GlobalNavigator.Config.Settings ->
            AppComponent.Child.SettingsChild(
                component = MediatorManager.settingsMediator.getApi().getSettingsComponent(componentContext = componentContext),
            )

        is GlobalNavigator.Config.Devices ->
            AppComponent.Child.DevicesChild(
                component = MediatorManager.devicesMediator.getApi().getDevicesComponent(componentContext = componentContext),
            )

        is GlobalNavigator.Config.Device ->
            AppComponent.Child.DeviceChild(
                component = MediatorManager.devicesMediator.getApi().getDeviceComponent(componentContext = componentContext, pairingId = config.pairingId, deviceId = config.deviceId),
            )

        is GlobalNavigator.Config.AddMac ->
            AppComponent.Child.AddMacChild(
                component = MediatorManager.devicesMediator.getApi().getAddMacComponent(componentContext = componentContext),
            )

        is GlobalNavigator.Config.Projects ->
            AppComponent.Child.ProjectsChild(
                component = MediatorManager.notificationsMediator.getApi().getProjectsComponent(componentContext = componentContext),
            )

        is GlobalNavigator.Config.Project ->
            AppComponent.Child.ProjectChild(
                component = MediatorManager.notificationsMediator.getApi().getProjectComponent(componentContext = componentContext, topic = config.topic),
            )

        is GlobalNavigator.Config.Alert ->
            AppComponent.Child.AlertChild(
                component = MediatorManager.notificationsMediator.getApi().getAlertComponent(componentContext = componentContext, id = config.id),
            )
    }

    override fun onBackClicked() {
        navigation.pop()
    }

    override fun onAlertOpened(id: String) {
        if (!repository.isPaired.value) return
        navigation.navigate { listOf(GlobalNavigator.Config.Main, GlobalNavigator.Config.Alert(id = id)) }
        _stack.value.items
            .firstNotNullOfOrNull { (it.instance as? AppComponent.Child.MainChild)?.component }
            ?.onTabClicked(tab = MainTab.NOTIFICATIONS)
    }
}
