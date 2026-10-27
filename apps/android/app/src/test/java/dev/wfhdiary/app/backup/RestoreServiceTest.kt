package dev.wfhdiary.app.backup

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.wfhdiary.app.data.local.WfhDiaryDatabase
import dev.wfhdiary.app.data.model.UpsertDayEntryRequest
import dev.wfhdiary.app.data.model.UserSettingsDto
import dev.wfhdiary.app.data.store.DiaryRules
import dev.wfhdiary.app.data.store.DiaryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
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
class RestoreServiceTest {
    private lateinit var db: WfhDiaryDatabase
    private lateinit var store: DiaryStore
    private lateinit var prefs: BackupPreferences
    private lateinit var backup: BackupService
    private val folder = FakeBackupFolder()
    private var rescheduledWith: UserSettingsDto? = null
    private lateinit var restore: RestoreService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, WfhDiaryDatabase::class.java).allowMainThreadQueries().build()
        store = DiaryStore(db)
        prefs = BackupPreferences(context).apply { folderUri = "content://tree" }
        backup = BackupService(store, prefs, "test", { folder })
        restore = RestoreService(store, backup) { rescheduledWith = it }
    }

    @After
    fun tearDown() = db.close()

    private fun document(
        days: List<BackupDay>,
        leavePeriods: List<BackupLeavePeriod> = emptyList(),
        legacy: JsonObject = JsonObject(emptyMap()),
    ) = BackupDocument(
        exportedAt = "2026-10-27T09:00:00Z",
        appVersion = "test",
        settings = DiaryRules.defaultSettings().copy(displayName = "From backup"),
        days = days,
        leavePeriods = leavePeriods,
        legacy = legacy,
    )

    @Test
    fun restore_writes_safety_backup_replaces_data_and_reschedules() = runBlocking {
        store.upsertDay(2025, "2025-08-01", UpsertDayEntryRequest(location = "Office"))
        val outcome = restore.restore(document(listOf(BackupDay("2025-07-01", location = "Home"))))

        assertTrue(outcome is RestoreOutcome.Success)
        val safety = folder.files.entries.single { it.key.startsWith("wfh-diary-pre-restore-") }
        assertTrue("safety backup holds old data", safety.value.contains("2025-08-01"))
        assertEquals(listOf("2025-07-01"), store.getDays(2025, null, null).map { it.date })
        assertEquals("From backup", rescheduledWith?.displayName)
    }

    @Test
    fun invalid_document_is_refused_without_changes() = runBlocking {
        store.upsertDay(2025, "2025-08-01", UpsertDayEntryRequest(location = "Office"))
        val outcome = restore.restore(document(listOf(BackupDay("2025-02-30"))))
        assertTrue(outcome is RestoreOutcome.Failed)
        assertEquals(listOf("2025-08-01"), store.getDays(2025, null, null).map { it.date })
        assertNull(rescheduledWith)
        assertTrue(folder.files.isEmpty())
    }

    @Test
    fun restore_is_refused_without_a_backup_folder() = runBlocking {
        prefs.folderUri = null
        store.upsertDay(2025, "2025-08-01", UpsertDayEntryRequest(location = "Office"))
        val outcome = restore.restore(document(emptyList()))
        assertTrue(outcome is RestoreOutcome.Failed)
        assertTrue((outcome as RestoreOutcome.Failed).message.contains("backup folder"))
        assertEquals(1, store.getDays(2025, null, null).size)
        assertNull(rescheduledWith)
    }

    @Test
    fun restore_is_refused_when_safety_write_fails() = runBlocking {
        folder.failWrites = true
        store.upsertDay(2025, "2025-08-01", UpsertDayEntryRequest(location = "Office"))
        val outcome = restore.restore(document(emptyList()))
        assertTrue(outcome is RestoreOutcome.Failed)
        assertTrue("reason surfaced", (outcome as RestoreOutcome.Failed).message.contains("disk full"))
        assertEquals(1, store.getDays(2025, null, null).size)
        assertNull(rescheduledWith)
    }

    @Test
    fun restore_round_trips_leave_periods_and_legacy() = runBlocking {
        val legacy = JsonObject(mapOf("workTemplates" to JsonArray(listOf(JsonPrimitive("t1")))))
        val doc =
            document(
                days = listOf(BackupDay("2025-12-22", description = "LEAVE")),
                leavePeriods = listOf(BackupLeavePeriod("L1", "2025-12-22", "2025-12-23", "Xmas")),
                legacy = legacy,
            )
        val decoded = (BackupCodec.decode(BackupCodec.encode(doc)) as BackupParseResult.Success).document

        assertTrue(restore.restore(decoded) is RestoreOutcome.Success)

        val exported = backup.buildDocument()
        assertEquals(doc.leavePeriods, exported.leavePeriods)
        assertEquals(legacy, exported.legacy)
        assertEquals(doc.days, exported.days)
    }

    @Test
    fun on_restored_failure_does_not_fail_committed_restore() = runBlocking {
        val service = RestoreService(store, backup) { error("scheduler broke") }
        val outcome = service.restore(document(listOf(BackupDay("2025-07-01", location = "Home"))))
        assertTrue(outcome is RestoreOutcome.Success)
        assertEquals(listOf("2025-07-01"), store.getDays(2025, null, null).map { it.date })
    }

    @Test(expected = CancellationException::class)
    fun on_restored_cancellation_is_rethrown() {
        val service = RestoreService(store, backup) { throw CancellationException("cancelled") }
        runBlocking { service.restore(document(emptyList())) }
    }
}
