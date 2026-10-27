package dev.wfhdiary.app.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.wfhdiary.app.WfhDiaryApplication

class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Failures are recorded in BackupPreferences and surfaced in the UI; retrying blindly won't fix a revoked folder.
        (applicationContext as WfhDiaryApplication).backupService.backupToFolder()
        return Result.success()
    }
}
