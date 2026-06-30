package dev.wfhdiary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wfhdiary.app.ui.auth.AuthViewModel
import dev.wfhdiary.app.ui.auth.LoginScreen
import dev.wfhdiary.app.ui.month.MonthGridScreen
import dev.wfhdiary.app.ui.month.MonthGridViewModel
import dev.wfhdiary.app.ui.settings.SettingsScreen
import dev.wfhdiary.app.ui.settings.SettingsViewModel
import dev.wfhdiary.app.ui.theme.WfhDiaryTheme
import dev.wfhdiary.app.ui.update.AppUpdateViewModel
import dev.wfhdiary.app.update.GithubApkReleaseUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.factory(application)
    }
    private val monthViewModel: MonthGridViewModel by viewModels {
        MonthGridViewModel.factory(application)
    }
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.factory(application)
    }
    private val appUpdateViewModel: AppUpdateViewModel by viewModels {
        AppUpdateViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WfhDiaryTheme {
                val authState by authViewModel.uiState.collectAsStateWithLifecycle()
                val appUpdateState by appUpdateViewModel.uiState.collectAsStateWithLifecycle()
                val activity = LocalContext.current as ComponentActivity

                when {
                    authState.showLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    authState.showLoginScreen -> {
                        LoginScreen(
                            signInBusy = authState.signInBusy,
                            errorMessage = authState.errorMessage,
                            onSignInWithGoogle = { authViewModel.signInWithGoogle(activity) },
                            updateConfigured = GithubApkReleaseUpdater.isConfigured(),
                            updateBusy = appUpdateState.busy,
                            updateStatus = appUpdateState.status,
                            pendingUpdate = appUpdateState.pendingRelease,
                            showUpdateDialog = appUpdateState.showUpdateDialog,
                            onAutoCheckForUpdate = appUpdateViewModel::checkOnLoginIfNeeded,
                            onCheckForUpdate = { appUpdateViewModel.checkForUpdate(manual = true) },
                            onDismissUpdateDialog = appUpdateViewModel::dismissUpdateDialog,
                            onDownloadUpdate = { appUpdateViewModel.downloadAndInstall(activity) },
                        )
                    }
                    else -> {
                        MainAppContent(
                            settingsViewModel = settingsViewModel,
                            monthViewModel = monthViewModel,
                            appUpdateViewModel = appUpdateViewModel,
                            accountUserLabel = authState.userLabel,
                            onSignOut = authViewModel::signOut,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainAppContent(
    settingsViewModel: SettingsViewModel,
    monthViewModel: MonthGridViewModel,
    appUpdateViewModel: AppUpdateViewModel,
    accountUserLabel: String?,
    onSignOut: () -> Unit,
) {
    val monthState by monthViewModel.uiState.collectAsStateWithLifecycle()
    val settingsForm by settingsViewModel.settingsForm.collectAsStateWithLifecycle()
    val saveMessage by settingsViewModel.saveMessage.collectAsStateWithLifecycle()
    val notificationMessage by settingsViewModel.notificationMessage.collectAsStateWithLifecycle()
    val exportBusy by settingsViewModel.exportBusy.collectAsStateWithLifecycle()
    val exportStatus by settingsViewModel.exportStatus.collectAsStateWithLifecycle()
    val exportFyYear by settingsViewModel.exportFyYear.collectAsStateWithLifecycle()
    val githubUpdateUi by appUpdateViewModel.uiState.collectAsStateWithLifecycle()
    val geofenceBusy by settingsViewModel.geofenceBusy.collectAsStateWithLifecycle()
    val geofenceMessage by settingsViewModel.geofenceMessage.collectAsStateWithLifecycle()
    val geofenceMapPicker by settingsViewModel.geofenceMapPicker.collectAsStateWithLifecycle()
    val workScheduleCustomize by settingsViewModel.workScheduleCustomize.collectAsStateWithLifecycle()
    val geolocationSettingsOpen by settingsViewModel.geolocationSettingsOpen.collectAsStateWithLifecycle()
    val geolocationDraft by settingsViewModel.geolocationDraft.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    monthViewModel.refreshFromRemote(showProgress = false)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) {
            settingsViewModel.refreshSettings()
            settingsViewModel.syncExportFyYear(monthState.fyYear)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet {
                SettingsScreen(
                    form = settingsForm,
                    saveMessage = saveMessage,
                    notificationMessage = notificationMessage,
                    exportFyYear = exportFyYear,
                    exportBusy = exportBusy,
                    exportStatus = exportStatus,
                    onPreviousExportFyYear = settingsViewModel::showPreviousExportFyYear,
                    onNextExportFyYear = settingsViewModel::showNextExportFyYear,
                    onFormChange = settingsViewModel::updateForm,
                    onSendTestNotification = settingsViewModel::sendTestNotification,
                    onClearNotificationMessage = settingsViewModel::clearNotificationMessage,
                    onExportClick = { settingsViewModel.exportXlsx(activity) },
                    accountUserLabel = accountUserLabel,
                    onSignOut = onSignOut,
                    githubUpdateConfigured = GithubApkReleaseUpdater.isConfigured(),
                    githubUpdateBusy = githubUpdateUi.busy,
                    githubUpdateStatus = githubUpdateUi.status,
                    githubUpdateHasPendingApk = githubUpdateUi.pendingRelease != null,
                    onCheckGithubUpdate = { appUpdateViewModel.checkForUpdate(manual = true) },
                    onDownloadInstallGithubApk = {
                        appUpdateViewModel.downloadAndInstall(activity)
                    },
                    workScheduleCustomize = workScheduleCustomize,
                    onCustomizeHomeSchedule = settingsViewModel::openHomeScheduleCustomize,
                    onCustomizeOfficeSchedule = settingsViewModel::openOfficeScheduleCustomize,
                    onDismissWorkScheduleCustomize = settingsViewModel::dismissWorkScheduleCustomize,
                    onApplyWorkScheduleCustomize = settingsViewModel::applyWorkScheduleCustomize,
                    geolocationSettingsOpen = geolocationSettingsOpen,
                    geolocationDraft = geolocationDraft,
                    onOpenGeolocationSettings = settingsViewModel::openGeolocationSettings,
                    onDismissGeolocationSettings = settingsViewModel::dismissGeolocationSettings,
                    onGeolocationDraftChange = settingsViewModel::updateGeolocationDraft,
                    geofenceBusy = geofenceBusy,
                    geofenceMessage = geofenceMessage,
                    onSetHomeLocation = settingsViewModel::setHomeLocationFromGps,
                    onClearHomeLocation = settingsViewModel::clearHomeLocation,
                    onSetOfficeLocation = settingsViewModel::setOfficeLocationFromGps,
                    onClearOfficeLocation = settingsViewModel::clearOfficeLocation,
                    geofenceMapPicker = geofenceMapPicker,
                    onPickHomeOnMap = settingsViewModel::openHomeMapPicker,
                    onPickOfficeOnMap = settingsViewModel::openOfficeMapPicker,
                    onDismissGeofenceMapPicker = settingsViewModel::dismissGeofenceMapPicker,
                    onSaveGeofenceFromMap = settingsViewModel::saveGeofenceFromMap,
                    onClose = {
                        settingsViewModel.flushPendingSettings()
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Surface(Modifier.fillMaxSize()) {
            MonthGridScreen(
                uiState = monthState,
                onPreviousMonth = monthViewModel::showPreviousMonth,
                onNextMonth = monthViewModel::showNextMonth,
                onPreviousYear = monthViewModel::showPreviousYear,
                onNextYear = monthViewModel::showNextYear,
                onOpenSettings = { scope.launch { drawerState.open() } },
                onSelectDate = monthViewModel::selectDate,
                onSaveDay = monthViewModel::saveDay,
                onOpenLeaveSheet = monthViewModel::openLeaveSheet,
                onDismissLeaveSheet = monthViewModel::dismissLeaveSheet,
                onScheduleLeave = monthViewModel::scheduleLeave,
                onDeleteLeave = monthViewModel::deleteLeave,
                onDeleteDay = monthViewModel::deleteDay,
                onRemoveLeaveForDate = monthViewModel::removeLeaveForDate,
                onRefresh = { monthViewModel.refreshFromRemote(showProgress = true) },
            )
        }
    }
}
