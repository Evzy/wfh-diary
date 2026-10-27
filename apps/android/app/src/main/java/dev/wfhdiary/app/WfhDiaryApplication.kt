package dev.wfhdiary.app

import android.app.Application
import android.net.Uri
import dev.wfhdiary.app.backup.BackupPreferences
import dev.wfhdiary.app.backup.BackupScheduler
import dev.wfhdiary.app.backup.BackupService
import dev.wfhdiary.app.backup.RestoreService
import dev.wfhdiary.app.backup.SafBackupFolder
import dev.wfhdiary.app.data.local.WfhDiaryDatabase
import dev.wfhdiary.app.data.store.DiaryStore
import dev.wfhdiary.app.notification.NotificationChannels
import dev.wfhdiary.app.notification.NotificationScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WfhDiaryApplication : Application() {
    lateinit var database: WfhDiaryDatabase
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val diaryStore: DiaryStore by lazy { DiaryStore(database) }

    val backupPreferences: BackupPreferences by lazy { BackupPreferences(this) }

    val backupService: BackupService by lazy {
        BackupService(
            store = diaryStore,
            prefs = backupPreferences,
            appVersion = BuildConfig.VERSION_NAME,
            openFolder = { uri -> SafBackupFolder(this, Uri.parse(uri)) },
        )
    }

    val restoreService: RestoreService by lazy {
        RestoreService(diaryStore, backupService) { settings ->
            NotificationScheduler.scheduleNext(this, settings.notificationTime, settings.notificationWorkingDaysMask)
        }
    }

    override fun onCreate() {
        super.onCreate()
        database = WfhDiaryDatabase.getInstance(this)
        NotificationChannels.create(this)
        BackupScheduler.ensureDaily(this)
        appScope.launch {
            diaryStore.changes.collect {
                try {
                    BackupScheduler.scheduleAfterChange(this@WfhDiaryApplication)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Keep collecting; the daily job still runs if one schedule attempt fails.
                }
            }
        }
        appScope.launch {
            runCatching {
                val settings = diaryStore.getSettings()
                NotificationScheduler.scheduleNext(
                    this@WfhDiaryApplication,
                    settings.notificationTime,
                    settings.notificationWorkingDaysMask,
                )
            }
        }
    }
}
