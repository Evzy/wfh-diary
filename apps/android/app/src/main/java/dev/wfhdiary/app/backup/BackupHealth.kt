package dev.wfhdiary.app.backup

enum class BackupWarning { NO_FOLDER, FAILED, STALE }

object BackupHealth {
    const val STALE_AFTER_MILLIS = 3L * 24 * 60 * 60 * 1000

    fun warning(folderUri: String?, lastSuccessMillis: Long?, lastError: String?, nowMillis: Long): BackupWarning? =
        when {
            folderUri == null -> BackupWarning.NO_FOLDER
            lastError != null -> BackupWarning.FAILED
            lastSuccessMillis == null || nowMillis - lastSuccessMillis > STALE_AFTER_MILLIS -> BackupWarning.STALE
            else -> null
        }
}
