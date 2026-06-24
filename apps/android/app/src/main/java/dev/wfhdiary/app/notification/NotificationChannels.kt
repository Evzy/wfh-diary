package dev.wfhdiary.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dev.wfhdiary.app.R

object NotificationChannels {
    const val MORNING_REMINDER = "morning_reminder"
    const val ACTION_HOME = "dev.wfhdiary.app.notification.HOME"
    const val ACTION_OFFICE = "dev.wfhdiary.app.notification.OFFICE"
    const val ACTION_LEAVE = "dev.wfhdiary.app.notification.LEAVE"
    const val ACTION_ADD_TIME = "dev.wfhdiary.app.notification.ADD_TIME"
    const val EXTRA_DATE = "date"

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
    }
}
