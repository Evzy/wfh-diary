package dev.wfhdiary.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "day_entries")
data class DayEntryEntity(
    @PrimaryKey val date: String,
    val fyYear: Int,
    val dayName: String,
    val startTime: String?,
    val finishTime: String?,
    val breakHours: Double?,
    val totalHours: Double,
    val description: String?,
    val location: String?,
    val cachedAtMillis: Long,
)
