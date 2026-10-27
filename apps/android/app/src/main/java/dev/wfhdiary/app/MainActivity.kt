package dev.wfhdiary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wfhdiary.app.backup.BackupWarning
import dev.wfhdiary.app.ui.backup.BackupSection
import dev.wfhdiary.app.ui.backup.BackupViewModel
import dev.wfhdiary.app.ui.month.MonthGridScreen
import dev.wfhdiary.app.ui.month.MonthGridViewModel
import dev.wfhdiary.app.ui.settings.SettingsScreen
import dev.wfhdiary.app.ui.settings.SettingsViewModel
import dev.wfhdiary.app.ui.theme.WfhDiaryTheme
import dev.wfhdiary.app.ui.update.AppUpdateViewModel
import dev.wfhdiary.app.ui.update.UpdateAvailableDialog
import dev.wfhdiary.app.update.GithubApkReleaseUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val monthViewModel: MonthGridViewModel by viewModels {
        MonthGridViewModel.factory(application)
    }
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.factory(application)
    }
    private val appUpdateViewModel: AppUpdateViewModel by viewModels {
        AppUpdateViewModel.factory(application)
    }
    private val backupViewModel: BackupViewModel by viewModels {
        BackupViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WfhDiaryTheme {
                MainAppContent(
                    settingsViewModel = settingsViewModel,
                    monthViewModel = monthViewModel,
                    appUpdateViewModel = appUpdateViewModel,
                    backupViewModel = backupViewModel,
                )
            }
        }
    }
}

@Composable
private fun MainAppContent(
    settingsViewModel: SettingsViewModel,
    monthViewModel: MonthGridViewModel,
    appUpdateViewModel: AppUpdateViewModel,
    backupViewModel: BackupViewModel,
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
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    monthViewModel.reload(showProgress = false)
                    backupViewModel.refresh()
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

    var lastHandledRestoreGeneration by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(backupState.restoreGeneration) {
        val generation = backupState.restoreGeneration
        if (generation > lastHandledRestoreGeneration) {
            monthViewModel.reload(showProgress = true)
            settingsViewModel.refreshSettings()
        }
        // Always sync (not just on increase) so a reset counter after process death can't hide later restores.
        lastHandledRestoreGeneration = generation
    }

    LaunchedEffect(Unit) {
        appUpdateViewModel.checkOnStartIfNeeded()
    }
    val pendingUpdate = githubUpdateUi.pendingRelease
    if (githubUpdateUi.showUpdateDialog && pendingUpdate != null) {
        UpdateAvailableDialog(
            pendingUpdate = pendingUpdate,
            busy = githubUpdateUi.busy,
            onDownload = { appUpdateViewModel.downloadAndInstall(activity) },
            onDismiss = appUpdateViewModel::dismissUpdateDialog,
        )
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
                    backupSection = {
                        BackupSection(
                            state = backupState,
                            onFolderChosen = backupViewModel::onFolderChosen,
                            onBackupNow = backupViewModel::backupNow,
                            onShare = { backupViewModel.share(activity) },
                            onRestoreFileChosen = backupViewModel::onRestoreFileChosen,
                            onConfirmRestore = backupViewModel::confirmRestore,
                            onCancelRestore = backupViewModel::cancelRestore,
                        )
                    },
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
                onRefresh = { monthViewModel.reload(showProgress = true) },
                backupWarning = when (backupState.warning) {
                    BackupWarning.NO_FOLDER -> stringResource(R.string.backup_warning_no_folder)
                    BackupWarning.FAILED -> stringResource(R.string.backup_warning_failed)
                    BackupWarning.STALE -> stringResource(R.string.backup_warning_stale)
                    null -> null
                },
                onBackupWarningClick = { scope.launch { drawerState.open() } },
            )
        }
    }
}
