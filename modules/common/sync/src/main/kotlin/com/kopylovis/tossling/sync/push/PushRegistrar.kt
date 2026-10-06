package com.kopylovis.tossling.sync.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

internal class PushRegistrar(private val context: Context) {

    val isAvailable: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    suspend fun subscribe(topic: String) {
        if (!isAvailable) return
        runCatching { FirebaseMessaging.getInstance().subscribeToTopic(topic).await() }
            .onFailure { error -> Log.e(TAG, "subscribe failed", error) }
    }

    suspend fun unsubscribe(topic: String) {
        if (!isAvailable) return
        runCatching { FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await() }
            .onFailure { error -> Log.e(TAG, "unsubscribe failed", error) }
    }

    private companion object {
        private const val TAG = "PushRegistrar"
    }
}
