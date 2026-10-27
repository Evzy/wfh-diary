package dev.wfhdiary.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DayEntryUpdate(
    val startTime: String? = null,
    val finishTime: String? = null,
    val breakHours: Double? = null,
    val description: String? = null,
    val location: String? = null,
)
