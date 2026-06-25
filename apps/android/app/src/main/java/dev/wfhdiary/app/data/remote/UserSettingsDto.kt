package dev.wfhdiary.app.data.remote

import dev.wfhdiary.app.util.WorkingDaysMask
import kotlinx.serialization.Serializable

@Serializable
data class UserSettingsDto(
    val displayName: String,
    val notificationTime: String,
    val notificationWorkingDaysMask: Int = WorkingDaysMask.DEFAULT,
    val missedNotificationDefault: String = "None",
    val missedNotificationApplyAt: String = "12:00",
    val missedNotificationAwayDefault: String = "Office",
    val geolocationSkipNotificationAtHome: Boolean = false,
    val geolocationSkipNotificationAtOffice: Boolean = false,
    val defaultHomeStart: String,
    val defaultHomeFinish: String,
    val defaultHomeBreakHours: Double,
    val defaultHomeDescription: String? = null,
    val defaultOfficeStart: String,
    val defaultOfficeFinish: String,
    val defaultOfficeBreakHours: Double,
    val defaultOfficeDescription: String? = null,
    val homeGeofenceLatitude: Double? = null,
    val homeGeofenceLongitude: Double? = null,
    val homeGeofenceRadiusMeters: Int? = null,
    val officeGeofenceLatitude: Double? = null,
    val officeGeofenceLongitude: Double? = null,
    val officeGeofenceRadiusMeters: Int? = null,
)
