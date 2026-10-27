package dev.wfhdiary.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LeavePeriodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(period: LeavePeriodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(periods: List<LeavePeriodEntity>)

    @Query("SELECT * FROM leave_periods WHERE id = :id")
    suspend fun get(id: String): LeavePeriodEntity?

    @Query("SELECT * FROM leave_periods ORDER BY startDate, endDate")
    suspend fun getAll(): List<LeavePeriodEntity>

    @Query("DELETE FROM leave_periods WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM leave_periods")
    suspend fun deleteAll()
}
