package com.kopylovis.tossling.pairing.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.BaseComponent
import com.kopylovis.tossling.navigation.GlobalNavigator
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.data.PairingException
import com.kopylovis.tossling.sync.data.PairingProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

internal class WelcomeComponentImpl(
    componentContext: ComponentContext,
    private val repository: ClipRepository,
) : BaseComponent(componentContext), WelcomeComponent {

    private val globalNavigator: GlobalNavigator by inject()
    private val current = MutableStateFlow(WelcomeState())

    override val state: Value<WelcomeState> = current.asValue()

    init {
        launchCoroutine(onError = {}) {
            val saved = repository.savedRooms()
            current.update { it.copy(saved = saved) }
        }
    }

    override fun onPairClicked() {
        globalNavigator.pushNew(configuration = GlobalNavigator.Config.Pairing())
    }

    override fun onRestoreClicked() {
        val saved = current.value.saved ?: return
        if (current.value.isRestoring) return
        current.update { it.copy(isRestoring = true, problem = null) }
        launchCoroutine(onError = { error ->
            val problem = (error as? PairingException)?.problem ?: PairingProblem.NETWORK
            current.update { it.copy(isRestoring = false, problem = problem) }
        }) {
            repository.restore(saved = saved)
            withContext(Dispatchers.Main.immediate) { globalNavigator.replaceAll(GlobalNavigator.Config.Main) }
        }
    }
}
