package dev.wfhdiary.app.data.model

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
