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
            NotificationChannels.ACTION_RETRY -> handleRetry(context, intent, date)
            NotificationChannels.ACTION_HOME,
            NotificationChannels.ACTION_OFFICE,
            NotificationChannels.ACTION_LEAVE,
            -> handleManualAction(context, intent.action!!, date)
        }
    }

    private fun handleManualAction(context: Context, action: String, date: String) {
        MissedNotificationScheduler.cancel(context, date)
        DayLogNotificationUpdater.showPending(
            context,
            action,
            DayLogNotificationTarget.MORNING_REMINDER,
        )
        DayLogWorkEnqueuer.enqueueManualAction(context, action, date)
    }

    private fun handleRetry(context: Context, intent: Intent, date: String) {
        val action = intent.getStringExtra(NotificationChannels.EXTRA_ACTION) ?: return
        val retryMode =
            intent.getStringExtra(NotificationChannels.EXTRA_RETRY_MODE)
                ?: NotificationChannels.RETRY_MODE_APPLY
        val target = notificationTarget(intent)

        MissedNotificationScheduler.cancel(context, date)
        DayLogNotificationUpdater.showPending(context, action, target)

        when (retryMode) {
            NotificationChannels.RETRY_MODE_MISSED ->
                DayLogWorkEnqueuer.enqueueMissedFallback(context, date)
            else ->
                DayLogWorkEnqueuer.enqueueApplyAction(
                    context,
                    action,
                    date,
                    NotificationActionWorker.SOURCE_RETRY,
                    target,
                )
        }
    }

    private fun notificationTarget(intent: Intent): DayLogNotificationTarget =
        DayLogRetryPolicy.notificationTargetFromKey(
            intent.getStringExtra(NotificationChannels.EXTRA_NOTIFICATION_TARGET),
        )
}
