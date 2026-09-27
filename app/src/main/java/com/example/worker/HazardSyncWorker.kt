package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.RoutePilotDatabase
import java.util.concurrent.TimeUnit

/**
 * WorkManager background worker that cleans up stale cached hazards
 * so outdated hazard cache records are never treated as current live hazards.
 */
class HazardSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            val db = RoutePilotDatabase.getInstance(applicationContext)
            val cutoff = System.currentTimeMillis() - (30 * 60 * 1000L)
            db.routePilotDao().deleteStaleCachedHazards(cutoff)
            Result.success()
        }.getOrDefault(Result.retry())
    }

    companion object {
        private const val WORK_NAME = "routepilot_hazard_cache_cleanup"

        fun schedulePeriodicSync(context: Context) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<HazardSyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }
        }
    }
}
