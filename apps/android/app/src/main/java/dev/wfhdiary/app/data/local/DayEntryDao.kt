package dev.wfhdiary.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DayEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<DayEntryEntity>)

    @Query(
        """
        SELECT * FROM day_entries
        WHERE date >= :fromDate AND date <= :toDate
        ORDER BY date
        """,
    )
    suspend fun getBetween(fromDate: String, toDate: String): List<DayEntryEntity>

    @Query("SELECT * FROM day_entries ORDER BY date")
    suspend fun getAll(): List<DayEntryEntity>
}
