package dev.wfhdiary.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.wfhdiary.app.WfhDiaryApplication
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class NotificationActionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        val date =
            inputData.getString(KEY_DATE)
                ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val app = applicationContext as WfhDiaryApplication

        return runCatching {
            val settings = app.diaryRepository.getSettings()
            NotificationDayActions.apply(app, date, action, settings)
            MissedNotificationScheduler.cancel(applicationContext, date)
            DayLogNotificationUpdater.showManualSuccess(applicationContext, action)
            Result.success()
        }.getOrElse {
            DayLogNotificationUpdater.showFailure(
                context = applicationContext,
                action = action,
                date = date,
                target = DayLogNotificationTarget.MORNING_REMINDER,
            )
            Result.failure()
        }
    }

    companion object {
        const val KEY_ACTION = "action"
        const val KEY_DATE = "date"
    }
}
