package dev.wfhdiary.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class UserSettingsDto(
    val displayName: String,
    val notificationTime: String,
    val defaultHomeStart: String,
    val defaultHomeFinish: String,
    val defaultHomeBreakHours: Double,
    val defaultOfficeStart: String,
    val defaultOfficeFinish: String,
    val defaultOfficeBreakHours: Double,
)
