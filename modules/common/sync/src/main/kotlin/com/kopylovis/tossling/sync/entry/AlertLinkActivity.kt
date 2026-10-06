package com.kopylovis.tossling.sync.entry

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.kopylovis.tossling.sync.alerts.AlertRepository
import org.koin.android.ext.android.inject

internal class AlertLinkActivity : Activity() {

    private val alerts: AlertRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(TossyIntents.EXTRA_ALERT)?.let(alerts::markRead)
        intent.data?.let { url ->
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        finish()
    }

    companion object {
        fun intent(context: Context, alertId: String, url: String): Intent =
            Intent(context, AlertLinkActivity::class.java)
                .setData(Uri.parse(url))
                .putExtra(TossyIntents.EXTRA_ALERT, alertId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
