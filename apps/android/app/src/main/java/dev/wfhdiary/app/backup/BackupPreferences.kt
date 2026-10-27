package dev.wfhdiary.app.backup

import android.content.Context

/** Device-local backup state. Deliberately not part of the backup file. */
class BackupPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    var folderUri: String?
        get() = prefs.getString(KEY_FOLDER, null)
        set(value) = prefs.edit().putString(KEY_FOLDER, value).apply()

    var folderLabel: String?
        get() = prefs.getString(KEY_FOLDER_LABEL, null)
        set(value) = prefs.edit().putString(KEY_FOLDER_LABEL, value).apply()

    val lastSuccessMillis: Long?
        get() = prefs.getLong(KEY_SUCCESS, 0L).takeIf { it > 0L }

    val lastError: String?
        get() = prefs.getString(KEY_ERROR, null)

    fun recordSuccess(millis: Long) {
        prefs.edit().putLong(KEY_SUCCESS, millis).remove(KEY_ERROR).apply()
    }

    fun recordFailure(message: String) {
        prefs.edit().putString(KEY_ERROR, message).apply()
    }

    private companion object {
        const val KEY_FOLDER = "folder_uri"
        const val KEY_FOLDER_LABEL = "folder_label"
        const val KEY_SUCCESS = "last_success_millis"
        const val KEY_ERROR = "last_error"
    }
}
