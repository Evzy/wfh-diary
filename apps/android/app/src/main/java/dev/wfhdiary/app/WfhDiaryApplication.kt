package dev.wfhdiary.app

import android.app.Application
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import dev.wfhdiary.app.backup.BackupPreferences
import dev.wfhdiary.app.backup.BackupScheduler
import dev.wfhdiary.app.backup.BackupService
import dev.wfhdiary.app.backup.LegacyServerImporter
import dev.wfhdiary.app.backup.RestoreService
import dev.wfhdiary.app.backup.SafBackupFolder
import dev.wfhdiary.app.data.DiaryDataSource
import dev.wfhdiary.app.data.auth.FirebaseIdTokenHolder
import dev.wfhdiary.app.data.local.WfhDiaryDatabase
import dev.wfhdiary.app.data.remote.DiaryRepository
import dev.wfhdiary.app.data.store.DiaryStore
import dev.wfhdiary.app.notification.NotificationChannels
import dev.wfhdiary.app.notification.NotificationScheduler
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

class WfhDiaryApplication : Application() {
    lateinit var database: WfhDiaryDatabase
        private set

    val firebaseIdTokenHolder = FirebaseIdTokenHolder()

    private val authSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val httpClient: HttpClient by lazy {
        HttpClient(Android) {
            expectSuccess = true
            install(HttpTimeout) {
                connectTimeoutMillis = 60_000
                requestTimeoutMillis = 90_000
                socketTimeoutMillis = 90_000
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = false
                        isLenient = true
                    },
                )
            }
            defaultRequest {
                val token = firebaseIdTokenHolder.getToken()
                if (!token.isNullOrBlank()) {
                    headers.append(HttpHeaders.Authorization, "Bearer $token")
                } else if (!BuildConfig.GOOGLE_SERVICES_CONFIGURED) {
                    headers.append("X-Debug-User-Id", "dev-user")
                }
            }
        }
    }

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

    /** Old server API; used only by the one-time importer in phase 1. */
    val remoteDiaryRepository: DiaryRepository by lazy {
        DiaryRepository(httpClient) { resolveApiBaseUrl() }
    }

    val legacyServerImporter: LegacyServerImporter by lazy {
        LegacyServerImporter(remoteDiaryRepository, BuildConfig.VERSION_NAME)
    }

    suspend fun resolveApiBaseUrl(): String {
        val url = BuildConfig.AZURE_API_BASE_URL.trim().trimEnd('/')
        return "$url/"
    }

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
