package dev.wfhdiary.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class CreateLeaveRequest(
    val startDate: String,
    val endDate: String,
    val note: String? = null,
)
