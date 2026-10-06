package com.kopylovis.tossling.sync.entry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kopylovis.tossling.sync.alerts.AlertRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class AlertActionReceiver : BroadcastReceiver(), KoinComponent {

    private val alerts: AlertRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TossyIntents.ACTION_MARK_READ) return
        intent.getStringExtra(TossyIntents.EXTRA_ALERT)?.let(alerts::markRead)
    }
}
