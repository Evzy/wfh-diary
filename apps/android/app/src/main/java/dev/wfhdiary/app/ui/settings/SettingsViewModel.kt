package dev.wfhdiary.app.ui.settings

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dev.wfhdiary.app.BuildConfig
import dev.wfhdiary.app.R
import dev.wfhdiary.app.WfhDiaryApplication
import dev.wfhdiary.app.data.auth.GoogleFirebaseSignIn
import dev.wfhdiary.app.data.prefs.ApiBackendMode
import dev.wfhdiary.app.data.prefs.AppSettingsPreferences
import dev.wfhdiary.app.data.remote.UpdateUserSettingsRequest
import dev.wfhdiary.app.notification.NotificationPermissions
import dev.wfhdiary.app.notification.NotificationScheduler
import dev.wfhdiary.app.update.GithubApkReleaseUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val wfhApp = application as WfhDiaryApplication
    private val prefs = wfhApp.appSettingsPreferences
    private val repo = wfhApp.diaryRepository

    private val _firebaseUserLabel = MutableStateFlow<String?>(null)
    val firebaseUserLabel = _firebaseUserLabel.asStateFlow()

    private val _firebaseSignInBusy = MutableStateFlow(false)
    val firebaseSignInBusy = _firebaseSignInBusy.asStateFlow()

    private val _firebaseAuthMessage = MutableStateFlow("")
    val firebaseAuthMessage = _firebaseAuthMessage.asStateFlow()

    private val _settingsForm = MutableStateFlow<SettingsFormState?>(null)
    val settingsForm = _settingsForm.asStateFlow()

    private val _saveMessage = MutableStateFlow("")
    val saveMessage = _saveMessage.asStateFlow()

    private val _notificationMessage = MutableStateFlow("")
    val notificationMessage = _notificationMessage.asStateFlow()

    private val _resolvedBaseUrl = MutableStateFlow("")
    val resolvedBaseUrl = _resolvedBaseUrl.asStateFlow()

    init {
        if (BuildConfig.GOOGLE_SERVICES_CONFIGURED) {
            val auth = FirebaseAuth.getInstance()
            fun syncLabel() {
                val u = auth.currentUser
                _firebaseUserLabel.value = u?.email?.takeIf { it.isNotBlank() } ?: u?.uid
            }
            syncLabel()
            auth.addAuthStateListener { syncLabel() }
        }
        refreshSettings()
        refreshBaseUrlMeta()
    }

    val apiBackendMode = prefs.apiBackendMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        initialValue = ApiBackendMode.API_AZURE,
    )

    fun refreshSettings() {
        viewModelScope.launch {
            runCatching {
                val s = repo.getSettings()
                _settingsForm.value =
                    SettingsFormState(
                        displayName = s.displayName,
                        notificationTime = s.notificationTime,
                        defaultHomeStart = s.defaultHomeStart,
                        defaultHomeFinish = s.defaultHomeFinish,
                        defaultHomeBreakHours = s.defaultHomeBreakHours.toString(),
                        defaultOfficeStart = s.defaultOfficeStart,
                        defaultOfficeFinish = s.defaultOfficeFinish,
                        defaultOfficeBreakHours = s.defaultOfficeBreakHours.toString(),
                    )
            }
        }
    }

    fun refreshBaseUrlMeta() {
        viewModelScope.launch {
            _resolvedBaseUrl.value = wfhApp.resolveApiBaseUrl()
        }
    }

    fun updateForm(transform: (SettingsFormState) -> SettingsFormState) {
        _settingsForm.update { current -> current?.let(transform) }
    }

    fun saveSettings() {
        val form = _settingsForm.value ?: return
        viewModelScope.launch {
            runCatching {
                val updated =
                    repo.updateSettings(
                        UpdateUserSettingsRequest(
                            displayName = form.displayName,
                            notificationTime = form.notificationTime,
                            defaultHomeStart = form.defaultHomeStart,
                            defaultHomeFinish = form.defaultHomeFinish,
                            defaultHomeBreakHours = form.defaultHomeBreakHours.toDoubleOrNull(),
                            defaultOfficeStart = form.defaultOfficeStart,
                            defaultOfficeFinish = form.defaultOfficeFinish,
                            defaultOfficeBreakHours = form.defaultOfficeBreakHours.toDoubleOrNull(),
                        ),
                    )
                NotificationScheduler.scheduleNext(getApplication(), updated.notificationTime)
                _saveMessage.value = getApplication<Application>().getString(R.string.settings_saved)
            }.onFailure { e ->
                _saveMessage.value = e.message.orEmpty()
            }
        }
    }

    fun sendTestNotification() {
        val app = getApplication<Application>()
        if (!NotificationPermissions.canPost(app)) {
            _notificationMessage.value = app.getString(R.string.settings_notification_permission_denied)
            return
        }
        NotificationScheduler.showTestNow(app)
        _notificationMessage.value = app.getString(R.string.settings_notification_test_sent)
    }

    fun clearNotificationMessage() {
        _notificationMessage.value = ""
    }

    suspend fun exportUrlAsync(): String {
        val fy = dev.wfhdiary.app.util.FinancialYearHelper.currentFyYear(java.time.LocalDate.now())
        return repo.exportXlsxUrl(fy)
    }

    fun setApiBackendMode(mode: ApiBackendMode) {
        viewModelScope.launch {
            prefs.setApiBackendMode(mode)
            refreshBaseUrlMeta()
        }
    }

    fun signInWithGoogle(activity: Activity) {
        val app = getApplication<Application>()
        if (!BuildConfig.GOOGLE_SERVICES_CONFIGURED) {
            _firebaseAuthMessage.value = app.getString(R.string.firebase_signin_need_json)
            return
        }
        viewModelScope.launch {
            _firebaseSignInBusy.value = true
            _firebaseAuthMessage.value = ""
            val result = withContext(Dispatchers.Main) { GoogleFirebaseSignIn.signIn(activity) }
            _firebaseSignInBusy.value = false
            result.exceptionOrNull()?.let { e ->
                _firebaseAuthMessage.value = e.message?.trim().orEmpty().ifEmpty { e.javaClass.simpleName }
            }
        }
    }

    fun signOutFirebase() {
        GoogleFirebaseSignIn.signOut()
        _firebaseAuthMessage.value = ""
    }

    private val _githubUpdateUi = MutableStateFlow(GithubUpdateUiState())
    val githubUpdateUiState = _githubUpdateUi.asStateFlow()

    fun checkGithubRelease() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            if (!GithubApkReleaseUpdater.isConfigured()) {
                _githubUpdateUi.update {
                    it.copy(
                        busy = false,
                        pendingRelease = null,
                        status = app.getString(R.string.github_update_not_configured),
                    )
                }
                return@launch
            }
            _githubUpdateUi.update {
                it.copy(busy = true, status = app.getString(R.string.github_update_checking))
            }
            GithubApkReleaseUpdater.fetchLatestApkRelease().fold(
                onSuccess = { info ->
                    _githubUpdateUi.update {
                        it.copy(
                            busy = false,
                            pendingRelease = info,
                            status = app.getString(R.string.github_update_found, info.tagName, info.assetName),
                        )
                    }
                },
                onFailure = { e ->
                    _githubUpdateUi.update {
                        it.copy(
                            busy = false,
                            pendingRelease = null,
                            status = e.message ?: app.getString(R.string.github_update_failed),
                        )
                    }
                },
            )
        }
    }

    fun downloadAndInstallGithubApk(activity: Activity) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val pending = _githubUpdateUi.value.pendingRelease
            if (pending == null) {
                _githubUpdateUi.update { it.copy(status = app.getString(R.string.github_update_check_first)) }
                return@launch
            }
            if (!GithubApkReleaseUpdater.ensureInstallFromUnknownSources(activity)) {
                _githubUpdateUi.update {
                    it.copy(status = app.getString(R.string.github_update_enable_unknown_sources))
                }
                return@launch
            }
            _githubUpdateUi.update {
                it.copy(busy = true, status = app.getString(R.string.github_update_downloading))
            }
            val result =
                withContext(Dispatchers.IO) {
                    GithubApkReleaseUpdater.downloadApkToCache(activity, pending.downloadUrl)
                }
            result.fold(
                onSuccess = { file ->
                    _githubUpdateUi.update {
                        it.copy(busy = false, status = app.getString(R.string.github_update_opening_installer))
                    }
                    GithubApkReleaseUpdater.startPackageInstall(activity, file)
                },
                onFailure = { e ->
                    _githubUpdateUi.update {
                        it.copy(
                            busy = false,
                            status = e.message ?: app.getString(R.string.github_update_failed),
                        )
                    }
                },
            )
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(application) as T
            }
    }
}
