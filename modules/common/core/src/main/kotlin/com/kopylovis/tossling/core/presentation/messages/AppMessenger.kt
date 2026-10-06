package com.kopylovis.tossling.core.presentation.messages

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first

enum class AppMessageKind { INFO, ERROR, DONE, BUSY, DEVICE }

data class AppMessage(
    val text: String,
    val kind: AppMessageKind = AppMessageKind.INFO,
)

class AppMessenger {

    private val _messages = MutableSharedFlow<AppMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<AppMessage> = _messages.asSharedFlow()

    fun info(text: String) {
        _messages.tryEmit(AppMessage(text = text, kind = AppMessageKind.INFO))
    }

    fun error(text: String) {
        _messages.tryEmit(AppMessage(text = text, kind = AppMessageKind.ERROR))
    }

    fun done(text: String) {
        _messages.tryEmit(AppMessage(text = text, kind = AppMessageKind.DONE))
    }

    fun device(text: String) {
        _messages.tryEmit(AppMessage(text = text, kind = AppMessageKind.DEVICE))
    }

    fun busy(text: String) {
        _messages.tryEmit(AppMessage(text = text, kind = AppMessageKind.BUSY))
    }

    suspend fun infoWhenCollected(text: String) {
        _messages.subscriptionCount.first { it > 0 }
        info(text)
    }
}
