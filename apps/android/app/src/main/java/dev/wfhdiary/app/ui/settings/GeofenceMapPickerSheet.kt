package dev.wfhdiary.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import dev.wfhdiary.app.R
import dev.wfhdiary.app.util.GeofenceHelper
import dev.wfhdiary.app.util.LocationHelper
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceMapPickerSheet(
    title: String,
    initialLatitude: Double?,
    initialLongitude: Double?,
    initialRadiusMeters: Int?,
    mapsConfigured: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (latitude: Double, longitude: Double, radiusMeters: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        if (!mapsConfigured) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.location_geofence_maps_not_configured),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.time_picker_cancel))
                }
            }
            return@ModalBottomSheet
        }

        var resolvingLocation by remember { mutableStateOf(true) }
        var mapLoaded by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var radiusMeters by remember {
            mutableIntStateOf(initialRadiusMeters ?: GeofenceHelper.DEFAULT_RADIUS_METERS)
        }
        val fallbackLatLng = remember {
            if (initialLatitude != null && initialLongitude != null) {
                LatLng(initialLatitude, initialLongitude)
            } else {
                LatLng(-33.8688, 151.2093)
            }
        }
        var center by remember { mutableStateOf<LatLng?>(null) }
        var pendingCenter by remember { mutableStateOf<LatLng?>(null) }

        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(fallbackLatLng, 16f)
        }

        LaunchedEffect(initialLatitude, initialLongitude) {
            resolvingLocation = true
            error = null
            runCatching {
                if (!LocationHelper.hasLocationPermission(context)) {
                    error = context.getString(R.string.work_templates_location_permission)
                    return@runCatching
                }
                val start =
                    when {
                        initialLatitude != null && initialLongitude != null ->
                            LatLng(initialLatitude, initialLongitude)
                        else ->
                            LocationHelper.getCurrentLocation(context)?.let { LatLng(it.latitude, it.longitude) }
                    }
                        ?: error(context.getString(R.string.location_geofence_unavailable))
                pendingCenter = start
            }.onFailure { e ->
                error = e.message.orEmpty()
            }
            resolvingLocation = false
        }

        LaunchedEffect(mapLoaded, pendingCenter) {
            val target = pendingCenter ?: return@LaunchedEffect
            if (!mapLoaded) return@LaunchedEffect
            center = target
            cameraPositionState.position = CameraPosition.fromLatLngZoom(target, 16f)
        }

        LaunchedEffect(cameraPositionState) {
            snapshotFlow { cameraPositionState.position.target }
                .distinctUntilChanged { old, new ->
                    old.latitude == new.latitude && old.longitude == new.longitude
                }
                .collect { target ->
                    if (!resolvingLocation) {
                        center = target
                    }
                }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.location_geofence_map_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                } else {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(isMyLocationEnabled = LocationHelper.hasLocationPermission(context)),
                        uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = true),
                        onMapLoaded = { mapLoaded = true },
                    ) {
                        center?.let { mapCenter ->
                            Circle(
                                center = mapCenter,
                                radius = radiusMeters.toDouble(),
                                fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                strokeColor = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2f,
                            )
                        }
                    }
                    if (resolvingLocation) {
                        CircularProgressIndicator()
                    }
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            if (error == null) {
                Text(
                    stringResource(R.string.location_geofence_radius_label, radiusMeters),
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = radiusMeters.toFloat(),
                    onValueChange = { radiusMeters = it.toInt() },
                    valueRange = GeofenceHelper.MIN_RADIUS_METERS.toFloat()..GeofenceHelper.MAX_RADIUS_METERS.toFloat(),
                    steps = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                center?.let { mapCenter ->
                    Text(
                        stringResource(
                            R.string.location_geofence_map_coords,
                            mapCenter.latitude,
                            mapCenter.longitude,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.time_picker_cancel))
                }
                Button(
                    onClick = {
                        val mapCenter = center ?: return@Button
                        onConfirm(mapCenter.latitude, mapCenter.longitude, radiusMeters)
                    },
                    enabled = !resolvingLocation && error == null && center != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.location_geofence_map_save))
                }
            }
        }
    }
}
