package dev.wfhdiary.app.notification

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

object DayLogWorkEnqueuer {
    fun enqueueManualAction(context: Context, action: String, date: String) {
        val request =
            OneTimeWorkRequestBuilder<NotificationActionWorker>()
                .setInputData(
                    workDataOf(
                        NotificationActionWorker.KEY_ACTION to action,
                        NotificationActionWorker.KEY_DATE to date,
                    ),
                )
                .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
