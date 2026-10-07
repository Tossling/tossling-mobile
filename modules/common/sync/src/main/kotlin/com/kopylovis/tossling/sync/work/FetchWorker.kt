package com.kopylovis.tossling.sync.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kopylovis.tossling.protocol.Transfer
import com.kopylovis.tossling.sync.data.SyncCoordinator
import com.kopylovis.tossling.sync.notifications.SyncNotifications
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class FetchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val coordinator: SyncCoordinator by inject()
    private val notifications: SyncNotifications by inject()

    @Volatile private var isPromoted = false

    override suspend fun doWork(): Result {
        if (inputData.getBoolean(KEY_RESUBSCRIBE, false)) coordinator.resubscribe()
        return runCatching { coordinator.fetch(topic = inputData.getString(KEY_TOPIC), onTransfer = ::showProgress) }.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                Log.e(TAG, "fetch failed", error)
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            },
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = notifications.foregroundInfo()

    private fun showProgress(transfer: Transfer) {
        if (!isPromoted) {
            isPromoted = true
            runCatching { setForegroundAsync(notifications.transferInfo(transfer = transfer, cancel = null)) }
        }
        notifications.showTransfer(transfer = transfer)
    }

    companion object {
        private const val TAG = "FetchWorker"
        private const val NAME = "tossy-fetch"
        private const val KEY_RESUBSCRIBE = "resubscribe"
        private const val KEY_TOPIC = "topic"
        private const val MAX_ATTEMPTS = 3

        fun enqueue(context: Context, resubscribe: Boolean = false, topic: String? = null) {
            val request = OneTimeWorkRequestBuilder<FetchWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_RESUBSCRIBE to resubscribe, KEY_TOPIC to topic))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
