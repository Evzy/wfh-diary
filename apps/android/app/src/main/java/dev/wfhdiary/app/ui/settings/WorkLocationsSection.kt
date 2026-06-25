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
fun LocationGeofenceRow(
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
