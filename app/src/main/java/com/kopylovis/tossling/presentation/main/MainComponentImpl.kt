package com.kopylovis.tossling.presentation.main

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.BaseComponent
import com.kopylovis.tossling.core.decompose.base.CommonComponent
import com.kopylovis.tossling.mediators.MediatorManager
import com.kopylovis.tossling.sync.alerts.AlertRepository
import kotlinx.serialization.builtins.serializer
import org.koin.core.component.inject

internal class MainComponentImpl(
    componentContext: ComponentContext,
) : BaseComponent(componentContext), MainComponent {

    private val alerts: AlertRepository by inject()

    override val buffer: CommonComponent =
        MediatorManager.homeMediator.getApi().getHomeComponent(componentContext = childContext(key = "buffer"))

    override val feed: CommonComponent =
        MediatorManager.notificationsMediator.getApi().getFeedComponent(componentContext = childContext(key = "feed"))

    private val _tab = MutableValue(
        stateKeeper.consume(key = KEY_TAB, strategy = String.serializer())?.let { saved -> MainTab.entries.firstOrNull { it.name == saved } } ?: MainTab.BUFFER,
    )
    override val tab: Value<MainTab> = _tab

    override val unread: Value<Int> = alerts.unreadCount.asValue()

    init {
        stateKeeper.register(key = KEY_TAB, strategy = String.serializer()) { _tab.value.name }
    }

    override fun onTabClicked(tab: MainTab) {
        _tab.value = tab
    }

    override fun onSendClicked() {
        _tab.value = MainTab.BUFFER
        MediatorManager.homeMediator.getApi().requestSend(component = buffer)
    }

    private companion object {
        private const val KEY_TAB = "tab"
    }
}
