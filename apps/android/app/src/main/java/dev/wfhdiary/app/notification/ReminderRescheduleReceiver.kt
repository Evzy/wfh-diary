package dev.wfhdiary.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.wfhdiary.app.WfhDiaryApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restores the reminder alarm after boot, app update, or a clock or time-zone change. */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val app = context.applicationContext as? WfhDiaryApplication ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = runCatching { app.diaryStore.getSettings() }.getOrNull()
                if (settings != null) {
                    NotificationScheduler.scheduleNext(
                        app,
                        settings.notificationTime,
                        settings.notificationWorkingDaysMask,
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
            )
    }
}
