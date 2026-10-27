package dev.wfhdiary.app.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun BackupSection(
    state: BackupUiState,
    onFolderChosen: (Uri?) -> Unit,
    onBackupNow: () -> Unit,
    onShare: () -> Unit,
    onRestoreFileChosen: (Uri?) -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), onFolderChosen)
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onRestoreFileChosen)
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.backup_section_title), style = MaterialTheme.typography.titleSmall)
        Text(
            if (state.folderChosen) {
                stringResource(R.string.backup_folder_set, state.folderLabel ?: "…")
            } else {
                stringResource(R.string.backup_folder_none)
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            state.lastSuccessMillis?.let {
                stringResource(
                    R.string.backup_last_success,
                    formatter.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())),
                )
            } ?: stringResource(R.string.backup_last_never),
            style = MaterialTheme.typography.bodySmall,
        )
        state.lastError?.let {
            Text(
                stringResource(R.string.backup_last_error, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedButton(onClick = { folderLauncher.launch(null) }, enabled = !state.busy) {
            Text(stringResource(if (state.folderChosen) R.string.backup_folder_change else R.string.backup_folder_choose))
        }
        Button(onClick = onBackupNow, enabled = !state.busy && state.folderChosen) {
            Text(stringResource(R.string.backup_now))
        }
        OutlinedButton(onClick = onShare, enabled = !state.busy) {
            Text(stringResource(R.string.backup_share))
        }
        OutlinedButton(
            onClick = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*")) },
            enabled = !state.busy && state.folderChosen,
        ) {
            Text(stringResource(R.string.restore_from_file))
        }
        if (!state.folderChosen) {
            Text(stringResource(R.string.restore_needs_folder_hint), style = MaterialTheme.typography.bodySmall)
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
    }

    state.pendingRestore?.let { pending ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.restore_confirm_body,
                        pending.document.exportedAt,
                        pending.document.days.size,
                        pending.document.leavePeriods.size,
                        pending.currentDays,
                        pending.currentLeavePeriods,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmRestore) { Text(stringResource(R.string.restore_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelRestore) { Text(stringResource(R.string.time_picker_cancel)) }
            },
        )
    }
}
