package dev.wfhdiary.app.backup

import dev.wfhdiary.app.data.model.UserSettingsDto
import dev.wfhdiary.app.data.store.DiaryStore
import kotlinx.coroutines.CancellationException

class RestoreService(
    private val store: DiaryStore,
    private val backupService: BackupService,
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
            when (val outcome = backupService.writeSafetyBackup()) {
                is BackupOutcome.Success -> outcome.fileName
                BackupOutcome.NoFolder ->
                    return RestoreOutcome.Failed("Choose a backup folder first so a safety backup can be saved. Nothing was changed.")
                BackupOutcome.NothingToBackUp ->
                    return RestoreOutcome.Failed("Could not save a safety backup, so nothing was changed.")
                is BackupOutcome.Failed ->
                    return RestoreOutcome.Failed(
                        "Could not save a safety backup to your backup folder, so nothing was changed: ${outcome.message}",
                    )
            }
        try {
            store.replaceAll(valid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RestoreOutcome.Failed("Restore failed and your data was not changed: ${e.message}")
        }
        try {
            onRestored(valid.settings)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The data is already committed; a rescheduling failure must not report the restore as failed.
        }
        return RestoreOutcome.Success(safety, valid.days.size, valid.leavePeriods.size)
    }
}
