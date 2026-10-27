package dev.wfhdiary.app.ui.leave

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.R
import dev.wfhdiary.app.data.model.LeavePeriod
import dev.wfhdiary.app.data.model.NewLeave
import dev.wfhdiary.app.ui.time.DatePickerField
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveScheduleSheet(
    leavePeriods: List<LeavePeriod>,
    onDismiss: () -> Unit,
    onSave: (NewLeave) -> Unit,
    onDelete: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val iso = DateTimeFormatter.ISO_LOCAL_DATE
    val today = remember { LocalDate.now().format(iso) }
    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf(today) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.leave_schedule_title))

            Text(stringResource(R.string.leave_existing_title), style = MaterialTheme.typography.titleSmall)
            if (leavePeriods.isEmpty()) {
                Text(
                    stringResource(R.string.leave_empty_list),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                leavePeriods.sortedByDescending { it.startDate }.forEach { period ->
                    LeavePeriodRow(period = period, onDelete = { onDelete(period.id) })
                }
            }

            HorizontalDivider()

            DatePickerField(
                value = startDate,
                onValueChange = { picked ->
                    startDate = picked
                    if (endDate < picked) endDate = picked
                },
                label = { Text(stringResource(R.string.leave_start_date)) },
            )
            DatePickerField(
                value = endDate,
                onValueChange = { picked ->
                    endDate = picked
                    if (picked < startDate) startDate = picked
                },
                label = { Text(stringResource(R.string.leave_end_date)) },
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.leave_note)) },
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { key ->
                val message =
                    when (key) {
                        "dates_required" -> stringResource(R.string.leave_error_dates_required)
                        "end_before_start" -> stringResource(R.string.leave_error_end_before_start)
                        else -> key
                    }
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    error = null
                    if (startDate.isBlank() || endDate.isBlank()) {
                        error = "dates_required"
                        return@Button
                    }
                    if (endDate < startDate) {
                        error = "end_before_start"
                        return@Button
                    }
                    onSave(
                        NewLeave(
                            startDate = startDate,
                            endDate = endDate,
                            note = note.takeIf { it.isNotBlank() },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.leave_save))
            }
        }
    }
}

@Composable
private fun LeavePeriodRow(
    period: LeavePeriod,
    onDelete: () -> Unit,
) {
    val range =
        if (period.startDate == period.endDate) {
            period.startDate
        } else {
            "${period.startDate} – ${period.endDate}"
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(range, style = MaterialTheme.typography.bodyMedium)
            period.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.leave_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
