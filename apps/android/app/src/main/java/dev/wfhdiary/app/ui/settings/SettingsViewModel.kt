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
import dev.wfhdiary.app.data.remote.CreateWorkTemplateRequest
import dev.wfhdiary.app.data.remote.UpdateUserSettingsRequest
import dev.wfhdiary.app.data.remote.WorkTemplateDto
import dev.wfhdiary.app.export.XlsxExportHelper
import dev.wfhdiary.app.util.GeofenceHelper
import dev.wfhdiary.app.util.LocationHelper
import dev.wfhdiary.app.notification.NotificationPermissions
import dev.wfhdiary.app.notification.NotificationScheduler
import dev.wfhdiary.app.update.GithubApkReleaseUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    enum class GeofenceMapTarget { HOME, OFFICE }

    private val wfhApp = application as WfhDiaryApplication
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

    private val _exportBusy = MutableStateFlow(false)
    val exportBusy = _exportBusy.asStateFlow()

    private val _exportStatus = MutableStateFlow("")
    val exportStatus = _exportStatus.asStateFlow()

    private val _workTemplates = MutableStateFlow<List<WorkTemplateDto>>(emptyList())
    val workTemplates = _workTemplates.asStateFlow()

    private val _showWorkTemplatesSheet = MutableStateFlow(false)
    val showWorkTemplatesSheet = _showWorkTemplatesSheet.asStateFlow()

    private val _geofenceBusy = MutableStateFlow(false)
    val geofenceBusy = _geofenceBusy.asStateFlow()

    private val _geofenceMessage = MutableStateFlow("")
    val geofenceMessage = _geofenceMessage.asStateFlow()

    private val _geofenceMapPicker = MutableStateFlow<GeofenceMapTarget?>(null)
    val geofenceMapPicker = _geofenceMapPicker.asStateFlow()

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
    }

    fun refreshSettings() {
        viewModelScope.launch {
            runCatching {
                val s = repo.getSettings()
                _settingsForm.value =
                    SettingsFormState(
                        displayName = s.displayName,
                        notificationTime = s.notificationTime,
                        notificationWorkingDaysMask = s.notificationWorkingDaysMask,
                        missedNotificationDefault = s.missedNotificationDefault,
                        missedNotificationApplyAt = s.missedNotificationApplyAt,
                        defaultHomeStart = s.defaultHomeStart,
                        defaultHomeFinish = s.defaultHomeFinish,
                        defaultHomeBreakHours = s.defaultHomeBreakHours.toString(),
                        defaultOfficeStart = s.defaultOfficeStart,
                        defaultOfficeFinish = s.defaultOfficeFinish,
                        defaultOfficeBreakHours = s.defaultOfficeBreakHours.toString(),
                        homeGeofenceLatitude = s.homeGeofenceLatitude,
                        homeGeofenceLongitude = s.homeGeofenceLongitude,
                        homeGeofenceRadiusMeters = s.homeGeofenceRadiusMeters,
                        officeGeofenceLatitude = s.officeGeofenceLatitude,
                        officeGeofenceLongitude = s.officeGeofenceLongitude,
                        officeGeofenceRadiusMeters = s.officeGeofenceRadiusMeters,
                    )
            }
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
                            notificationWorkingDaysMask = form.notificationWorkingDaysMask,
                            missedNotificationDefault = form.missedNotificationDefault,
                            missedNotificationApplyAt = form.missedNotificationApplyAt,
                            defaultHomeStart = form.defaultHomeStart,
                            defaultHomeFinish = form.defaultHomeFinish,
                            defaultHomeBreakHours = form.defaultHomeBreakHours.toDoubleOrNull(),
                            defaultOfficeStart = form.defaultOfficeStart,
                            defaultOfficeFinish = form.defaultOfficeFinish,
                            defaultOfficeBreakHours = form.defaultOfficeBreakHours.toDoubleOrNull(),
                        ),
                    )
                NotificationScheduler.scheduleNext(
                    getApplication(),
                    updated.notificationTime,
                    updated.notificationWorkingDaysMask,
                )
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

    fun openWorkTemplatesSheet() {
        _showWorkTemplatesSheet.value = true
        refreshWorkTemplates()
    }

    fun dismissWorkTemplatesSheet() {
        _showWorkTemplatesSheet.value = false
    }

    fun refreshWorkTemplates() {
        viewModelScope.launch {
            runCatching {
                _workTemplates.value = repo.getWorkTemplates()
            }
        }
    }

    fun createWorkTemplate(request: CreateWorkTemplateRequest) {
        viewModelScope.launch {
            runCatching {
                repo.createWorkTemplate(request)
                refreshWorkTemplates()
            }.onFailure { e ->
                _saveMessage.value = e.message.orEmpty()
            }
        }
    }

    fun deleteWorkTemplate(id: String) {
        viewModelScope.launch {
            runCatching {
                repo.deleteWorkTemplate(id)
                refreshWorkTemplates()
            }.onFailure { e ->
                _saveMessage.value = e.message.orEmpty()
            }
        }
    }

    fun clearGeofenceMessage() {
        _geofenceMessage.value = ""
    }

    fun setHomeLocationFromGps() {
        captureGeofenceFromGps(isHome = true)
    }

    fun setOfficeLocationFromGps() {
        captureGeofenceFromGps(isHome = false)
    }

    fun openHomeMapPicker() {
        _geofenceMapPicker.value = GeofenceMapTarget.HOME
    }

    fun openOfficeMapPicker() {
        _geofenceMapPicker.value = GeofenceMapTarget.OFFICE
    }

    fun dismissGeofenceMapPicker() {
        _geofenceMapPicker.value = null
    }

    fun saveGeofenceFromMap(
        target: GeofenceMapTarget,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ) {
        _geofenceMapPicker.value = null
        viewModelScope.launch {
            _geofenceBusy.value = true
            runCatching {
                saveGeofenceAt(
                    isHome = target == GeofenceMapTarget.HOME,
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = radiusMeters,
                )
            }.onFailure { e ->
                _geofenceMessage.value = e.message.orEmpty()
            }
            _geofenceBusy.value = false
        }
    }

    fun clearHomeLocation() {
        clearGeofence(isHome = true)
    }

    fun clearOfficeLocation() {
        clearGeofence(isHome = false)
    }

    private fun captureGeofenceFromGps(isHome: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _geofenceBusy.value = true
            _geofenceMessage.value = app.getString(R.string.location_geofence_fetching)
            runCatching {
                if (!LocationHelper.hasLocationPermission(app)) {
                    error(app.getString(R.string.work_templates_location_permission))
                }
                val location =
                    LocationHelper.getCurrentLocation(app)
                        ?: error(app.getString(R.string.location_geofence_unavailable))
                saveGeofenceAt(
                    isHome = isHome,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    radiusMeters = GeofenceHelper.DEFAULT_RADIUS_METERS,
                )
            }.onFailure { e ->
                _geofenceMessage.value = e.message.orEmpty()
            }
            _geofenceBusy.value = false
        }
    }

    private suspend fun saveGeofenceAt(
        isHome: Boolean,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ) {
        val app = getApplication<Application>()
        val updated =
            repo.updateSettings(
                if (isHome) {
                    UpdateUserSettingsRequest(
                        homeGeofenceLatitude = latitude,
                        homeGeofenceLongitude = longitude,
                        homeGeofenceRadiusMeters = radiusMeters,
                    )
                } else {
                    UpdateUserSettingsRequest(
                        officeGeofenceLatitude = latitude,
                        officeGeofenceLongitude = longitude,
                        officeGeofenceRadiusMeters = radiusMeters,
                    )
                },
            )
        applyGeofenceForm(updated)
        _geofenceMessage.value =
            app.getString(
                if (isHome) R.string.location_geofence_home_saved else R.string.location_geofence_office_saved,
            )
    }

    private fun applyGeofenceForm(updated: dev.wfhdiary.app.data.remote.UserSettingsDto) {
        _settingsForm.value =
            _settingsForm.value?.copy(
                homeGeofenceLatitude = updated.homeGeofenceLatitude,
                homeGeofenceLongitude = updated.homeGeofenceLongitude,
                homeGeofenceRadiusMeters = updated.homeGeofenceRadiusMeters,
                officeGeofenceLatitude = updated.officeGeofenceLatitude,
                officeGeofenceLongitude = updated.officeGeofenceLongitude,
                officeGeofenceRadiusMeters = updated.officeGeofenceRadiusMeters,
            )
    }

    private fun clearGeofence(isHome: Boolean) {
        viewModelScope.launch {
            _geofenceBusy.value = true
            runCatching {
                val updated =
                    repo.updateSettings(
                        if (isHome) {
                            UpdateUserSettingsRequest(clearHomeGeofence = true)
                        } else {
                            UpdateUserSettingsRequest(clearOfficeGeofence = true)
                        },
                    )
                applyGeofenceForm(updated)
            }.onFailure { e ->
                _geofenceMessage.value = e.message.orEmpty()
            }
            _geofenceBusy.value = false
        }
    }

    fun exportXlsx(fyYear: Int, activity: Activity) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _exportBusy.value = true
            _exportStatus.value = app.getString(R.string.settings_export_downloading)
            runCatching {
                val (bytes, filename) =
                    withContext(Dispatchers.IO) { repo.downloadExportXlsx(fyYear) }
                withContext(Dispatchers.Main) {
                    XlsxExportHelper.saveAndShare(activity, bytes, filename)
                }
                _exportStatus.value = ""
            }.onFailure { e ->
                _exportStatus.value = e.message?.trim().orEmpty().ifEmpty {
                    app.getString(R.string.settings_export_failed)
                }
            }
            _exportBusy.value = false
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
