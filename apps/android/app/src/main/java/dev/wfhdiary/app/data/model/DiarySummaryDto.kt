package dev.wfhdiary.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DiarySummaryDto(
    val totalHours: Double = 0.0,
    val homeHours: Double = 0.0,
    val officeHours: Double = 0.0,
    val claimableWfhAmount: Double = 0.0,
    val daysAtHome: Int = 0,
    val daysInOffice: Int = 0,
    val logicCheckPassed: Boolean = true,
)
