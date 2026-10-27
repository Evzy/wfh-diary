package dev.wfhdiary.app.data.store

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.wfhdiary.app.backup.BackupDay
import dev.wfhdiary.app.backup.BackupDocument
import dev.wfhdiary.app.backup.BackupLeavePeriod
import dev.wfhdiary.app.data.local.WfhDiaryDatabase
import dev.wfhdiary.app.data.model.DayEntryUpdate
import dev.wfhdiary.app.data.model.NewLeave
import dev.wfhdiary.app.data.model.UserSettingsUpdate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DiaryStoreTest {
    private lateinit var db: WfhDiaryDatabase
    private lateinit var store: DiaryStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WfhDiaryDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = DiaryStore(db) { 1_000L }
    }

    @After
    fun tearDown() = db.close()

    private val homeDay = DayEntryUpdate("08:00", "17:00", 1.0, null, "Home")

    @Test
    fun diary_range_derived_without_storing_days() = runBlocking {
        val diary = store.getDiary(2025)
        assertEquals(2025, diary.fyYear)
        assertEquals("2025-07-01", diary.fyStartDate)
        assertEquals("2026-06-30", diary.fyEndDate)
        assertEquals(LocalCounts(0, 0), store.localCounts())
    }

    @Test
    fun upsert_then_partial_upsert_keeps_fields() = runBlocking {
        store.upsertDay(2025, "2025-07-01", homeDay)
        val saved = store.upsertDay(2025, "2025-07-01", DayEntryUpdate(description = "Notes"))
        assertEquals("08:00", saved.startTime)
        assertEquals("Notes", saved.description)
        assertEquals(8.0, saved.totalHours, 0.0001)
        assertEquals("Tuesday", saved.dayName)
    }

    @Test
    fun replace_clears_fields() = runBlocking {
        store.upsertDay(2025, "2025-07-01", homeDay)
        val saved = store.replaceDay(2025, "2025-07-01", DayEntryUpdate(location = "Office"))
        assertNull(saved.startTime)
        assertEquals("Office", saved.location)
    }

    @Test(expected = IllegalArgumentException::class)
    fun upsert_outside_fy_throws() {
        runBlocking { store.upsertDay(2025, "2026-07-01", homeDay) }
    }

    @Test
    fun get_days_filters_by_fy_and_range() = runBlocking {
        store.upsertDay(2025, "2025-07-01", homeDay)
        store.upsertDay(2025, "2025-08-01", homeDay)
        store.upsertDay(2024, "2025-06-30", homeDay)
        assertEquals(2, store.getDays(2025, null, null).size)
        assertEquals(listOf("2025-08-01"), store.getDays(2025, "2025-08-01", "2025-08-31").map { it.date })
    }

    @Test
    fun delete_day_removes_row_and_returns_empty_entry() = runBlocking {
        store.upsertDay(2025, "2025-07-01", homeDay)
        val cleared = store.deleteDay(2025, "2025-07-01")
        assertNull(cleared.location)
        assertTrue(store.getDays(2025, null, null).isEmpty())
    }

    @Test
    fun create_leave_swaps_dates_and_marks_fy_days_only() = runBlocking {
        val period = store.createLeave(2025, NewLeave("2026-07-01", "2026-06-29", "Trip"))
        assertEquals("2026-06-29", period.startDate)
        assertEquals("2026-07-01", period.endDate)
        val days = store.getDays(2025, null, null)
        assertEquals(listOf("2026-06-29", "2026-06-30"), days.map { it.date })
        assertTrue(days.all { it.description == "LEAVE" })
        assertEquals(1, store.getLeavePeriods().size)
    }

    @Test
    fun delete_leave_clears_only_uncovered_leave_days() = runBlocking {
        val a = store.createLeave(2025, NewLeave("2025-12-22", "2025-12-26"))
        store.createLeave(2025, NewLeave("2025-12-25", "2025-12-31"))
        store.replaceDay(2025, "2025-12-22", homeDay)
        store.deleteLeave(a.id)
        val dates = store.getDays(2025, "2025-12-01", "2025-12-31").map { it.date }
        assertTrue("worked day kept", "2025-12-22" in dates)
        assertTrue("leave day cleared", "2025-12-23" !in dates)
        assertTrue("covered by other period", "2025-12-25" in dates)
    }

    @Test
    fun summary_uses_fy_days() = runBlocking {
        store.upsertDay(2025, "2025-07-01", homeDay)
        store.upsertDay(2024, "2025-06-30", homeDay)
        assertEquals(8.0, store.getSummary(2025).homeHours, 0.0001)
    }

    @Test
    fun settings_default_then_update() = runBlocking {
        assertEquals("YOUR NAME HERE", store.getSettings().displayName)
        store.updateSettings(UserSettingsUpdate(displayName = "Me"))
        assertEquals("Me", store.getSettings().displayName)
    }

    @Test
    fun update_settings_preserves_legacy() = runBlocking {
        val legacy = JsonObject(mapOf("workTemplates" to JsonPrimitive("keep")))
        store.replaceAll(
            BackupDocument(
                exportedAt = "2026-10-27T09:00:00Z",
                appVersion = "test",
                settings = DiaryRules.defaultSettings(),
                legacy = legacy,
            ),
        )
        store.updateSettings(UserSettingsUpdate(displayName = "Changed"))
        val exported = store.exportDocument("test", "2026-10-27T09:00:00Z")
        assertEquals(legacy, exported.legacy)
        assertEquals("Changed", exported.settings.displayName)
    }

    @Test
    fun export_contains_xlsx_bytes_and_filename() = runBlocking {
        val (bytes, name) = store.downloadExportXlsx(2025)
        assertEquals("WFH-Diary-2025-2026.xlsx", name)
        assertEquals('P'.code.toByte(), bytes[0])
        assertEquals('K'.code.toByte(), bytes[1])
    }

    @Test
    fun export_document_and_replace_all_round_trip() = runBlocking {
        val legacy = JsonObject(mapOf("workTemplates" to JsonPrimitive("x")))
        val doc =
            BackupDocument(
                exportedAt = "2026-10-27T09:00:00Z",
                appVersion = "test",
                settings = DiaryRules.defaultSettings().copy(displayName = "Restored"),
                days = listOf(BackupDay("2025-07-01", "08:00", "17:00", 1.0, null, "Home")),
                leavePeriods = listOf(BackupLeavePeriod("id-1", "2025-12-22", "2025-12-23", null)),
                legacy = legacy,
            )
        store.upsertDay(2025, "2025-08-01", homeDay)
        store.replaceAll(doc)
        val exported = store.exportDocument("test", "2026-10-27T09:00:00Z")
        assertEquals(doc, exported)
    }

    @Test
    fun replace_all_rolls_back_when_a_step_fails() = runBlocking {
        store.upsertDay(2025, "2025-08-01", homeDay)
        store.createLeave(2025, NewLeave("2025-12-22", "2025-12-23", "Old"))
        store.updateSettings(UserSettingsUpdate(displayName = "Before"))
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_leave_insert BEFORE INSERT ON leave_periods BEGIN SELECT RAISE(ABORT, 'boom'); END",
        )
        val doc =
            BackupDocument(
                exportedAt = "2026-10-27T09:00:00Z",
                appVersion = "test",
                settings = DiaryRules.defaultSettings().copy(displayName = "After"),
                days = listOf(BackupDay("2025-07-01", location = "Home")),
                leavePeriods = listOf(BackupLeavePeriod("new", "2026-01-05", "2026-01-06", null)),
            )

        val failure = runCatching { store.replaceAll(doc) }.exceptionOrNull()

        assertTrue("replace should fail", failure != null)
        assertEquals(listOf("2025-08-01", "2025-12-22", "2025-12-23"), store.getDays(2025, null, null).map { it.date })
        assertEquals("Old", store.getLeavePeriods().single().note)
        assertEquals("Before", store.getSettings().displayName)
    }

    @Test
    fun local_counts_reports_days_and_leave_periods() = runBlocking {
        assertEquals(LocalCounts(0, 0), store.localCounts())
        store.upsertDay(2025, "2025-08-01", homeDay)
        store.createLeave(2025, NewLeave("2025-12-22", "2025-12-23"))
        assertEquals(LocalCounts(3, 1), store.localCounts())
    }

    @Test
    fun writes_emit_change_events() = runBlocking {
        val first = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { store.changes.first() } }
        store.upsertDay(2025, "2025-07-01", homeDay)
        first.await()
    }
}
