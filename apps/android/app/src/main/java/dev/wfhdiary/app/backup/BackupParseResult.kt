package dev.wfhdiary.app.backup

sealed interface BackupParseResult {
    data class Success(val document: BackupDocument) : BackupParseResult

    data class Failure(val message: String) : BackupParseResult
}
