package dev.wfhdiary.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class DiaryDto(
    val fyYear: Int,
    val fyStartDate: String,
    val fyEndDate: String,
)
