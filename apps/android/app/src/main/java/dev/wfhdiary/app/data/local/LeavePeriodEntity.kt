package dev.wfhdiary.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "leave_periods")
data class LeavePeriodEntity(
    @PrimaryKey val id: String,
    val startDate: String,
    val endDate: String,
    val note: String?,
    val createdAtMillis: Long,
)
