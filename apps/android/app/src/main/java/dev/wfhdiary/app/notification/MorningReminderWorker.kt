package dev.wfhdiary.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.wfhdiary.app.WfhDiaryApplication
import dev.wfhdiary.app.util.WorkingDaysMask
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MorningReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as WfhDiaryApplication
        val today = LocalDate.now()
        val iso = DateTimeFormatter.ISO_LOCAL_DATE
        val todayStr = today.format(iso)

        val settings =
            runCatching { app.diaryRepository.getSettings() }
                .getOrNull()

        val notificationTime = settings?.notificationTime ?: "08:00"
        val workingDaysMask = settings?.notificationWorkingDaysMask ?: WorkingDaysMask.DEFAULT

        fun reschedule() {
            NotificationScheduler.scheduleNext(applicationContext, notificationTime, workingDaysMask)
        }

        if (!WorkingDaysMask.isWorkingDay(workingDaysMask, today)) {
            reschedule()
            return Result.success()
        }

        val onLeave =
            runCatching {
                app.diaryRepository.getLeavePeriods().any { period ->
                    val start = LocalDate.parse(period.startDate, iso)
                    val end = LocalDate.parse(period.endDate, iso)
                    !today.isBefore(start) && !today.isAfter(end)
                }
            }.getOrDefault(false)

        if (onLeave) {
            reschedule()
            return Result.success()
        }

        if (NotificationPermissions.canPost(applicationContext)) {
            MorningReminderNotifier.show(applicationContext, todayStr)
        }
        reschedule()
        return Result.success()
    }
}
