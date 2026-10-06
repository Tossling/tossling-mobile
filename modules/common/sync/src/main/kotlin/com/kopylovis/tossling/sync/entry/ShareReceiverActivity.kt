package com.kopylovis.tossling.sync.entry

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.kopylovis.tossling.sync.R
import com.kopylovis.tossling.sync.clipboard.ClipboardBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

internal class ShareReceiverActivity : Activity() {

    private val sender: ClipSender by inject()
    private val bridge: ClipboardBridge by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            sendMany(uris = streamsOf(intent = intent))
            return
        }
        if (intent?.action != Intent.ACTION_SEND) {
            finish()
            return
        }
        val mime = intent.type.orEmpty()
        val stream = streamOf(intent = intent)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            sender.send {
                when {
                    stream != null -> bridge.fromUri(uri = stream, mime = contentResolver.getType(stream) ?: mime.ifEmpty { "application/octet-stream" })
                    !text.isNullOrEmpty() -> bridge.fromText(text = text)
                    else -> null
                }
            }
            withContext(Dispatchers.Main) { finish() }
        }
    }

    private fun sendMany(uris: List<Uri>) {
        val fallback = intent.type.orEmpty().ifEmpty { "application/octet-stream" }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val prepared = uris.map { uri -> sender.prepare { bridge.fromUri(uri = uri, mime = contentResolver.getType(uri) ?: fallback, asFile = true) } }
            val ready = prepared.filterIsInstance<Prepared.Ready>()
            ready.forEach { sender.enqueue(outgoing = it.outgoing) }
            val refused = prepared.filterIsInstance<Prepared.Refused>().firstOrNull()
            val message = when {
                ready.isEmpty() && refused != null -> sender.text(refused.message)
                ready.size == uris.size -> resources.getQuantityString(R.plurals.sync_sending_files, ready.size, ready.size)
                else -> resources.getQuantityString(R.plurals.sync_sending_some, ready.size, ready.size, uris.size)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun streamsOf(intent: Intent): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }

    private fun streamOf(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
}
