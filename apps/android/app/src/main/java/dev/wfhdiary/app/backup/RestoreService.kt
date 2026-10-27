package dev.wfhdiary.app.backup

import dev.wfhdiary.app.data.remote.UserSettingsDto
import dev.wfhdiary.app.data.store.DiaryStore
import kotlinx.coroutines.CancellationException
import java.io.File

class RestoreService(
    private val store: DiaryStore,
    private val backupService: BackupService,
    private val safetyFallbackDir: File,
    private val onRestored: suspend (UserSettingsDto) -> Unit,
) {
    fun preview(text: String): BackupParseResult = BackupCodec.decode(text)

    suspend fun restore(document: BackupDocument): RestoreOutcome {
        val valid =
            when (val result = BackupCodec.validate(document)) {
                is BackupParseResult.Failure -> return RestoreOutcome.Failed(result.message)
                is BackupParseResult.Success -> result.document
            }
        val safety =
            try {
                backupService.writeSafetyBackup(safetyFallbackDir)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return RestoreOutcome.Failed("Could not save a safety backup first, so nothing was changed: ${e.message}")
            }
        try {
            store.replaceAll(valid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RestoreOutcome.Failed("Restore failed and your data was not changed: ${e.message}")
        }
        runCatching { onRestored(valid.settings) }
        return RestoreOutcome.Success(safety, valid.days.size, valid.leavePeriods.size)
    }
}
