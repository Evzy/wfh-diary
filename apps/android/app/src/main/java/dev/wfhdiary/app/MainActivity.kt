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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wfhdiary.app.ui.month.MonthGridScreen
import dev.wfhdiary.app.ui.month.MonthGridViewModel
import dev.wfhdiary.app.ui.settings.SettingsScreen
import dev.wfhdiary.app.ui.settings.SettingsViewModel
import dev.wfhdiary.app.ui.theme.WfhDiaryTheme
import dev.wfhdiary.app.update.GithubApkReleaseUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val monthViewModel: MonthGridViewModel by viewModels {
        MonthGridViewModel.factory(application)
    }
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WfhDiaryTheme {
                val monthState by monthViewModel.uiState.collectAsStateWithLifecycle()
                val settingsForm by settingsViewModel.settingsForm.collectAsStateWithLifecycle()
                val saveMessage by settingsViewModel.saveMessage.collectAsStateWithLifecycle()
                val notificationMessage by settingsViewModel.notificationMessage.collectAsStateWithLifecycle()
                val exportBusy by settingsViewModel.exportBusy.collectAsStateWithLifecycle()
                val exportStatus by settingsViewModel.exportStatus.collectAsStateWithLifecycle()
                val firebaseUserLabel by settingsViewModel.firebaseUserLabel.collectAsStateWithLifecycle()
                val firebaseSignInBusy by settingsViewModel.firebaseSignInBusy.collectAsStateWithLifecycle()
                val firebaseAuthMessage by settingsViewModel.firebaseAuthMessage.collectAsStateWithLifecycle()
                val githubUpdateUi by settingsViewModel.githubUpdateUiState.collectAsStateWithLifecycle()
                val geofenceBusy by settingsViewModel.geofenceBusy.collectAsStateWithLifecycle()
                val geofenceMessage by settingsViewModel.geofenceMessage.collectAsStateWithLifecycle()
                val geofenceMapPicker by settingsViewModel.geofenceMapPicker.collectAsStateWithLifecycle()
                val workScheduleCustomize by settingsViewModel.workScheduleCustomize.collectAsStateWithLifecycle()
                val geolocationSettingsOpen by settingsViewModel.geolocationSettingsOpen.collectAsStateWithLifecycle()
                val geolocationDraft by settingsViewModel.geolocationDraft.collectAsStateWithLifecycle()
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val activity = LocalContext.current as ComponentActivity

                LaunchedEffect(drawerState.currentValue) {
                    if (drawerState.currentValue == DrawerValue.Open) {
                        settingsViewModel.refreshSettings()
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
                                exportFyYear = monthState.fyYear,
                                exportBusy = exportBusy,
                                exportStatus = exportStatus,
                                onFormChange = settingsViewModel::updateForm,
                                onSave = settingsViewModel::saveSettings,
                                onSendTestNotification = settingsViewModel::sendTestNotification,
                                onClearNotificationMessage = settingsViewModel::clearNotificationMessage,
                                onExportClick = {
                                    settingsViewModel.exportXlsx(monthState.fyYear, activity)
                                },
                                firebaseUserLabel = firebaseUserLabel,
                                firebaseSignInBusy = firebaseSignInBusy,
                                firebaseAuthMessage = firebaseAuthMessage,
                                onSignInWithGoogle = { settingsViewModel.signInWithGoogle(activity) },
                                onSignOutFirebase = settingsViewModel::signOutFirebase,
                                githubUpdateConfigured = GithubApkReleaseUpdater.isConfigured(),
                                githubUpdateBusy = githubUpdateUi.busy,
                                githubUpdateStatus = githubUpdateUi.status,
                                githubUpdateHasPendingApk = githubUpdateUi.pendingRelease != null,
                                onCheckGithubUpdate = settingsViewModel::checkGithubRelease,
                                onDownloadInstallGithubApk = {
                                    settingsViewModel.downloadAndInstallGithubApk(activity)
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
                                onApplyGeolocationSettings = settingsViewModel::applyGeolocationSettings,
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
                                onClose = { scope.launch { drawerState.close() } },
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
                        )
                    }
                }
            }
        }
    }
}
