package dev.wfhdiary.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class WorkTemplateDto(
    val id: String,
    val label: String,
    val sortOrder: Int = 0,
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
