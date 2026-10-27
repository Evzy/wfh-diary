package dev.wfhdiary.app.backup

import dev.wfhdiary.app.data.store.DiaryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class BackupService(
    private val store: DiaryStore,
    private val prefs: BackupPreferences,
    private val appVersion: String,
    private val openFolder: (String) -> BackupFolder,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val mutex = Mutex()

    suspend fun buildDocument(): BackupDocument =
        store.exportDocument(appVersion, OffsetDateTime.now(clock).toString())

    suspend fun backupToFolder(): BackupOutcome =
        mutex.withLock {
            val uri = prefs.folderUri ?: return BackupOutcome.NoFolder
            try {
                val text = BackupCodec.encode(buildDocument())
                val name = BackupRetention.fileNameFor(LocalDate.now(clock))
                val folder =
                    withContext(Dispatchers.IO) {
                        openFolder(uri).also { it.write(name, text) }
                    }
                prefs.recordSuccess(clock.millis())
                withContext(Dispatchers.IO) {
                    runCatching { BackupRetention.filesToDelete(folder.listNames()).forEach(folder::delete) }
                }
                BackupOutcome.Success(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                prefs.recordFailure(message)
                BackupOutcome.Failed(message)
            }
        }

    /** Writes a backup of the current data into the backup folder before a restore. */
    suspend fun writeSafetyBackup(): BackupOutcome {
        val uri = prefs.folderUri ?: return BackupOutcome.NoFolder
        val stamp = LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val name = "wfh-diary-pre-restore-$stamp.json"
        return try {
            val text = BackupCodec.encode(buildDocument())
            withContext(Dispatchers.IO) { openFolder(uri).write(name, text) }
            BackupOutcome.Success(name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackupOutcome.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Writes today's backup into [dir] for sharing. */
    suspend fun writeShareFile(dir: File): File {
        val text = BackupCodec.encode(buildDocument())
        return withContext(Dispatchers.IO) {
            dir.mkdirs()
            File(dir, BackupRetention.fileNameFor(LocalDate.now(clock))).apply { writeText(text) }
        }
    }
}
