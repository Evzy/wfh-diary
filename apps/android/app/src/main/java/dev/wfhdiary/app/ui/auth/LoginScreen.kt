package dev.wfhdiary.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.BuildConfig
import dev.wfhdiary.app.R
import dev.wfhdiary.app.update.GithubApkReleaseInfo

@Composable
fun LoginScreen(
    signInBusy: Boolean,
    errorMessage: String,
    onSignInWithGoogle: () -> Unit,
    updateConfigured: Boolean,
    updateBusy: Boolean,
    updateStatus: String,
    pendingUpdate: GithubApkReleaseInfo?,
    showUpdateDialog: Boolean,
    onAutoCheckForUpdate: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onDismissUpdateDialog: () -> Unit,
    onDownloadUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(updateConfigured) {
        if (updateConfigured) {
            onAutoCheckForUpdate()
        }
    }

    if (showUpdateDialog && pendingUpdate != null) {
        AlertDialog(
            onDismissRequest = onDismissUpdateDialog,
            title = { Text(stringResource(R.string.login_update_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.login_update_dialog_message,
                        pendingUpdate.tagName,
                        pendingUpdate.releaseTitle?.takeIf { it.isNotBlank() } ?: pendingUpdate.assetName,
                    ),
                )
            },
            confirmButton = {
                Button(onClick = onDownloadUpdate, enabled = !updateBusy) {
                    Text(stringResource(R.string.github_update_download_install))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUpdateDialog) {
                    Text(stringResource(R.string.login_update_dialog_later))
                }
            },
        )
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF0D47A1),
                    modifier = Modifier.size(96.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_app_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(40.dp))
                Button(
                    onClick = onSignInWithGoogle,
                    enabled = !signInBusy && !updateBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (signInBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.login_busy))
                    } else {
                        Text(stringResource(R.string.login_button))
                    }
                }
                if (errorMessage.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(32.dp))
                if (updateConfigured) {
                    if (pendingUpdate != null && !showUpdateDialog) {
                        TextButton(onClick = onDownloadUpdate, enabled = !updateBusy) {
                            Text(stringResource(R.string.login_update_download_available, pendingUpdate.tagName))
                        }
                    }
                    TextButton(onClick = onCheckForUpdate, enabled = !updateBusy) {
                        if (updateBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(stringResource(R.string.github_update_check))
                    }
                    if (updateStatus.isNotBlank()) {
                        Text(
                            text = updateStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(
                        R.string.settings_app_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
