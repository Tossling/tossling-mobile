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
import com.kopylovis.tossling.protocol.ClipKind
import com.kopylovis.tossling.protocol.Transfer
import com.kopylovis.tossling.sync.clipboard.Outgoing
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.notifications.SyncNotifications
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.UUID

internal class SendWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val repository: ClipRepository by inject()
    private val notifications: SyncNotifications by inject()
    private val control: SendControl by inject()
    private val paused: PausedSends by inject()

    @Volatile private var isPromoted = false
    @Volatile private var lastTransfer: Transfer? = null

    override suspend fun doWork(): Result {
        val file = File(inputData.getString(KEY_PATH) ?: return Result.failure())
        if (!file.exists()) return Result.failure()
        val outgoing = Outgoing(
            kind = ClipKind.valueOf(inputData.getString(KEY_KIND) ?: ClipKind.TEXT.name),
            mime = inputData.getString(KEY_MIME) ?: "text/plain",
            file = file,
            name = inputData.getString(KEY_NAME),
            sensitive = inputData.getBoolean(KEY_SENSITIVE, false),
        )
        val targets = inputData.getStringArray(KEY_TARGETS)?.toSet()
        control.start(id = id)
        try {
            val item = repository.send(outgoing = outgoing, targets = targets, gate = control) { transfer -> showProgress(transfer = transfer) }
            if (item.kind == ClipKind.FILE) notifications.showSent(name = item.name ?: "file", device = if (item.toAll) "" else item.device)
            return Result.success()
        } catch (stopped: SendStopped) {
            // Success, not failure: the next file in the queue starts right away.
            if (stopped.isPaused) {
                val kept = paused.add(outgoing = outgoing, targets = targets, device = lastTransfer?.device.orEmpty())
                notifications.showPaused(paused = kept)
            } else {
                file.delete()
            }
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
        } finally {
            control.finish(id = id)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = notifications.foregroundInfo()

    private fun showProgress(transfer: Transfer) {
        lastTransfer = transfer
        if (!isPromoted) {
            isPromoted = true
            runCatching { setForegroundAsync(notifications.transferInfo(transfer = transfer, work = id)) }
        }
        notifications.showTransfer(transfer = transfer, work = id)
    }

    companion object {
        private const val TAG = "SendWorker"
        private const val NAME = "tossy-send"
        private const val KEY_PATH = "path"
        private const val KEY_KIND = "kind"
        private const val KEY_MIME = "mime"
        private const val KEY_NAME = "name"
        private const val KEY_TARGETS = "targets"
        private const val KEY_SENSITIVE = "sensitive"
        private const val MAX_ATTEMPTS = 3

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }

        fun cancel(context: Context, id: UUID) {
            WorkManager.getInstance(context).cancelWorkById(id)
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
                        KEY_SENSITIVE to outgoing.sensitive,
                    ),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
