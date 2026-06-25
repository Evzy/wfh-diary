package dev.wfhdiary.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.R
import dev.wfhdiary.app.util.WorkingDaysMask
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WorkingDaysSelector(
    mask: Int,
    onMaskChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val summary =
        WorkingDaysMask.selectedDayLabels(mask, TextStyle.FULL)
            .joinToString(", ")
            .ifBlank { stringResource(R.string.settings_notification_working_days_none) }

    Text(
        stringResource(R.string.settings_notification_working_days),
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        stringResource(R.string.settings_notification_working_days_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            summary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        OutlinedButton(onClick = { showDialog = true }) {
            Text(stringResource(R.string.settings_notification_working_days_set))
        }
    }

    if (showDialog) {
        WorkingDaysDialog(
            initialMask = mask,
            onDismiss = { showDialog = false },
            onSave = { updated ->
                onMaskChange(updated)
                showDialog = false
            },
        )
    }
}

@Composable
private fun WorkingDaysDialog(
    initialMask: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var draftMask by remember(initialMask) { mutableIntStateOf(initialMask) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_notification_working_days_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WorkingDaysMask.allDays().forEach { day ->
                    val label = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = WorkingDaysMask.isWorkingDay(draftMask, day),
                            onCheckedChange = { enabled ->
                                draftMask = WorkingDaysMask.setDay(draftMask, day, enabled)
                            },
                        )
                        Text(label, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draftMask) }) {
                Text(stringResource(R.string.settings_notification_working_days_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.time_picker_cancel))
            }
        },
    )
}
