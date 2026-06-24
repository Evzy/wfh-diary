package dev.wfhdiary.app

import android.content.Intent
import android.net.Uri
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
                val apiBackendMode by settingsViewModel.apiBackendMode.collectAsStateWithLifecycle()
                val resolvedBaseUrl by settingsViewModel.resolvedBaseUrl.collectAsStateWithLifecycle()
                val firebaseUserLabel by settingsViewModel.firebaseUserLabel.collectAsStateWithLifecycle()
                val firebaseSignInBusy by settingsViewModel.firebaseSignInBusy.collectAsStateWithLifecycle()
                val firebaseAuthMessage by settingsViewModel.firebaseAuthMessage.collectAsStateWithLifecycle()
                val githubUpdateUi by settingsViewModel.githubUpdateUiState.collectAsStateWithLifecycle()
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val activity = LocalContext.current as ComponentActivity

                LaunchedEffect(drawerState.currentValue) {
                    if (drawerState.currentValue == DrawerValue.Open) {
                        settingsViewModel.refreshSettings()
                        settingsViewModel.refreshBaseUrlMeta()
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
                                apiBackendMode = apiBackendMode,
                                resolvedBaseUrl = resolvedBaseUrl,
                                onApiBackendModeChange = settingsViewModel::setApiBackendMode,
                                onFormChange = settingsViewModel::updateForm,
                                onSave = settingsViewModel::saveSettings,
                                onExportClick = {
                                    scope.launch {
                                        runCatching {
                                            settingsViewModel.exportUrlAsync()
                                        }.onSuccess { url ->
                                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    }
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
                            onOpenSettings = { scope.launch { drawerState.open() } },
                            onSelectDate = monthViewModel::selectDate,
                            onSaveDay = monthViewModel::saveDay,
                        )
                    }
                }
            }
        }
    }
}
