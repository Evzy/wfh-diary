package dev.wfhdiary.app.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.wfhdiary.app.util.TimeHelpers
import dev.wfhdiary.app.util.WorkingDaysMask
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object NotificationScheduler {
    private const val LEGACY_WORK_NAME = "wfh_morning_reminder"
    const val RUN_WORK_NAME = "wfh_morning_reminder_run"
    private val nextReminderFormatter = DateTimeFormatter.ofPattern("EEE d MMM 'at' HH:mm")

    private fun reminderTime(notificationTime: String): LocalTime {
        val minutes = TimeHelpers.parseTimeString(notificationTime) ?: (8 * 60)
        return LocalTime.of(minutes / 60, minutes % 60)
    }

    fun nextReminderAt(
        notificationTime: String,
        workingDaysMask: Int = WorkingDaysMask.DEFAULT,
        now: LocalDateTime = LocalDateTime.now(),
    ): LocalDateTime {
        val time = reminderTime(notificationTime)
        var date = now.toLocalDate()
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

    fun catchUpDue(
        notificationTime: String,
        workingDaysMask: Int,
        lastRunDate: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean {
        val today = now.toLocalDate()
        if (!WorkingDaysMask.isWorkingDay(workingDaysMask, today)) return false
        if (now.isBefore(LocalDateTime.of(today, reminderTime(notificationTime)))) return false
        return lastRunDate != today.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    fun formatNextReminder(
        notificationTime: String,
        workingDaysMask: Int = WorkingDaysMask.DEFAULT,
    ): String = nextReminderAt(notificationTime, workingDaysMask).format(nextReminderFormatter)

    fun showTestNow(context: Context) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        MorningReminderNotifier.show(context, today)
    }

    /**
     * Sets the alarm for the next reminder. When [allowCatchUp] is true and today's reminder
     * time has passed without a run, the reminder runs now as a catch-up run.
     */
    fun scheduleNext(
        context: Context,
        notificationTime: String,
        workingDaysMask: Int = WorkingDaysMask.DEFAULT,
        allowCatchUp: Boolean = true,
    ) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_WORK_NAME)
        if (allowCatchUp &&
            catchUpDue(notificationTime, workingDaysMask, ReminderRunState(context).lastRunDate)
        ) {
            enqueueRun(context, catchUp = true)
        }
        ReminderAlarmScheduler.set(context, nextReminderAt(notificationTime, workingDaysMask))
    }

    fun enqueueRun(context: Context, catchUp: Boolean) {
        val request =
            OneTimeWorkRequestBuilder<MorningReminderWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(MorningReminderWorker.KEY_CATCH_UP to catchUp))
                .addTag(RUN_WORK_NAME)
                .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(RUN_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
