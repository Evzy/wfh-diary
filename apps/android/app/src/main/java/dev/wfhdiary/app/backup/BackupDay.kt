package dev.wfhdiary.app.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupDay(
    val date: String,
    val startTime: String? = null,
    val finishTime: String? = null,
    val breakHours: Double? = null,
    val description: String? = null,
    val location: String? = null,
)
