package dev.wfhdiary.app.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.wfhdiary.app.util.TimeHelpers
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    private const val WORK_NAME = "wfh_morning_reminder"

    fun scheduleNext(context: Context, notificationTime: String) {
        val minutes = TimeHelpers.parseTimeString(notificationTime) ?: (8 * 60)
        val hour = minutes / 60
        val minute = minutes % 60
        val now = LocalDateTime.now()
        var target = LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute))
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        val delayMs = Duration.between(now, target).toMillis()
        val request =
            OneTimeWorkRequestBuilder<MorningReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
