package com.kopylovis.tossling.sync.entry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/** Pause, Continue and Cancel from the transfer notifications. */
internal class TransferActionReceiver : BroadcastReceiver(), KoinComponent {

    private val sender: ClipSender by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val work = intent.getStringExtra(TosslingIntents.EXTRA_WORK)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val paused = intent.getStringExtra(TosslingIntents.EXTRA_PAUSED)
        when (intent.action) {
            TosslingIntents.ACTION_PAUSE_SEND -> sender.pauseSending(work = work)
            TosslingIntents.ACTION_CANCEL_SEND -> sender.cancelSending(work = work)
            TosslingIntents.ACTION_RESUME_SEND -> paused?.let(sender::resume)
            TosslingIntents.ACTION_DISCARD_SEND -> paused?.let(sender::discard)
        }
    }
}
