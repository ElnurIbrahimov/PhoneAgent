package com.phoneagent.soma.ground

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class GroundWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = (applicationContext as? com.phoneagent.PhoneAgentApplication) ?: return Result.failure()
            val controller = app.agentController ?: return Result.failure()

            val observer = GroundObserver(controller)
            observer.observe()

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "kira_ground_observer"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GroundWorker>(1, TimeUnit.MINUTES)
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
