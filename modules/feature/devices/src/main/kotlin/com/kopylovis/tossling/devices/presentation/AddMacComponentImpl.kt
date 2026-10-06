package com.kopylovis.tossling.devices.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.BaseComponent
import com.kopylovis.tossling.core.presentation.tr
import com.kopylovis.tossling.navigation.GlobalNavigator
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.data.SyncCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.inject

internal class AddMacComponentImpl(
    componentContext: ComponentContext,
    private val repository: ClipRepository,
    private val coordinator: SyncCoordinator,
) : BaseComponent(componentContext), AddMacComponent {

    private val globalNavigator: GlobalNavigator by inject()
    private val joined = MutableStateFlow<String?>(null)

    override val state: Value<AddMacScreenState> =
        combine(repository.pairings, joined) { pairings, name ->
            AddMacScreenState(host = pairings.firstOrNull { it.isRoom }?.host ?: pairings.firstOrNull()?.host.orEmpty(), joined = name)
        }.asValue()

    init {
        scope.launch {
            repository.joined.collect { name -> joined.value = name }
        }
        launchCoroutine(onError = {}) {
            while (isActive && joined.value == null) {
                runCatching { coordinator.fetch() }
                delay(POLL_MS)
            }
        }
    }

    override fun onBackClicked() {
        globalNavigator.pop()
    }

    override fun onCopyInvite() {
        repository.copyText(text = INVITE)
        messenger.done(tr("Copied", "Скопировано"))
    }

    override fun onCopyJoin() {
        repository.copyText(text = "tossling join ${state.value.host}/")
        messenger.done(tr("Copied", "Скопировано"))
    }

    override fun onScanClicked() {
        globalNavigator.pushNew(configuration = GlobalNavigator.Config.Pairing(isReconnect = true))
    }

    override fun onDoneClicked() {
        globalNavigator.pop()
    }

    private companion object {
        private const val INVITE = "tossling invite"
        private const val POLL_MS = 5_000L
    }
}
