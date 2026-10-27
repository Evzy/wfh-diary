package dev.wfhdiary.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DayEntryEntity::class, LeavePeriodEntity::class, SettingsEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class WfhDiaryDatabase : RoomDatabase() {
    abstract fun dayEntryDao(): DayEntryDao

    abstract fun leavePeriodDao(): LeavePeriodDao

    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var instance: WfhDiaryDatabase? = null

        fun getInstance(context: Context): WfhDiaryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WfhDiaryDatabase::class.java,
                    "wfh_diary.db",
                )
                    // v1 was only a cache of server responses; nothing in it needs keeping.
                    .fallbackToDestructiveMigrationFrom(1)
                    .build()
                    .also { instance = it }
            }
    }
}
