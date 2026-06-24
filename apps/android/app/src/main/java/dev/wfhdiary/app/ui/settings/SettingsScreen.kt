package dev.wfhdiary.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.BuildConfig
import dev.wfhdiary.app.R
import dev.wfhdiary.app.data.prefs.ApiBackendMode

@Composable
private fun ApiBackendOptionRow(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SettingsScreen(
    form: SettingsFormState?,
    saveMessage: String,
    apiBackendMode: ApiBackendMode,
    resolvedBaseUrl: String,
    onApiBackendModeChange: (ApiBackendMode) -> Unit,
    onFormChange: ((SettingsFormState) -> SettingsFormState) -> Unit,
    onSave: () -> Unit,
    onExportClick: () -> Unit,
    firebaseUserLabel: String?,
    firebaseSignInBusy: Boolean,
    firebaseAuthMessage: String,
    onSignInWithGoogle: () -> Unit,
    onSignOutFirebase: () -> Unit,
    githubUpdateConfigured: Boolean,
    githubUpdateBusy: Boolean,
    githubUpdateStatus: String,
    githubUpdateHasPendingApk: Boolean,
    onCheckGithubUpdate: () -> Unit,
    onDownloadInstallGithubApk: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
            }
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.settings_close))
            }
        }
        Text(
            stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.firebase_signin_section_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.firebase_signin_section_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (BuildConfig.GOOGLE_SERVICES_CONFIGURED) {
            firebaseUserLabel?.let {
                Text(stringResource(R.string.firebase_user_signed_in_as, it))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSignInWithGoogle, enabled = !firebaseSignInBusy) {
                    Text(
                        if (firebaseSignInBusy) {
                            stringResource(R.string.firebase_signin_busy)
                        } else {
                            stringResource(R.string.firebase_signin_button)
                        },
                    )
                }
                TextButton(onClick = onSignOutFirebase) {
                    Text(stringResource(R.string.firebase_signout_button))
                }
            }
        } else {
            Text(
                stringResource(R.string.firebase_signin_need_json),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (firebaseAuthMessage.isNotBlank()) {
            Text(firebaseAuthMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.api_backend_section_title), style = MaterialTheme.typography.titleSmall)
        Text("Base URL: $resolvedBaseUrl", style = MaterialTheme.typography.bodySmall)
        ApiBackendOptionRow(
            selected = apiBackendMode == ApiBackendMode.API_LOCAL,
            title = stringResource(R.string.api_backend_local_title),
            description = stringResource(R.string.api_backend_local_desc),
            onClick = { onApiBackendModeChange(ApiBackendMode.API_LOCAL) },
        )
        ApiBackendOptionRow(
            selected = apiBackendMode == ApiBackendMode.API_AZURE,
            title = stringResource(R.string.api_backend_azure_title),
            description = stringResource(R.string.api_backend_azure_desc),
            onClick = { onApiBackendModeChange(ApiBackendMode.API_AZURE) },
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        form?.let { f ->
            OutlinedTextField(
                value = f.displayName,
                onValueChange = { v -> onFormChange { it.copy(displayName = v) } },
                label = { Text(stringResource(R.string.settings_display_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = f.notificationTime,
                onValueChange = { v -> onFormChange { it.copy(notificationTime = v) } },
                label = { Text(stringResource(R.string.settings_notification_time)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.settings_default_home), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = f.defaultHomeStart,
                    onValueChange = { v -> onFormChange { it.copy(defaultHomeStart = v) } },
                    label = { Text("Start") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f.defaultHomeFinish,
                    onValueChange = { v -> onFormChange { it.copy(defaultHomeFinish = v) } },
                    label = { Text("Finish") },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(stringResource(R.string.settings_default_office), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = f.defaultOfficeStart,
                    onValueChange = { v -> onFormChange { it.copy(defaultOfficeStart = v) } },
                    label = { Text("Start") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f.defaultOfficeFinish,
                    onValueChange = { v -> onFormChange { it.copy(defaultOfficeFinish = v) } },
                    label = { Text("Finish") },
                    modifier = Modifier.weight(1f),
                )
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_save))
            }
            if (saveMessage.isNotBlank()) {
                Text(saveMessage, style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(
            onClick = onExportClick,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Text(stringResource(R.string.settings_export_xlsx))
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.github_update_section_title), style = MaterialTheme.typography.titleSmall)
        if (!githubUpdateConfigured) {
            Text(stringResource(R.string.github_update_not_configured), style = MaterialTheme.typography.bodySmall)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCheckGithubUpdate, enabled = !githubUpdateBusy) {
                    Text(stringResource(R.string.github_update_check))
                }
                if (githubUpdateHasPendingApk) {
                    Button(onClick = onDownloadInstallGithubApk, enabled = !githubUpdateBusy) {
                        Text(stringResource(R.string.github_update_download_install))
                    }
                }
            }
            if (githubUpdateStatus.isNotBlank()) {
                Text(githubUpdateStatus, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}