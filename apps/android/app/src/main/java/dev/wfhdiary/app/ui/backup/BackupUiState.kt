package dev.wfhdiary.app.ui.backup

import dev.wfhdiary.app.backup.BackupDocument
import dev.wfhdiary.app.backup.BackupWarning

enum class RestoreSource { FILE, SERVER }

data class PendingRestore(
    val document: BackupDocument,
    val source: RestoreSource,
)

data class BackupUiState(
    val folderChosen: Boolean = false,
    val folderLabel: String? = null,
    val lastSuccessMillis: Long? = null,
    val lastError: String? = null,
    val warning: BackupWarning? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val pendingRestore: PendingRestore? = null,
    /** Incremented after each successful restore so screens can reload. */
    val restoreGeneration: Int = 0,
)
