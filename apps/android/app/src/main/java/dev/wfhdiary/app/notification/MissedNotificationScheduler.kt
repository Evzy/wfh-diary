package dev.wfhdiary.app.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.wfhdiary.app.util.TimeHelpers
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object MissedNotificationScheduler {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    fun workName(date: String): String = "wfh_missed_default_$date"

    fun schedule(
        context: Context,
        date: String,
        applyAtTime: String,
        default: String,
    ) {
        if (!MissedNotificationDefault.isEnabled(default)) return

        val day = runCatching { LocalDate.parse(date, iso) }.getOrNull() ?: return
        if (day != LocalDate.now()) return

        val minutes = TimeHelpers.parseTimeString(applyAtTime) ?: (12 * 60)
        val hour = minutes / 60
        val minute = minutes % 60
        val now = LocalDateTime.now()
        var target = LocalDateTime.of(day, LocalTime.of(hour, minute))
        val delayMs =
            if (target.isAfter(now)) {
                Duration.between(now, target).toMillis()
            } else {
                // Reminder was late or apply time already passed — apply soon.
                60_000L
            }

        val request =
            OneTimeWorkRequestBuilder<MissedNotificationWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(MissedNotificationWorker.KEY_DATE to date))
                .addTag(workName(date))
                .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(date), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, date: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(date))
    }
}
