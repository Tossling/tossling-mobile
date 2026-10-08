package com.kopylovis.tossling.pairing.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.BaseComponent
import com.kopylovis.tossling.core.presentation.tr
import com.kopylovis.tossling.navigation.GlobalNavigator
import com.kopylovis.tossling.protocol.PairingException
import com.kopylovis.tossling.protocol.PairingProblem
import com.kopylovis.tossling.protocol.pairingHost
import com.kopylovis.tossling.protocol.pairingName
import com.kopylovis.tossling.sync.data.ClipRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.component.inject

internal class PairingComponentImpl(
    componentContext: ComponentContext,
    private val repository: ClipRepository,
    @Suppress("unused") private val isReconnect: Boolean,
    link: String?,
    isDemo: Boolean,
) : BaseComponent(componentContext), PairingComponent {

    private val globalNavigator: GlobalNavigator by inject()
    private val context: Context by inject()

    private val _state = MutableStateFlow(PairingScreenState())
    override val state: Value<PairingScreenState> = _state.asValue()

    private var job: Job? = null

    init {
        if (link != null && isDemo) {
            onScanned(raw = link)
        } else if (link != null) {
            val switch = repository.roomSwitch(raw = link)
            _state.value = PairingScreenState(
                stage = if (switch != null) {
                    PairingStage.Switching(raw = link, from = switch.from, to = switch.to)
                } else {
                    PairingStage.Joining(raw = link, name = pairingName(raw = link).ifEmpty { "?" }, server = pairingHost(raw = link))
                },
            )
        }
    }

    override fun onScanned(raw: String) {
        if (_state.value.stage is PairingStage.Connecting) return
        val switch = repository.roomSwitch(raw = raw)
        if (switch != null) {
            _state.value = PairingScreenState(stage = PairingStage.Switching(raw = raw, from = switch.from, to = switch.to))
            return
        }
        connect(raw = raw)
    }

    override fun onSwitchConfirmed() {
        val stage = _state.value.stage as? PairingStage.Switching ?: return
        connect(raw = stage.raw)
    }

    override fun onJoinConfirmed() {
        val stage = _state.value.stage as? PairingStage.Joining ?: return
        connect(raw = stage.raw)
    }

    private fun connect(raw: String) {
        val server = pairingHost(raw = raw)
        _state.value = PairingScreenState(stage = PairingStage.Connecting(server = server))
        job = launchCoroutine(
            onError = { error ->
                val failure = error as? PairingException
                _state.value = PairingScreenState(
                    stage = PairingStage.Failed(problem = failure?.problem ?: PairingProblem.NETWORK, server = failure?.server ?: server),
                )
            },
        ) {
            val pairing = repository.pair(raw = raw)
            _state.value = PairingScreenState(stage = PairingStage.Paired(macName = pairing.macName.ifEmpty { "Mac" }, server = pairing.host))
        }
    }

    override fun onScanFailed() {
        messenger.info(tr("The camera did not start", "Камера не запустилась"))
    }

    override fun onCancelClicked() {
        job?.cancel()
        _state.value = PairingScreenState()
    }

    override fun onCopyCommandClicked() {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Tossling", COMMAND))
        messenger.done(tr("Copied", "Скопировано"))
    }

    override fun onDoneClicked() {
        globalNavigator.replaceAll(if (repository.isPaired.value) GlobalNavigator.Config.Main else GlobalNavigator.Config.Welcome)
    }

    override fun onBackClicked() {
        job?.cancel()
        globalNavigator.pop()
    }

    companion object {
        const val COMMAND = "tossling pair"
    }
}
