package dev.wfhdiary.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DayEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class WfhDiaryDatabase : RoomDatabase() {
    abstract fun dayEntryDao(): DayEntryDao

    companion object {
        @Volatile
        private var instance: WfhDiaryDatabase? = null

        fun getInstance(context: Context): WfhDiaryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WfhDiaryDatabase::class.java,
                    "wfh_diary.db",
                ).build().also { instance = it }
            }
    }
}
