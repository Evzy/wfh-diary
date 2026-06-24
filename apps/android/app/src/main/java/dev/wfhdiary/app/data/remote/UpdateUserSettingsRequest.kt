package dev.wfhdiary.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class UpdateUserSettingsRequest(
    val displayName: String? = null,
    val notificationTime: String? = null,
    val notificationWorkingDaysMask: Int? = null,
    val missedNotificationDefault: String? = null,
    val missedNotificationApplyAt: String? = null,
    val defaultHomeStart: String? = null,
    val defaultHomeFinish: String? = null,
    val defaultHomeBreakHours: Double? = null,
    val defaultOfficeStart: String? = null,
    val defaultOfficeFinish: String? = null,
    val defaultOfficeBreakHours: Double? = null,
)
