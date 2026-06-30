package dev.wfhdiary.app.notification

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

object DayLogWorkEnqueuer {
    fun enqueueManualAction(context: Context, action: String, date: String) {
        enqueueApplyAction(
            context,
            action,
            date,
            NotificationActionWorker.SOURCE_MANUAL,
            DayLogNotificationTarget.MORNING_REMINDER,
        )
    }

    fun enqueueApplyAction(
        context: Context,
        action: String,
        date: String,
        source: String,
        target: DayLogNotificationTarget = DayLogNotificationTarget.DAY_LOG_RESULT,
    ) {
        val targetKey =
            DayLogRetryPolicy.notificationTargetKey(target)
        val request =
            OneTimeWorkRequestBuilder<NotificationActionWorker>()
                .setInputData(
                    workDataOf(
                        NotificationActionWorker.KEY_ACTION to action,
                        NotificationActionWorker.KEY_DATE to date,
                        NotificationActionWorker.KEY_SOURCE to source,
                        NotificationActionWorker.KEY_NOTIFICATION_TARGET to targetKey,
                    ),
                )
                .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun enqueueMissedFallback(context: Context, date: String) {
        val request =
            OneTimeWorkRequestBuilder<MissedNotificationWorker>()
                .setInputData(workDataOf(MissedNotificationWorker.KEY_DATE to date))
                .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
