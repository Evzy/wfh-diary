package dev.wfhdiary.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.R

@Composable
fun LocationGeofenceSection(
    form: SettingsFormState,
    locationGranted: Boolean,
    mapsConfigured: Boolean,
    geofenceBusy: Boolean,
    onRequestLocationPermission: () -> Unit,
    onPickHomeOnMap: () -> Unit,
    onPickOfficeOnMap: () -> Unit,
    onSetHomeLocation: () -> Unit,
    onClearHomeLocation: () -> Unit,
    onSetOfficeLocation: () -> Unit,
    onClearOfficeLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(stringResource(R.string.location_geofence_section_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.location_geofence_section_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (!locationGranted) {
            Text(
                stringResource(R.string.work_templates_location_permission),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Button(
                onClick = onRequestLocationPermission,
                enabled = !geofenceBusy,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Text(stringResource(R.string.work_templates_location_enable))
            }
        }
        if (!mapsConfigured) {
            Text(
                stringResource(R.string.location_geofence_maps_not_configured),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        LocationGeofenceRow(
            label = stringResource(R.string.location_geofence_home),
            latitude = form.homeGeofenceLatitude,
            longitude = form.homeGeofenceLongitude,
            radiusMeters = form.homeGeofenceRadiusMeters,
            locationGranted = locationGranted,
            mapsConfigured = mapsConfigured,
            geofenceBusy = geofenceBusy,
            onPickOnMap = onPickHomeOnMap,
            onSetCurrent = onSetHomeLocation,
            onClear = onClearHomeLocation,
        )
        LocationGeofenceRow(
            label = stringResource(R.string.location_geofence_office),
            latitude = form.officeGeofenceLatitude,
            longitude = form.officeGeofenceLongitude,
            radiusMeters = form.officeGeofenceRadiusMeters,
            locationGranted = locationGranted,
            mapsConfigured = mapsConfigured,
            geofenceBusy = geofenceBusy,
            onPickOnMap = onPickOfficeOnMap,
            onSetCurrent = onSetOfficeLocation,
            onClear = onClearOfficeLocation,
        )
    }
}

@Composable
private fun LocationGeofenceRow(
    label: String,
    latitude: Double?,
    longitude: Double?,
    radiusMeters: Int?,
    locationGranted: Boolean,
    mapsConfigured: Boolean,
    geofenceBusy: Boolean,
    onPickOnMap: () -> Unit,
    onSetCurrent: () -> Unit,
    onClear: () -> Unit,
) {
    val configured = latitude != null && longitude != null && radiusMeters != null
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (configured) {
                stringResource(
                    R.string.location_geofence_configured,
                    latitude!!,
                    longitude!!,
                    radiusMeters!!,
                )
            } else {
                stringResource(R.string.location_geofence_not_set)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPickOnMap,
                enabled = locationGranted && mapsConfigured && !geofenceBusy,
            ) {
                Text(stringResource(R.string.location_geofence_pick_on_map))
            }
            TextButton(
                onClick = onSetCurrent,
                enabled = locationGranted && !geofenceBusy,
            ) {
                Text(stringResource(R.string.location_geofence_use_current))
            }
            if (configured) {
                TextButton(onClick = onClear, enabled = !geofenceBusy) {
                    Text(stringResource(R.string.location_geofence_clear))
                }
            }
        }
    }
}
