package com.kopylovis.tossling.pairing.presentation

import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.CommonComponent
import com.kopylovis.tossling.sync.data.PairingProblem

internal sealed interface PairingStage {
    data object Idle : PairingStage
    data class Connecting(val server: String) : PairingStage
    data class Switching(val raw: String, val from: String, val to: String) : PairingStage
    data class Failed(val problem: PairingProblem, val server: String) : PairingStage
    data class Paired(val macName: String, val server: String) : PairingStage
}

internal data class PairingScreenState(
    val stage: PairingStage = PairingStage.Idle,
)

internal interface PairingComponent : CommonComponent {

    val state: Value<PairingScreenState>

    fun onScanned(raw: String)

    fun onScanFailed()

    fun onSwitchConfirmed()

    fun onCancelClicked()

    fun onCopyCommandClicked()

    fun onDoneClicked()

    fun onBackClicked()
}
