package dev.wfhdiary.app.ui.time

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.wfhdiary.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val iso = DateTimeFormatter.ISO_LOCAL_DATE
    val display =
        value.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching {
                LocalDate.parse(raw, iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            }.getOrDefault(raw)
        } ?: stringResource(R.string.date_picker_not_set)
    val fieldColors =
        OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

    Box(modifier = modifier.clickable { showDialog = true }) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = label,
            trailingIcon = {
                Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.date_picker_open))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
        )
    }

    if (showDialog) {
        val zone = ZoneId.systemDefault()
        val initial =
            runCatching { LocalDate.parse(value, iso) }.getOrElse { LocalDate.now() }
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis = initial.atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                            onValueChange(date.format(iso))
                        }
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
        ) {
            DatePicker(state = state)
        }
    }
}
