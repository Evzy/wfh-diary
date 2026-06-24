package dev.wfhdiary.app

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import dev.wfhdiary.app.data.auth.FirebaseIdTokenHolder
import dev.wfhdiary.app.data.local.WfhDiaryDatabase
import dev.wfhdiary.app.data.remote.DiaryRepository
import dev.wfhdiary.app.notification.NotificationChannels
import dev.wfhdiary.app.notification.NotificationScheduler
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
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
                } else {
                    headers.append("X-Debug-User-Id", "dev-user")
                }
            }
        }
    }

    val diaryRepository: DiaryRepository by lazy {
        DiaryRepository(httpClient) { resolveApiBaseUrl() }
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
