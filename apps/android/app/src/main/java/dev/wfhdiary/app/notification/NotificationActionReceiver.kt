package dev.wfhdiary.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val date =
            intent.getStringExtra(NotificationChannels.EXTRA_DATE)
                ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        MorningReminderNotifier.cancel(context)
        MissedNotificationScheduler.cancel(context, date)

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
