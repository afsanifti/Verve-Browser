package com.verve.browser.data.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = GitHubReleaseUpdateRepository()
        val fetchResult = repository.fetchAvailableUpdate()

        return fetchResult.fold(
            onSuccess = { manifest ->
                if (manifest != null) {
                    UpdateNotificationHelper.showUpdateAvailableNotification(
                        context = applicationContext,
                        manifest = manifest
                    )
                } else {
                    UpdateNotificationHelper.cancelUpdateNotification(applicationContext)
                }
                Result.success()
            },
            onFailure = {
                Result.retry()
            }
        )
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "verve_periodic_update_check"

        fun scheduleEveryTwoHours(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                2L,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}