package com.kopylovis.tossling.sync.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kopylovis.tossling.sync.data.Endpoint
import com.kopylovis.tossling.sync.network.NtfyClient
import com.kopylovis.tossling.sync.network.NtfyException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

internal class RetireTokenWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val client: NtfyClient by inject()

    override suspend fun doWork(): Result {
        val server = inputData.getString(KEY_SERVER) ?: return Result.failure()
        val token = inputData.getString(KEY_TOKEN) ?: return Result.failure()
        val old = inputData.getString(KEY_OLD) ?: return Result.failure()
        return try {
            client.deleteToken(endpoint = Endpoint(server = server, token = token), token = old)
            Result.success()
        } catch (error: NtfyException) {
            Log.w(TAG, "old token not deleted: HTTP ${error.code}")
            if (error.code in HTTP_CLIENT_ERRORS) Result.success() else retryOrFail()
        } catch (error: Exception) {
            Log.w(TAG, "old token not deleted", error)
            retryOrFail()
        }
    }

    private fun retryOrFail(): Result = if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()

    companion object {
        private const val TAG = "RetireTokenWorker"
        private const val KEY_SERVER = "server"
        private const val KEY_TOKEN = "token"
        private const val KEY_OLD = "old"
        private const val MAX_ATTEMPTS = 20
        private const val DELAY_HOURS = 24L
        private val HTTP_CLIENT_ERRORS = 400..499

        fun enqueue(context: Context, server: String, token: String, old: String) {
            val request = OneTimeWorkRequestBuilder<RetireTokenWorker>()
                .setInitialDelay(DELAY_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_SERVER to server, KEY_TOKEN to token, KEY_OLD to old))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
