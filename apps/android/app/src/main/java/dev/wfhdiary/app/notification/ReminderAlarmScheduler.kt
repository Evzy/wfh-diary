package dev.wfhdiary.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderAlarmScheduler {
    private const val REQUEST_CODE = 2001

    fun set(context: Context, at: LocalDateTime) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMs = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val operation = pendingIntent(context)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, operation)
                return
            } catch (_: SecurityException) {
                // Exact alarm access was revoked between the check and the call.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, operation)
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
