package dev.wfhdiary.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.wfhdiary.app.WfhDiaryApplication
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

        val onLeave =
            runCatching {
                app.diaryRepository.getLeavePeriods().any { period ->
                    val start = LocalDate.parse(period.startDate, iso)
                    val end = LocalDate.parse(period.endDate, iso)
                    !today.isBefore(start) && !today.isAfter(end)
                }
            }.getOrDefault(false)

        if (onLeave) {
            NotificationScheduler.scheduleNext(
                applicationContext,
                app.diaryRepository.getSettings().notificationTime,
            )
            return Result.success()
        }

        if (NotificationPermissions.canPost(applicationContext)) {
            MorningReminderNotifier.show(applicationContext, todayStr)
        }
        NotificationScheduler.scheduleNext(
            applicationContext,
            runCatching { app.diaryRepository.getSettings().notificationTime }.getOrDefault("08:00"),
        )
        return Result.success()
    }
}
