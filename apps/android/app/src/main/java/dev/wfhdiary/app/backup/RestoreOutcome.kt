package dev.wfhdiary.app.backup

sealed interface RestoreOutcome {
    data class Success(val safetyBackup: String, val days: Int, val leavePeriods: Int) : RestoreOutcome

    data class Failed(val message: String) : RestoreOutcome
}
