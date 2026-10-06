package com.kopylovis.tossling.presentation.main

import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.CommonComponent

internal enum class MainTab { BUFFER, NOTIFICATIONS }

internal interface MainComponent : CommonComponent {

    val buffer: CommonComponent

    val feed: CommonComponent

    val tab: Value<MainTab>

    val unread: Value<Int>

    fun onTabClicked(tab: MainTab)

    fun onSendClicked()
}
