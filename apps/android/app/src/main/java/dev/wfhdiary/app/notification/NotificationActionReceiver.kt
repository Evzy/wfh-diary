package dev.wfhdiary.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val date =
            intent.getStringExtra(NotificationChannels.EXTRA_DATE)
                ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        when (intent.action) {
            NotificationChannels.ACTION_HOME,
            NotificationChannels.ACTION_OFFICE,
            NotificationChannels.ACTION_LEAVE,
            -> handleManualAction(context, intent.action!!, date)
        }
    }

    private fun handleManualAction(context: Context, action: String, date: String) {
        MissedNotificationScheduler.cancel(context, date)
        DayLogWorkEnqueuer.enqueueManualAction(context, action, date)
    }
}
