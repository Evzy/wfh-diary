package dev.wfhdiary.app.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WfhDiaryDatabaseTest {
    private lateinit var db: WfhDiaryDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WfhDiaryDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun day(date: String) = DayEntryEntity(date, "08:00", "17:00", 1.0, null, "Home", 0L)

    @Test
    fun days_between_and_delete() = runBlocking {
        val dao = db.dayEntryDao()
        dao.upsertAll(listOf(day("2025-07-01"), day("2025-07-02"), day("2025-08-01")))
        assertEquals(2, dao.getBetween("2025-07-01", "2025-07-31").size)
        dao.delete("2025-07-01")
        assertNull(dao.get("2025-07-01"))
        assertEquals(2, dao.getAll().size)
    }

    @Test
    fun leave_ordered_by_start() = runBlocking {
        val dao = db.leavePeriodDao()
        dao.insert(LeavePeriodEntity("b", "2025-12-22", "2025-12-30", null, 0L))
        dao.insert(LeavePeriodEntity("a", "2025-09-01", "2025-09-02", "x", 0L))
        assertEquals(listOf("a", "b"), dao.getAll().map { it.id })
    }

    @Test
    fun settings_single_row_upsert() = runBlocking {
        val dao = db.settingsDao()
        assertNull(dao.get())
        dao.upsert(SettingsEntity(settingsJson = "{}", legacyJson = null))
        dao.upsert(SettingsEntity(settingsJson = "{\"a\":1}", legacyJson = "{}"))
        assertEquals("{\"a\":1}", dao.get()!!.settingsJson)
    }
}
