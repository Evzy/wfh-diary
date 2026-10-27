package dev.wfhdiary.app.backup

sealed interface BackupOutcome {
    data object NoFolder : BackupOutcome

    /** The store is still in its fresh-install state; writing would overwrite a real same-day backup with nothing. */
    data object NothingToBackUp : BackupOutcome

    data class Success(val fileName: String) : BackupOutcome

    data class Failed(val message: String) : BackupOutcome
}
