package com.kopylovis.tossling.pairing.presentation

import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.CommonComponent
import com.kopylovis.tossling.protocol.PairingProblem
import com.kopylovis.tossling.sync.data.SavedRooms

internal data class WelcomeState(
    val saved: SavedRooms? = null,
    val isRestoring: Boolean = false,
    val isOpeningDemo: Boolean = false,
    val problem: PairingProblem? = null,
)

internal interface WelcomeComponent : CommonComponent {

    val state: Value<WelcomeState>

    fun onPairClicked()

    fun onRestoreClicked()

    fun onDemoClicked()
}
