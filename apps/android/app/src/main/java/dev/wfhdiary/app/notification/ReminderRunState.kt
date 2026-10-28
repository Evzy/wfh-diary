package dev.wfhdiary.app.notification

import android.content.Context

/** Device-local record of the last date the morning reminder worker ran. Not backed up. */
class ReminderRunState(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lastRunDate: String?
        get() = prefs.getString(KEY_LAST_RUN_DATE, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_RUN_DATE, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "reminder_state"
        const val KEY_LAST_RUN_DATE = "last_run_date"
    }
}
