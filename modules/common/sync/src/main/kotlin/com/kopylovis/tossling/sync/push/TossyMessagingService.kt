package com.kopylovis.tossling.sync.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kopylovis.tossling.sync.work.FetchWorker

internal class TossyMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["event"] in WAKE_EVENTS) FetchWorker.enqueue(context = applicationContext, topic = message.data["topic"])
    }

    override fun onNewToken(token: String) {
        FetchWorker.enqueue(context = applicationContext, resubscribe = true)
    }

    private companion object {
        private val WAKE_EVENTS = setOf("message", "poll_request")
    }
}
