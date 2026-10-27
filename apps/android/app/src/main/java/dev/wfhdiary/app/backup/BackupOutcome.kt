package dev.wfhdiary.app.backup

sealed interface BackupOutcome {
    data object NoFolder : BackupOutcome

    data class Success(val fileName: String) : BackupOutcome

    data class Failed(val message: String) : BackupOutcome
}
