package dev.wfhdiary.app.notification

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.wfhdiary.app.R
import dev.wfhdiary.app.util.GeofenceHelper
import dev.wfhdiary.app.util.TimeHelpers

object AutoLogResultNotifier {
    const val NOTIFICATION_ID = 1002

    fun showGeolocationMatch(
        context: Context,
        place: GeofenceHelper.Place,
        applyAtTime: String,
    ) {
        val location =
            when (place) {
                GeofenceHelper.Place.HOME -> context.getString(R.string.notification_action_home)
                GeofenceHelper.Place.OFFICE -> context.getString(R.string.notification_action_office)
            }
        show(
            context,
            context.getString(
                R.string.notification_auto_log_geolocation,
                location,
                formatApplyAt(context, applyAtTime),
            ),
        )
    }

    fun showGeolocationAway(
        context: Context,
        awayDefault: String,
        applyAtTime: String,
    ) {
        show(
            context,
            context.getString(
                R.string.notification_auto_log_away,
                locationLabel(context, awayDefault),
                formatApplyAt(context, applyAtTime),
            ),
        )
    }

    fun showDefault(
        context: Context,
        default: String,
        applyAtTime: String,
    ) {
        show(
            context,
            context.getString(
                R.string.notification_auto_log_default,
                locationLabel(context, default),
                formatApplyAt(context, applyAtTime),
            ),
        )
    }

    private fun show(context: Context, message: String) {
        val mgr = NotificationManagerCompat.from(context)
        val builder =
            NotificationCompat.Builder(context, NotificationChannels.AUTO_LOG_RESULT)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(context.getString(R.string.notification_auto_log_title))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
        mgr.notify(NOTIFICATION_ID, builder.build())
    }

    private fun locationLabel(context: Context, action: String): String =
        when (action) {
            MissedNotificationDefault.HOME -> context.getString(R.string.notification_action_home)
            MissedNotificationDefault.OFFICE -> context.getString(R.string.notification_action_office)
            MissedNotificationDefault.LEAVE -> context.getString(R.string.notification_action_leave)
            else -> action
        }

    private fun formatApplyAt(context: Context, applyAtTime: String): String {
        val minutes = TimeHelpers.parseTimeString(applyAtTime) ?: return applyAtTime
        val hour24 = minutes / 60
        val minute = minutes % 60
        val amPm = if (hour24 < 12) "am" else "pm"
        val hour12 = when (val h = hour24 % 12) {
            0 -> 12
            else -> h
        }
        return if (minute == 0) {
            context.getString(R.string.notification_auto_log_time_on_hour, hour12, amPm)
        } else {
            context.getString(R.string.notification_auto_log_time, hour12, minute, amPm)
        }
    }
}
