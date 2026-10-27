package dev.wfhdiary.app.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackupScheduler {
    private const val AFTER_CHANGE = "backup-after-change"
    private const val DAILY = "backup-daily"
    const val AFTER_CHANGE_DELAY_MINUTES = 10L

    fun scheduleAfterChange(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<BackupWorker>()
                .setInitialDelay(AFTER_CHANGE_DELAY_MINUTES, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(AFTER_CHANGE, ExistingWorkPolicy.REPLACE, request)
    }

    fun ensureDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
