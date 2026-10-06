package com.kopylovis.tossling.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.kopylovis.tossling.sync.alerts.AlertRepository
import com.kopylovis.tossling.sync.data.ClipRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DebugPairActivity : Activity() {

    private val clips: ClipRepository by inject()
    private val alerts: AlertRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = intent.getStringExtra(EXTRA_CODE)
        val topic = intent.getStringExtra(EXTRA_TOPIC)
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                when {
                    code != null -> clips.pair(raw = code).let { "paired ${it.macName}" }
                    topic != null -> alerts.addProject(name = name, topic = topic, endpoint = clips.endpoint()).let { "project ${it.project.topic}${it.token?.let { token -> " token $token" }.orEmpty()}" }
                    else -> "nothing"
                }
            }
                .onSuccess { result -> Log.i(TAG, result) }
                .onFailure { error -> Log.e(TAG, "failed", error) }
        }
        finish()
    }

    private companion object {
        private const val TAG = "DebugPair"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_TOPIC = "topic"
        private const val EXTRA_NAME = "name"
    }
}
