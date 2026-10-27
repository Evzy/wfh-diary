package dev.wfhdiary.app

import android.app.Application
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import dev.wfhdiary.app.backup.BackupPreferences
import dev.wfhdiary.app.backup.BackupScheduler
import dev.wfhdiary.app.backup.BackupService
import dev.wfhdiary.app.backup.RestoreService
import dev.wfhdiary.app.backup.SafBackupFolder
import dev.wfhdiary.app.data.DiaryDataSource
import dev.wfhdiary.app.data.auth.FirebaseIdTokenHolder
import dev.wfhdiary.app.data.local.WfhDiaryDatabase
import dev.wfhdiary.app.data.store.DiaryStore
import dev.wfhdiary.app.notification.NotificationChannels
import dev.wfhdiary.app.notification.NotificationScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WfhDiaryApplication : Application() {
    lateinit var database: WfhDiaryDatabase
        private set

    val firebaseIdTokenHolder = FirebaseIdTokenHolder()

    private val authSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

    val diaryRepository: DiaryDataSource
        get() = diaryStore

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.GOOGLE_SERVICES_CONFIGURED) {
            FirebaseAuth.getInstance().addAuthStateListener { auth ->
                val user = auth.currentUser
                if (user == null) {
                    firebaseIdTokenHolder.setToken(null)
                } else {
                    authSyncScope.launch {
                        runCatching {
                            val token = user.getIdToken(false).await().token
                            firebaseIdTokenHolder.setToken(token)
                        }.onFailure {
                            firebaseIdTokenHolder.setToken(null)
                        }
                    }
                }
            }
        }
        database = WfhDiaryDatabase.getInstance(this)
        NotificationChannels.create(this)
        BackupScheduler.ensureDaily(this)
        authSyncScope.launch {
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
        authSyncScope.launch {
            runCatching {
                val settings = diaryRepository.getSettings()
                NotificationScheduler.scheduleNext(
                    this@WfhDiaryApplication,
                    settings.notificationTime,
                    settings.notificationWorkingDaysMask,
                )
            }
        }
    }
}
