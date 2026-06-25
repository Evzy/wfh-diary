package dev.wfhdiary.app.ui.settings

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.R
import dev.wfhdiary.app.notification.MissedNotificationDefault

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GeolocationSettingsSheet(
    draft: GeolocationSettingsDraft,
    locationGranted: Boolean,
    mapsConfigured: Boolean,
    geofenceBusy: Boolean,
    statusMessage: String,
    onDismiss: () -> Unit,
    onDraftChange: ((GeolocationSettingsDraft) -> GeolocationSettingsDraft) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onPickHomeOnMap: () -> Unit,
    onPickOfficeOnMap: () -> Unit,
    onSetHomeLocation: () -> Unit,
    onClearHomeLocation: () -> Unit,
    onSetOfficeLocation: () -> Unit,
    onClearOfficeLocation: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.geolocation_settings_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                stringResource(R.string.geolocation_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (!locationGranted) {
                Text(
                    stringResource(R.string.work_templates_location_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onRequestLocationPermission,
                    enabled = !geofenceBusy,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.work_templates_location_enable))
                }
            }

            Text(
                stringResource(R.string.settings_missed_notification_away_title),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.settings_missed_notification_away_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                MissedNotificationDefault.awayOptions.forEach { option ->
                    androidx.compose.material3.FilterChip(
                        selected = draft.missedNotificationAwayDefault.equals(option, ignoreCase = true),
                        onClick = { onDraftChange { it.copy(missedNotificationAwayDefault = option) } },
                        label = { Text(awayLabel(option)) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(
                stringResource(R.string.settings_geolocation_skip_notification_title),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.settings_geolocation_skip_notification_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.settings_geolocation_skip_at_home))
                Switch(
                    checked = draft.skipNotificationAtHome,
                    onCheckedChange = { checked ->
                        onDraftChange { it.copy(skipNotificationAtHome = checked) }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.settings_geolocation_skip_at_office))
                Switch(
                    checked = draft.skipNotificationAtOffice,
                    onCheckedChange = { checked ->
                        onDraftChange { it.copy(skipNotificationAtOffice = checked) }
                    },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

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
                latitude = if (draft.clearHomeGeofence) null else draft.homeGeofenceLatitude,
                longitude = if (draft.clearHomeGeofence) null else draft.homeGeofenceLongitude,
                radiusMeters = if (draft.clearHomeGeofence) null else draft.homeGeofenceRadiusMeters,
                locationGranted = locationGranted,
                mapsConfigured = mapsConfigured,
                geofenceBusy = geofenceBusy,
                onPickOnMap = onPickHomeOnMap,
                onSetCurrent = onSetHomeLocation,
                onClear = onClearHomeLocation,
            )
            LocationGeofenceRow(
                label = stringResource(R.string.location_geofence_office),
                latitude = if (draft.clearOfficeGeofence) null else draft.officeGeofenceLatitude,
                longitude = if (draft.clearOfficeGeofence) null else draft.officeGeofenceLongitude,
                radiusMeters = if (draft.clearOfficeGeofence) null else draft.officeGeofenceRadiusMeters,
                locationGranted = locationGranted,
                mapsConfigured = mapsConfigured,
                geofenceBusy = geofenceBusy,
                onPickOnMap = onPickOfficeOnMap,
                onSetCurrent = onSetOfficeLocation,
                onClear = onClearOfficeLocation,
            )

            if (statusMessage.isNotBlank()) {
                Text(
                    statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_close))
            }
        }
    }
}

@Composable
private fun awayLabel(option: String): String =
    when (option) {
        MissedNotificationDefault.HOME -> stringResource(R.string.notification_action_home)
        MissedNotificationDefault.OFFICE -> stringResource(R.string.notification_action_office)
        MissedNotificationDefault.LEAVE -> stringResource(R.string.notification_action_leave)
        else -> option
    }
