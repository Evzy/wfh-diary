package dev.wfhdiary.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class CreateWorkTemplateRequest(
    val label: String,
    val sortOrder: Int? = null,
    val startTime: String? = null,
    val finishTime: String? = null,
    val breakHours: Double? = null,
    val location: String? = null,
    val description: String? = null,
    val isDefault: Boolean = false,
    val geofenceLatitude: Double? = null,
    val geofenceLongitude: Double? = null,
    val geofenceRadiusMeters: Int? = null,
)
