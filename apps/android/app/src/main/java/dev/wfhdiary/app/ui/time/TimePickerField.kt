package dev.wfhdiary.app.ui.time

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.wfhdiary.app.R
import dev.wfhdiary.app.util.TimeHelpers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    defaultHour: Int = 8,
    defaultMinute: Int = 0,
) {
    var showDialog by remember { mutableStateOf(false) }
    val display = value.trim().ifEmpty { stringResource(R.string.time_picker_not_set) }

    OutlinedTextField(
        value = display,
        onValueChange = {},
        readOnly = true,
        label = label,
        trailingIcon = {
            Icon(Icons.Default.Schedule, contentDescription = stringResource(R.string.time_picker_open))
        },
        modifier = modifier.clickable { showDialog = true },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = if (value.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
    )

    if (showDialog) {
        val (initialHour, initialMinute) = TimeHelpers.hourMinuteOrDefault(value, defaultHour, defaultMinute)
        val state = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = label,
            confirmButton = {
                TextButton(
                    onClick = {
                        onValueChange(TimeHelpers.formatHourMinute(state.hour, state.minute))
                        showDialog = false
                    },
                ) {
                    Text(stringResource(R.string.time_picker_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.time_picker_cancel))
                }
            },
            text = {
                TimePicker(state = state)
            },
        )
    }
}
