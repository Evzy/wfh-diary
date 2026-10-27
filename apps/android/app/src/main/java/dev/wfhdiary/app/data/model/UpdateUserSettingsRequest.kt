package dev.wfhdiary.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdateUserSettingsRequest(
    val displayName: String? = null,
    val notificationTime: String? = null,
    val notificationWorkingDaysMask: Int? = null,
    val missedNotificationDefault: String? = null,
    val missedNotificationApplyAt: String? = null,
    val missedNotificationAwayDefault: String? = null,
    val geolocationSkipNotificationAtHome: Boolean? = null,
    val geolocationSkipNotificationAtOffice: Boolean? = null,
    val defaultHomeStart: String? = null,
    val defaultHomeFinish: String? = null,
    val defaultHomeBreakHours: Double? = null,
    val defaultHomeDescription: String? = null,
    val defaultOfficeStart: String? = null,
    val defaultOfficeFinish: String? = null,
    val defaultOfficeBreakHours: Double? = null,
    val defaultOfficeDescription: String? = null,
    val homeGeofenceLatitude: Double? = null,
    val homeGeofenceLongitude: Double? = null,
    val homeGeofenceRadiusMeters: Int? = null,
    val officeGeofenceLatitude: Double? = null,
    val officeGeofenceLongitude: Double? = null,
    val officeGeofenceRadiusMeters: Int? = null,
    val clearHomeGeofence: Boolean? = null,
    val clearOfficeGeofence: Boolean? = null,
)
