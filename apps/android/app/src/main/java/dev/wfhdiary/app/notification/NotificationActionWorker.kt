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
        val source = inputData.getString(KEY_SOURCE) ?: SOURCE_MANUAL
        val target =
            DayLogRetryPolicy.notificationTargetForWorker(
                inputData.getString(KEY_NOTIFICATION_TARGET),
                source,
            )
        val app = applicationContext as WfhDiaryApplication

        return runCatching {
            FirebaseTokenHelper.ensureToken(app)
            val settings = app.diaryRepository.getSettings()
            NotificationDayActions.apply(app, date, action, settings)
            MissedNotificationScheduler.cancel(applicationContext, date)
            when (source) {
                SOURCE_MANUAL, SOURCE_RETRY ->
                    DayLogNotificationUpdater.showManualSuccess(applicationContext, action)
                SOURCE_AUTO_APPLY -> {
                    // Success for auto-apply is shown by the caller when extra context is needed.
                }
            }
            Result.success()
        }.getOrElse { error ->
            DayLogNotificationUpdater.showFailure(
                context = applicationContext,
                action = action,
                date = date,
                reason = ApiErrorClassifier.classify(error),
                retryMode = NotificationChannels.RETRY_MODE_APPLY,
                target = target,
            )
            Result.failure()
        }
    }

    companion object {
        const val KEY_ACTION = "action"
        const val KEY_DATE = "date"
        const val KEY_SOURCE = "source"
        const val KEY_NOTIFICATION_TARGET = "notification_target"
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_RETRY = "retry"
        const val SOURCE_AUTO_APPLY = "auto_apply"
    }
}
