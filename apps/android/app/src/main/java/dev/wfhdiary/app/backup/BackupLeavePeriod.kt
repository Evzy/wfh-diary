package dev.wfhdiary.app.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupLeavePeriod(
    val id: String,
    val startDate: String,
    val endDate: String,
    val note: String? = null,
)
