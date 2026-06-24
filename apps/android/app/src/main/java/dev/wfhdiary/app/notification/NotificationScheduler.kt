package dev.wfhdiary.app.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.wfhdiary.app.util.TimeHelpers
import dev.wfhdiary.app.util.WorkingDaysMask
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    private const val WORK_NAME = "wfh_morning_reminder"
    private val nextReminderFormatter = DateTimeFormatter.ofPattern("EEE d MMM 'at' HH:mm")

    fun nextReminderAt(notificationTime: String, workingDaysMask: Int = WorkingDaysMask.DEFAULT): LocalDateTime {
        val minutes = TimeHelpers.parseTimeString(notificationTime) ?: (8 * 60)
        val hour = minutes / 60
        val minute = minutes % 60
        val time = LocalTime.of(hour, minute)
        val now = LocalDateTime.now()
        var date = LocalDate.now()
        var target = LocalDateTime.of(date, time)
        if (!target.isAfter(now)) {
            date = date.plusDays(1)
            target = LocalDateTime.of(date, time)
        }
        while (!WorkingDaysMask.isWorkingDay(workingDaysMask, date)) {
            date = date.plusDays(1)
            target = LocalDateTime.of(date, time)
        }
        return target
    }

    fun formatNextReminder(
        notificationTime: String,
        workingDaysMask: Int = WorkingDaysMask.DEFAULT,
    ): String = nextReminderAt(notificationTime, workingDaysMask).format(nextReminderFormatter)

    fun showTestNow(context: Context) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        MorningReminderNotifier.show(context, today)
    }

    fun scheduleNext(
        context: Context,
        notificationTime: String,
        workingDaysMask: Int = WorkingDaysMask.DEFAULT,
    ) {
        val now = LocalDateTime.now()
        val target = nextReminderAt(notificationTime, workingDaysMask)
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
