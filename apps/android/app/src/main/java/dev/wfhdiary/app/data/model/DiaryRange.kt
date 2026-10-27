package dev.wfhdiary.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DiaryRange(
    val fyYear: Int,
    val fyStartDate: String,
    val fyEndDate: String,
)
