package dev.wfhdiary.app.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.wfhdiary.app.MainActivity
import dev.wfhdiary.app.R
import dev.wfhdiary.app.util.GeofenceHelper
import dev.wfhdiary.app.util.TimeHelpers

enum class DayLogNotificationTarget {
    MORNING_REMINDER,
    DAY_LOG_RESULT,
}

object DayLogNotificationUpdater {
    const val MORNING_REMINDER_ID = MorningReminderNotifier.NOTIFICATION_ID
    const val DAY_LOG_RESULT_ID = 1002

    fun showManualSuccess(context: Context, action: String) {
        if (!NotificationPermissions.canPost(context)) return
        val label = actionLabel(context, action)
        val builder =
            NotificationCompat.Builder(context, NotificationChannels.MORNING_REMINDER)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(context.getString(R.string.notification_log_success_title))
                .setContentText(context.getString(R.string.notification_log_success_manual, label))
                .setAutoCancel(true)
                .setTimeoutAfter(8_000)
        NotificationManagerCompat.from(context).notify(MORNING_REMINDER_ID, builder.build())
    }

    fun showAutoSuccessGeolocationMatch(
        context: Context,
        place: GeofenceHelper.Place,
        applyAtTime: String,
    ) {
        val location =
            when (place) {
                GeofenceHelper.Place.HOME -> context.getString(R.string.notification_action_home)
                GeofenceHelper.Place.OFFICE -> context.getString(R.string.notification_action_office)
            }
        showAutoSuccessMessage(
            context,
            context.getString(
                R.string.notification_auto_log_geolocation,
                location,
                formatApplyAt(context, applyAtTime),
            ),
        )
    }

    fun showAutoSuccessGeolocationAway(
        context: Context,
        awayDefault: String,
        applyAtTime: String,
    ) {
        showAutoSuccessMessage(
            context,
            context.getString(
                R.string.notification_auto_log_away,
                actionLabel(context, awayDefault),
                formatApplyAt(context, applyAtTime),
            ),
        )
    }

    fun showAutoSuccessDefault(
        context: Context,
        default: String,
        applyAtTime: String,
    ) {
        showAutoSuccessMessage(
            context,
            context.getString(
                R.string.notification_auto_log_default,
                actionLabel(context, default),
                formatApplyAt(context, applyAtTime),
            ),
        )
    }

    fun showAutoSuccessMorningGeo(context: Context, place: GeofenceHelper.Place) {
        val label =
            when (place) {
                GeofenceHelper.Place.HOME -> context.getString(R.string.notification_action_home)
                GeofenceHelper.Place.OFFICE -> context.getString(R.string.notification_action_office)
            }
        showAutoSuccessMessage(
            context,
            context.getString(R.string.notification_auto_log_morning_geo, label),
        )
    }

    fun showFailure(
        context: Context,
        action: String,
        date: String,
        target: DayLogNotificationTarget,
    ) {
        if (!NotificationPermissions.canPost(context)) return
        val message = context.getString(R.string.notification_log_failure_local, actionLabel(context, action))
        val builder =
            NotificationCompat.Builder(context, channelFor(target))
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(context.getString(R.string.notification_log_failure_title))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .addAction(
                    0,
                    context.getString(R.string.notification_action_open_app),
                    openAppPendingIntent(context, date),
                )
        NotificationManagerCompat.from(context).notify(notificationIdFor(target), builder.build())
    }

    fun actionForPlace(place: GeofenceHelper.Place): String =
        when (place) {
            GeofenceHelper.Place.HOME -> MissedNotificationDefault.HOME
            GeofenceHelper.Place.OFFICE -> MissedNotificationDefault.OFFICE
        }

    fun actionLabel(context: Context, action: String): String =
        when (action) {
            NotificationChannels.ACTION_HOME, MissedNotificationDefault.HOME ->
                context.getString(R.string.notification_action_home)
            NotificationChannels.ACTION_OFFICE, MissedNotificationDefault.OFFICE ->
                context.getString(R.string.notification_action_office)
            NotificationChannels.ACTION_LEAVE, MissedNotificationDefault.LEAVE ->
                context.getString(R.string.notification_action_leave)
            MissedNotificationDefault.GEOLOCATION ->
                context.getString(R.string.settings_missed_geolocation)
            else -> action
        }

    private fun showAutoSuccessMessage(context: Context, message: String) {
        if (!NotificationPermissions.canPost(context)) return
        val builder =
            NotificationCompat.Builder(context, NotificationChannels.AUTO_LOG_RESULT)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(context.getString(R.string.notification_auto_log_title))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setTimeoutAfter(8_000)
        NotificationManagerCompat.from(context).notify(DAY_LOG_RESULT_ID, builder.build())
    }

    private fun notificationIdFor(target: DayLogNotificationTarget): Int =
        when (target) {
            DayLogNotificationTarget.MORNING_REMINDER -> MORNING_REMINDER_ID
            DayLogNotificationTarget.DAY_LOG_RESULT -> DAY_LOG_RESULT_ID
        }

    private fun channelFor(target: DayLogNotificationTarget): String =
        when (target) {
            DayLogNotificationTarget.MORNING_REMINDER -> NotificationChannels.MORNING_REMINDER
            DayLogNotificationTarget.DAY_LOG_RESULT -> NotificationChannels.AUTO_LOG_RESULT
        }

    private fun openAppPendingIntent(context: Context, date: String): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(NotificationChannels.EXTRA_DATE, date)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        return PendingIntent.getActivity(
            context,
            ("open-app-$date").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatApplyAt(context: Context, applyAtTime: String): String {
        val minutes = TimeHelpers.parseTimeString(applyAtTime) ?: return applyAtTime
        val hour24 = minutes / 60
        val minute = minutes % 60
        val amPm = if (hour24 < 12) "am" else "pm"
        val hour12 =
            when (val h = hour24 % 12) {
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
