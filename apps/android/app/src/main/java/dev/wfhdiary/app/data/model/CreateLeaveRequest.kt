package dev.wfhdiary.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateLeaveRequest(
    val startDate: String,
    val endDate: String,
    val note: String? = null,
)
