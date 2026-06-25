package dev.wfhdiary.app.ui.settings

import dev.wfhdiary.app.util.WorkingDaysMask

data class SettingsFormState(
    val displayName: String = "",
    val notificationTime: String = "08:00",
    val notificationWorkingDaysMask: Int = WorkingDaysMask.DEFAULT,
    val missedNotificationDefault: String = "None",
    val missedNotificationApplyAt: String = "12:00",
    val defaultHomeStart: String = "08:00",
    val defaultHomeFinish: String = "17:00",
    val defaultHomeBreakHours: String = "1",
    val defaultOfficeStart: String = "08:00",
    val defaultOfficeFinish: String = "17:00",
    val defaultOfficeBreakHours: String = "1",
    val homeGeofenceLatitude: Double? = null,
    val homeGeofenceLongitude: Double? = null,
    val homeGeofenceRadiusMeters: Int? = null,
    val officeGeofenceLatitude: Double? = null,
    val officeGeofenceLongitude: Double? = null,
    val officeGeofenceRadiusMeters: Int? = null,
)
