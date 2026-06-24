package dev.wfhdiary.app.ui.leave

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.R
import dev.wfhdiary.app.data.remote.CreateLeaveRequest
import dev.wfhdiary.app.ui.time.DatePickerField
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveScheduleSheet(
    onDismiss: () -> Unit,
    onSave: (CreateLeaveRequest) -> Unit,
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.leave_schedule_title))
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
                        CreateLeaveRequest(
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
