package dev.wfhdiary.app.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
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
import dev.wfhdiary.app.data.remote.DayEntryDto
import dev.wfhdiary.app.data.remote.UpsertDayEntryRequest
import dev.wfhdiary.app.ui.time.TimePickerField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditSheet(
    date: String,
    entry: DayEntryDto?,
    onDismiss: () -> Unit,
    onSave: (UpsertDayEntryRequest) -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.day_edit_break))
                IconButton(onClick = {
                    val v = breakHours.toDoubleOrNull() ?: 0.0
                    breakHours = (v - 0.25).coerceAtLeast(0.0).toString()
                }) { Text("−") }
                Text(breakHours.ifEmpty { "0" }, modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = {
                    val v = breakHours.toDoubleOrNull() ?: 0.0
                    breakHours = (v + 0.25).toString()
                }) { Text("+") }
            }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.day_edit_description)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onSave(
                        UpsertDayEntryRequest(
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
        }
    }
}
