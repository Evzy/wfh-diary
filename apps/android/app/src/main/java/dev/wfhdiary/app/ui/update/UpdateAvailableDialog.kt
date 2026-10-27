package dev.wfhdiary.app.ui.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.wfhdiary.app.R
import dev.wfhdiary.app.update.GithubApkReleaseInfo

@Composable
fun UpdateAvailableDialog(
    pendingUpdate: GithubApkReleaseInfo,
    busy: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_dialog_title)) },
        text = {
            Text(
                stringResource(
                    R.string.update_dialog_message,
                    pendingUpdate.tagName,
                    pendingUpdate.releaseTitle?.takeIf { it.isNotBlank() } ?: pendingUpdate.assetName,
                ),
            )
        },
        confirmButton = {
            Button(onClick = onDownload, enabled = !busy) {
                Text(stringResource(R.string.github_update_download_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_dialog_later))
            }
        },
    )
}
