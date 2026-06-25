package dev.wfhdiary.app.ui.settings

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
import dev.wfhdiary.app.data.remote.CreateWorkTemplateRequest
import dev.wfhdiary.app.data.remote.WorkTemplateDto
import dev.wfhdiary.app.ui.BreakHoursField
import dev.wfhdiary.app.ui.time.TimePickerField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkTemplatesSheet(
    templates: List<WorkTemplateDto>,
    onDismiss: () -> Unit,
    onCreate: (CreateWorkTemplateRequest) -> Unit,
    onDelete: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val labelRequiredMessage = stringResource(R.string.work_templates_label_required)
    var label by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("08:30") }
    var finishTime by remember { mutableStateOf("17:00") }
    var breakHours by remember { mutableStateOf("1") }
    var location by remember { mutableStateOf("Office") }
    var description by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("200") }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.work_templates_title))
            Text(
                stringResource(R.string.work_templates_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (templates.isEmpty()) {
                Text(
                    stringResource(R.string.work_templates_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                templates.forEach { template ->
                    WorkTemplateRow(template = template, onDelete = { onDelete(template.id) })
                }
            }
            HorizontalDivider()
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.work_templates_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimePickerField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text(stringResource(R.string.day_edit_start)) },
                    defaultHour = 8,
                    defaultMinute = 30,
                    modifier = Modifier.weight(1f),
                )
                TimePickerField(
                    value = finishTime,
                    onValueChange = { finishTime = it },
                    label = { Text(stringResource(R.string.day_edit_finish)) },
                    defaultHour = 17,
                    modifier = Modifier.weight(1f),
                )
            }
            BreakHoursField(value = breakHours, onValueChange = { breakHours = it })
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text(stringResource(R.string.day_edit_location)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.day_edit_description)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.work_templates_geofence_title), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = latitude,
                onValueChange = { latitude = it },
                label = { Text(stringResource(R.string.work_templates_latitude)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = longitude,
                onValueChange = { longitude = it },
                label = { Text(stringResource(R.string.work_templates_longitude)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = radius,
                onValueChange = { radius = it },
                label = { Text(stringResource(R.string.work_templates_radius_m)) },
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    error = null
                    if (label.isBlank()) {
                        error = labelRequiredMessage
                        return@Button
                    }
                    onCreate(
                        CreateWorkTemplateRequest(
                            label = label.trim(),
                            startTime = startTime.ifBlank { null },
                            finishTime = finishTime.ifBlank { null },
                            breakHours = breakHours.toDoubleOrNull(),
                            location = location.ifBlank { null },
                            description = description.ifBlank { null },
                            geofenceLatitude = latitude.toDoubleOrNull(),
                            geofenceLongitude = longitude.toDoubleOrNull(),
                            geofenceRadiusMeters = radius.toIntOrNull(),
                        ),
                    )
                    label = ""
                    description = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.work_templates_add))
            }
        }
    }
}

@Composable
private fun WorkTemplateRow(
    template: WorkTemplateDto,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(template.label, style = MaterialTheme.typography.bodyMedium)
            val geo =
                if (template.geofenceLatitude != null && template.geofenceLongitude != null) {
                    "${template.geofenceLatitude}, ${template.geofenceLongitude} (${template.geofenceRadiusMeters ?: 0}m)"
                } else {
                    null
                }
            geo?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.work_templates_delete))
        }
    }
}
