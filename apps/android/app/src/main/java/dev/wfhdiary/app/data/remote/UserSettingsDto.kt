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
    val defaultHomeStart: String,
    val defaultHomeFinish: String,
    val defaultHomeBreakHours: Double,
    val defaultOfficeStart: String,
    val defaultOfficeFinish: String,
    val defaultOfficeBreakHours: Double,
)
