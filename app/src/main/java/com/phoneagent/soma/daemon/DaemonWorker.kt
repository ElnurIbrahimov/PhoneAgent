package com.phoneagent.soma.daemon

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class DaemonWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = (applicationContext as? com.phoneagent.PhoneAgentApplication) ?: return Result.failure()
            val controller = app.agentController ?: return Result.failure()

            val mind = DaemonMind(controller)
            mind.runMonologue()

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "kira_daemon_monologue"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DaemonWorker>(8, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
