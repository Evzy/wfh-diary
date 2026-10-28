package dev.wfhdiary.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationScheduler.enqueueRun(context, catchUp = false)
    }
}
