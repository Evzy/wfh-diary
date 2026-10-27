package dev.wfhdiary.app.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.R
import dev.wfhdiary.app.data.model.DayEntry
import dev.wfhdiary.app.data.model.DayEntryUpdate
import dev.wfhdiary.app.data.model.LeavePeriod
import dev.wfhdiary.app.ui.BreakHoursField
import dev.wfhdiary.app.ui.time.TimePickerField

private fun DayEntry?.hasLoggedWork(): Boolean {
    if (this == null) return false
    if (description.equals("LEAVE", ignoreCase = true)) return false
    return !location.isNullOrBlank() ||
        !startTime.isNullOrBlank() ||
        !finishTime.isNullOrBlank() ||
        (breakHours != null && breakHours > 0.0) ||
        !description.isNullOrBlank()
}

private fun leaveRangeLabel(period: LeavePeriod): String =
    if (period.startDate == period.endDate) {
        period.startDate
    } else {
        "${period.startDate} – ${period.endDate}"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditSheet(
    date: String,
    entry: DayEntry?,
    leavePeriod: LeavePeriod? = null,
    onDismiss: () -> Unit,
    onSave: (DayEntryUpdate) -> Unit,
    onDelete: (() -> Unit)? = null,
    onRemoveLeave: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var location by remember(date, entry) { mutableStateOf(entry?.location ?: "") }
    var startTime by remember(date, entry) { mutableStateOf(entry?.startTime.orEmpty()) }
    var finishTime by remember(date, entry) { mutableStateOf(entry?.finishTime.orEmpty()) }
    var breakHours by remember(date, entry) {
        mutableStateOf(entry?.breakHours?.toString().orEmpty())
    }
    var description by remember(date, entry) { mutableStateOf(entry?.description.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.day_edit_title) + " — $date")
            if (leavePeriod != null) {
                Text(
                    stringResource(R.string.day_edit_on_leave, leaveRangeLabel(leavePeriod)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onRemoveLeave != null) {
                    OutlinedButton(
                        onClick = onRemoveLeave,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.day_edit_remove_leave))
                    }
                }
            }
            Text(stringResource(R.string.day_edit_location))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Home", "Office").forEach { loc ->
                    FilterChip(
                        selected = location.equals(loc, ignoreCase = true),
                        onClick = { location = loc },
                        label = { Text(loc) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimePickerField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text(stringResource(R.string.day_edit_start)) },
                    defaultHour = 8,
                    defaultMinute = 0,
                    modifier = Modifier.weight(1f),
                )
                TimePickerField(
                    value = finishTime,
                    onValueChange = { finishTime = it },
                    label = { Text(stringResource(R.string.day_edit_finish)) },
                    defaultHour = 17,
                    defaultMinute = 0,
                    modifier = Modifier.weight(1f),
                )
            }
            BreakHoursField(
                value = breakHours,
                onValueChange = { breakHours = it },
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.day_edit_description)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onSave(
                        DayEntryUpdate(
                            startTime = startTime.ifBlank { null },
                            finishTime = finishTime.ifBlank { null },
                            breakHours = breakHours.toDoubleOrNull(),
                            description = description.ifBlank { null },
                            location = location.ifBlank { null },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.day_edit_save))
            }
            if (entry.hasLoggedWork() && onDelete != null) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.day_edit_delete))
                }
            }
        }
    }
}
