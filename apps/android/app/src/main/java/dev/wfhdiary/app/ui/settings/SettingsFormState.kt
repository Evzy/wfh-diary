package dev.wfhdiary.app.ui.settings

import dev.wfhdiary.app.util.WorkingDaysMask

data class SettingsFormState(
    val displayName: String = "",
    val notificationTime: String = "08:00",
    val notificationWorkingDaysMask: Int = WorkingDaysMask.DEFAULT,
    val missedNotificationDefault: String = "None",
    val missedNotificationApplyAt: String = "12:00",
    val missedNotificationAwayDefault: String = "Office",
    val geolocationSkipNotificationAtHome: Boolean = false,
    val geolocationSkipNotificationAtOffice: Boolean = false,
    val defaultHomeStart: String = "08:00",
    val defaultHomeFinish: String = "17:00",
    val defaultHomeBreakHours: String = "1",
    val defaultHomeDescription: String = "",
    val defaultOfficeStart: String = "08:30",
    val defaultOfficeFinish: String = "17:00",
    val defaultOfficeBreakHours: String = "1",
    val defaultOfficeDescription: String = "",
    val homeGeofenceLatitude: Double? = null,
    val homeGeofenceLongitude: Double? = null,
    val homeGeofenceRadiusMeters: Int? = null,
    val officeGeofenceLatitude: Double? = null,
    val officeGeofenceLongitude: Double? = null,
    val officeGeofenceRadiusMeters: Int? = null,
)
