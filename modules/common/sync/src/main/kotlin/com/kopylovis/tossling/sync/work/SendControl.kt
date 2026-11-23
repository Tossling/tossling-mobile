package com.kopylovis.tossling.sync.work

import java.util.UUID
import kotlin.concurrent.thread

/** Lets the user stop a file while it uploads. */
interface UploadGate {

    /** Throws [SendStopped] once the user paused or cancelled the upload. */
    fun check()

    /** How to break the current connection, or null once it is closed. */
    fun attach(abort: (() -> Unit)?)
}

/**
 * Pause and Cancel for the file that is going out right now. A request breaks the connection at once, a write stuck
 * on a stalled network included, and the worker then sees [SendStopped] instead of a network error.
 */
internal class SendControl : UploadGate {

    enum class Request { PAUSE, CANCEL }

    @Volatile var active: UUID? = null
        private set

    @Volatile private var request: Request? = null
    @Volatile private var abort: (() -> Unit)? = null

    fun start(id: UUID) {
        request = null
        abort = null
        active = id
    }

    fun finish(id: UUID) {
        if (active != id) return
        active = null
        request = null
        abort = null
    }

    /** False when nothing with this id is uploading now. */
    fun request(id: UUID?, value: Request): Boolean {
        if (id == null || active != id) return false
        request = value
        // Closing a TLS socket writes to the network, and the request may come on the main thread.
        abort?.let { drop -> thread(name = "send-abort") { runCatching(drop) } }
        return true
    }

    override fun attach(abort: (() -> Unit)?) {
        this.abort = abort
    }

    override fun check() {
        when (request) {
            Request.PAUSE -> throw SendStopped(isPaused = true)
            Request.CANCEL -> throw SendStopped(isPaused = false)
            null -> Unit
        }
    }
}

class SendStopped(val isPaused: Boolean) : Exception(if (isPaused) "paused" else "cancelled")
