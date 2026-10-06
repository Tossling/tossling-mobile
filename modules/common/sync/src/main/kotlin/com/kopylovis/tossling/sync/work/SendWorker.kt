package com.kopylovis.tossling.sync.work

import android.app.PendingIntent
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
import com.kopylovis.tossling.sync.clipboard.Outgoing
import com.kopylovis.tossling.sync.data.ClipKind
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.data.Transfer
import com.kopylovis.tossling.sync.notifications.SyncNotifications
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.CancellationException
import java.io.File

internal class SendWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val repository: ClipRepository by inject()
    private val notifications: SyncNotifications by inject()

    @Volatile private var isPromoted = false

    override suspend fun doWork(): Result {
        val file = File(inputData.getString(KEY_PATH) ?: return Result.failure())
        if (!file.exists()) return Result.failure()
        val outgoing = Outgoing(
            kind = ClipKind.valueOf(inputData.getString(KEY_KIND) ?: ClipKind.TEXT.name),
            mime = inputData.getString(KEY_MIME) ?: "text/plain",
            file = file,
            name = inputData.getString(KEY_NAME),
        )
        val targets = inputData.getStringArray(KEY_TARGETS)?.toSet()
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        try {
            val item = repository.send(outgoing = outgoing, targets = targets) { transfer -> showProgress(transfer = transfer, cancel = cancel) }
            if (item.kind == ClipKind.FILE) notifications.showSent(name = item.name ?: "file", device = if (item.toAll) "" else item.device)
            return Result.success()
        } catch (error: CancellationException) {
            file.delete()
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "send failed", error)
            if (runAttemptCount < MAX_ATTEMPTS) return Result.retry()
            file.delete()
            notifications.clearTransfer()
            notifications.showSendFailed(reason = error.message.orEmpty())
            return Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = notifications.foregroundInfo()

    private fun showProgress(transfer: Transfer, cancel: PendingIntent) {
        if (!isPromoted) {
            isPromoted = true
            runCatching { setForegroundAsync(notifications.transferInfo(transfer = transfer, cancel = cancel)) }
        }
        notifications.showTransfer(transfer = transfer, cancel = cancel)
    }

    companion object {
        private const val TAG = "SendWorker"
        private const val NAME = "tossy-send"
        private const val KEY_PATH = "path"
        private const val KEY_KIND = "kind"
        private const val KEY_MIME = "mime"
        private const val KEY_NAME = "name"
        private const val KEY_TARGETS = "targets"
        private const val MAX_ATTEMPTS = 3

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }

        fun enqueue(context: Context, outgoing: Outgoing, targets: Set<String>? = null) {
            val request = OneTimeWorkRequestBuilder<SendWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(
                    workDataOf(
                        KEY_PATH to outgoing.file.absolutePath,
                        KEY_KIND to outgoing.kind.name,
                        KEY_MIME to outgoing.mime,
                        KEY_NAME to outgoing.name,
                        KEY_TARGETS to targets?.toTypedArray(),
                    ),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
