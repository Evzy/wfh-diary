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
import dev.wfhdiary.app.data.remote.UpdateUserSettingsRequest
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

    private val _workScheduleCustomize = MutableStateFlow<WorkScheduleTarget?>(null)
    val workScheduleCustomize = _workScheduleCustomize.asStateFlow()

    private val _geofenceBusy = MutableStateFlow(false)
    val geofenceBusy = _geofenceBusy.asStateFlow()

    private val _geofenceMessage = MutableStateFlow("")
    val geofenceMessage = _geofenceMessage.asStateFlow()

    private val _geofenceMapPicker = MutableStateFlow<GeofenceMapTarget?>(null)
    val geofenceMapPicker = _geofenceMapPicker.asStateFlow()

    private val _geolocationSettingsOpen = MutableStateFlow(false)
    val geolocationSettingsOpen = _geolocationSettingsOpen.asStateFlow()

    private val _geolocationDraft = MutableStateFlow<GeolocationSettingsDraft?>(null)
    val geolocationDraft = _geolocationDraft.asStateFlow()

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
                        missedNotificationAwayDefault = s.missedNotificationAwayDefault,
                        geolocationSkipNotificationAtHome = s.geolocationSkipNotificationAtHome,
                        geolocationSkipNotificationAtOffice = s.geolocationSkipNotificationAtOffice,
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
                            missedNotificationAwayDefault = form.missedNotificationAwayDefault,
                            geolocationSkipNotificationAtHome = form.geolocationSkipNotificationAtHome,
                            geolocationSkipNotificationAtOffice = form.geolocationSkipNotificationAtOffice,
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

    fun openHomeScheduleCustomize() {
        _workScheduleCustomize.value = WorkScheduleTarget.HOME
    }

    fun openOfficeScheduleCustomize() {
        _workScheduleCustomize.value = WorkScheduleTarget.OFFICE
    }

    fun dismissWorkScheduleCustomize() {
        _workScheduleCustomize.value = null
    }

    fun applyWorkScheduleCustomize(updated: SettingsFormState) {
        _settingsForm.value = updated
        _workScheduleCustomize.value = null
    }

    fun openGeolocationSettings() {
        val form = _settingsForm.value ?: return
        _geolocationDraft.value = form.toGeolocationDraft()
        _geolocationSettingsOpen.value = true
        _geofenceMessage.value = ""
    }

    fun dismissGeolocationSettings() {
        _geolocationSettingsOpen.value = false
        _geolocationDraft.value = null
        _geofenceMapPicker.value = null
    }

    fun updateGeolocationDraft(transform: (GeolocationSettingsDraft) -> GeolocationSettingsDraft) {
        _geolocationDraft.update { current -> current?.let(transform) }
    }

    fun applyGeolocationSettings() {
        val draft = _geolocationDraft.value ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            _geofenceBusy.value = true
            runCatching {
                val updated = repo.updateSettings(geolocationRequestFrom(draft))
                _settingsForm.value =
                    draft.applyTo(_settingsForm.value ?: SettingsFormState()).let { merged ->
                        merged.copy(
                            missedNotificationAwayDefault = updated.missedNotificationAwayDefault,
                            geolocationSkipNotificationAtHome = updated.geolocationSkipNotificationAtHome,
                            geolocationSkipNotificationAtOffice = updated.geolocationSkipNotificationAtOffice,
                            homeGeofenceLatitude = updated.homeGeofenceLatitude,
                            homeGeofenceLongitude = updated.homeGeofenceLongitude,
                            homeGeofenceRadiusMeters = updated.homeGeofenceRadiusMeters,
                            officeGeofenceLatitude = updated.officeGeofenceLatitude,
                            officeGeofenceLongitude = updated.officeGeofenceLongitude,
                            officeGeofenceRadiusMeters = updated.officeGeofenceRadiusMeters,
                        )
                    }
                _geolocationSettingsOpen.value = false
                _geolocationDraft.value = null
                _geofenceMessage.value = app.getString(R.string.geolocation_settings_saved)
            }.onFailure { e ->
                _geofenceMessage.value = e.message.orEmpty()
            }
            _geofenceBusy.value = false
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
        if (!_geolocationSettingsOpen.value) return
        updateGeolocationDraft { draft ->
            when (target) {
                GeofenceMapTarget.HOME ->
                    draft.copy(
                        homeGeofenceLatitude = latitude,
                        homeGeofenceLongitude = longitude,
                        homeGeofenceRadiusMeters = radiusMeters,
                        clearHomeGeofence = false,
                    )
                GeofenceMapTarget.OFFICE ->
                    draft.copy(
                        officeGeofenceLatitude = latitude,
                        officeGeofenceLongitude = longitude,
                        officeGeofenceRadiusMeters = radiusMeters,
                        clearOfficeGeofence = false,
                    )
            }
        }
        val app = getApplication<Application>()
        _geofenceMessage.value =
            app.getString(
                when (target) {
                    GeofenceMapTarget.HOME -> R.string.location_geofence_home_saved
                    GeofenceMapTarget.OFFICE -> R.string.location_geofence_office_saved
                },
            )
    }

    fun clearHomeLocation() {
        clearGeofence(isHome = true)
    }

    fun clearOfficeLocation() {
        clearGeofence(isHome = false)
    }

    private fun captureGeofenceFromGps(isHome: Boolean) {
        val app = getApplication<Application>()
        if (!_geolocationSettingsOpen.value) return
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
                updateGeolocationDraft { draft ->
                    if (isHome) {
                        draft.copy(
                            homeGeofenceLatitude = location.latitude,
                            homeGeofenceLongitude = location.longitude,
                            homeGeofenceRadiusMeters = GeofenceHelper.DEFAULT_RADIUS_METERS,
                            clearHomeGeofence = false,
                        )
                    } else {
                        draft.copy(
                            officeGeofenceLatitude = location.latitude,
                            officeGeofenceLongitude = location.longitude,
                            officeGeofenceRadiusMeters = GeofenceHelper.DEFAULT_RADIUS_METERS,
                            clearOfficeGeofence = false,
                        )
                    }
                }
                _geofenceMessage.value =
                    app.getString(
                        if (isHome) R.string.location_geofence_home_saved else R.string.location_geofence_office_saved,
                    )
            }.onFailure { e ->
                _geofenceMessage.value = e.message.orEmpty()
            }
            _geofenceBusy.value = false
        }
    }

    private fun clearGeofence(isHome: Boolean) {
        if (!_geolocationSettingsOpen.value) return
        updateGeolocationDraft { draft ->
            if (isHome) {
                draft.copy(
                    homeGeofenceLatitude = null,
                    homeGeofenceLongitude = null,
                    homeGeofenceRadiusMeters = null,
                    clearHomeGeofence = true,
                )
            } else {
                draft.copy(
                    officeGeofenceLatitude = null,
                    officeGeofenceLongitude = null,
                    officeGeofenceRadiusMeters = null,
                    clearOfficeGeofence = true,
                )
            }
        }
        _geofenceMessage.value = ""
    }

    private fun geolocationRequestFrom(draft: GeolocationSettingsDraft): UpdateUserSettingsRequest =
        UpdateUserSettingsRequest(
            missedNotificationAwayDefault = draft.missedNotificationAwayDefault,
            geolocationSkipNotificationAtHome = draft.skipNotificationAtHome,
            geolocationSkipNotificationAtOffice = draft.skipNotificationAtOffice,
            homeGeofenceLatitude = if (!draft.clearHomeGeofence) draft.homeGeofenceLatitude else null,
            homeGeofenceLongitude = if (!draft.clearHomeGeofence) draft.homeGeofenceLongitude else null,
            homeGeofenceRadiusMeters = if (!draft.clearHomeGeofence) draft.homeGeofenceRadiusMeters else null,
            officeGeofenceLatitude = if (!draft.clearOfficeGeofence) draft.officeGeofenceLatitude else null,
            officeGeofenceLongitude = if (!draft.clearOfficeGeofence) draft.officeGeofenceLongitude else null,
            officeGeofenceRadiusMeters = if (!draft.clearOfficeGeofence) draft.officeGeofenceRadiusMeters else null,
            clearHomeGeofence = draft.clearHomeGeofence,
            clearOfficeGeofence = draft.clearOfficeGeofence,
        )

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
