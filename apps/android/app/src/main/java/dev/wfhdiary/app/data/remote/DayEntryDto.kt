package dev.wfhdiary.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class DayEntryDto(
    val date: String,
    val dayName: String,
    val startTime: String? = null,
    val finishTime: String? = null,
    val breakHours: Double? = null,
    val totalHours: Double = 0.0,
    val description: String? = null,
    val location: String? = null,
)
