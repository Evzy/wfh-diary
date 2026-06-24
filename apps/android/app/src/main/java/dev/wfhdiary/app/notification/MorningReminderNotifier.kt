package dev.wfhdiary.app.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.wfhdiary.app.R
import dev.wfhdiary.app.ui.quick.QuickAddActivity

object MorningReminderNotifier {
    private const val NOTIFICATION_ID = 1001

    fun show(context: Context, date: String) {
        val mgr = NotificationManagerCompat.from(context)
        val builder =
            NotificationCompat.Builder(context, NotificationChannels.MORNING_REMINDER)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(context.getString(R.string.notification_morning_title))
                .setContentText(context.getString(R.string.notification_morning_text))
                .setAutoCancel(true)
                .addAction(
                    0,
                    context.getString(R.string.notification_action_home),
                    actionPendingIntent(context, NotificationChannels.ACTION_HOME, date),
                )
                .addAction(
                    0,
                    context.getString(R.string.notification_action_office),
                    actionPendingIntent(context, NotificationChannels.ACTION_OFFICE, date),
                )
                .addAction(
                    0,
                    context.getString(R.string.notification_action_leave),
                    actionPendingIntent(context, NotificationChannels.ACTION_LEAVE, date),
                )
                .addAction(
                    0,
                    context.getString(R.string.notification_action_add_time),
                    addTimePendingIntent(context, date),
                )
        mgr.notify(NOTIFICATION_ID, builder.build())
    }

    private fun actionPendingIntent(context: Context, action: String, date: String): PendingIntent {
        val intent =
            Intent(context, NotificationActionReceiver::class.java).apply {
                this.action = action
                putExtra(NotificationChannels.EXTRA_DATE, date)
            }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun addTimePendingIntent(context: Context, date: String): PendingIntent {
        val intent =
            Intent(context, QuickAddActivity::class.java).apply {
                putExtra(NotificationChannels.EXTRA_DATE, date)
            }
        return PendingIntent.getActivity(
            context,
            date.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
