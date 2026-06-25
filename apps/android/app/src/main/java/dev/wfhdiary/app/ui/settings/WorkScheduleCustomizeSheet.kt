package dev.wfhdiary.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.wfhdiary.app.ui.BreakHoursField
import dev.wfhdiary.app.ui.time.TimePickerField

enum class WorkScheduleTarget { HOME, OFFICE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkScheduleCustomizeSheet(
    target: WorkScheduleTarget,
    form: SettingsFormState,
    onDismiss: () -> Unit,
    onSave: (SettingsFormState) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(target, form) { mutableStateOf(form) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                when (target) {
                    WorkScheduleTarget.HOME -> stringResource(R.string.settings_default_home)
                    WorkScheduleTarget.OFFICE -> stringResource(R.string.settings_default_office)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimePickerField(
                    value =
                        when (target) {
                            WorkScheduleTarget.HOME -> draft.defaultHomeStart
                            WorkScheduleTarget.OFFICE -> draft.defaultOfficeStart
                        },
                    onValueChange = { value ->
                        draft =
                            when (target) {
                                WorkScheduleTarget.HOME -> draft.copy(defaultHomeStart = value)
                                WorkScheduleTarget.OFFICE -> draft.copy(defaultOfficeStart = value)
                            }
                    },
                    label = { Text(stringResource(R.string.day_edit_start)) },
                    defaultHour = 8,
                    defaultMinute = if (target == WorkScheduleTarget.OFFICE) 30 else 0,
                    modifier = Modifier.weight(1f),
                )
                TimePickerField(
                    value =
                        when (target) {
                            WorkScheduleTarget.HOME -> draft.defaultHomeFinish
                            WorkScheduleTarget.OFFICE -> draft.defaultOfficeFinish
                        },
                    onValueChange = { value ->
                        draft =
                            when (target) {
                                WorkScheduleTarget.HOME -> draft.copy(defaultHomeFinish = value)
                                WorkScheduleTarget.OFFICE -> draft.copy(defaultOfficeFinish = value)
                            }
                    },
                    label = { Text(stringResource(R.string.day_edit_finish)) },
                    defaultHour = 17,
                    modifier = Modifier.weight(1f),
                )
            }
            BreakHoursField(
                value =
                    when (target) {
                        WorkScheduleTarget.HOME -> draft.defaultHomeBreakHours
                        WorkScheduleTarget.OFFICE -> draft.defaultOfficeBreakHours
                    },
                onValueChange = { value ->
                    draft =
                        when (target) {
                            WorkScheduleTarget.HOME -> draft.copy(defaultHomeBreakHours = value)
                            WorkScheduleTarget.OFFICE -> draft.copy(defaultOfficeBreakHours = value)
                        }
                },
                modifier = Modifier.padding(vertical = 8.dp),
            )
            OutlinedTextField(
                value =
                    when (target) {
                        WorkScheduleTarget.HOME -> draft.defaultHomeDescription
                        WorkScheduleTarget.OFFICE -> draft.defaultOfficeDescription
                    },
                onValueChange = { value ->
                    draft =
                        when (target) {
                            WorkScheduleTarget.HOME -> draft.copy(defaultHomeDescription = value)
                            WorkScheduleTarget.OFFICE -> draft.copy(defaultOfficeDescription = value)
                        }
                },
                label = { Text(stringResource(R.string.day_edit_description)) },
                placeholder = { Text(stringResource(R.string.work_schedule_description_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                minLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.time_picker_cancel))
                }
                Button(
                    onClick = { onSave(draft) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.work_schedule_customize_save))
                }
            }
        }
    }
}
