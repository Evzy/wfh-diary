package dev.wfhdiary.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dev.wfhdiary.app.R

object NotificationChannels {
    const val MORNING_REMINDER = "morning_reminder"
    const val AUTO_LOG_RESULT = "auto_log_result"
    const val ACTION_HOME = "dev.wfhdiary.app.notification.HOME"
    const val ACTION_OFFICE = "dev.wfhdiary.app.notification.OFFICE"
    const val ACTION_LEAVE = "dev.wfhdiary.app.notification.LEAVE"
    const val ACTION_ADD_TIME = "dev.wfhdiary.app.notification.ADD_TIME"
    const val ACTION_RETRY = "dev.wfhdiary.app.notification.RETRY"
    const val EXTRA_DATE = "date"
    const val EXTRA_ACTION = "action"
    const val EXTRA_RETRY_MODE = "retry_mode"
    const val EXTRA_NOTIFICATION_TARGET = "notification_target"
    const val RETRY_MODE_APPLY = "apply"
    const val RETRY_MODE_MISSED = "missed"
    const val TARGET_MORNING = "morning"
    const val TARGET_DAY_LOG = "day_log"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                MORNING_REMINDER,
                context.getString(R.string.notification_channel_morning),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        mgr.createNotificationChannel(channel)
        val autoLogChannel =
            NotificationChannel(
                AUTO_LOG_RESULT,
                context.getString(R.string.notification_channel_auto_log),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        mgr.createNotificationChannel(autoLogChannel)
    }
}
