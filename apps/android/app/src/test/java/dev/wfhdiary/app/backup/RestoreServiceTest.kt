package dev.wfhdiary.app.backup

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.wfhdiary.app.data.local.WfhDiaryDatabase
import dev.wfhdiary.app.data.remote.UpsertDayEntryRequest
import dev.wfhdiary.app.data.remote.UserSettingsDto
import dev.wfhdiary.app.data.store.DiaryRules
import dev.wfhdiary.app.data.store.DiaryStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RestoreServiceTest {
    private lateinit var db: WfhDiaryDatabase
    private lateinit var store: DiaryStore
    private val folder = FakeBackupFolder()
    private var rescheduledWith: UserSettingsDto? = null
    private lateinit var restore: RestoreService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, WfhDiaryDatabase::class.java).allowMainThreadQueries().build()
        store = DiaryStore(db)
        val prefs = BackupPreferences(context).apply { folderUri = "content://tree" }
        val backup = BackupService(store, prefs, "test", { folder })
        restore = RestoreService(store, backup, File("unused")) { rescheduledWith = it }
    }

    @After
    fun tearDown() = db.close()

    private fun document(days: List<BackupDay>) =
        BackupDocument(
            exportedAt = "2026-10-27T09:00:00Z",
            appVersion = "test",
            settings = DiaryRules.defaultSettings().copy(displayName = "From backup"),
            days = days,
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
    fun safety_backup_failure_aborts_restore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = BackupPreferences(context).apply { folderUri = null }
        val brokenDir = File.createTempFile("not-a-dir", ".tmp")
        val service = RestoreService(store, BackupService(store, prefs, "test", { folder }), brokenDir) {}
        store.upsertDay(2025, "2025-08-01", UpsertDayEntryRequest(location = "Office"))
        val outcome = service.restore(document(emptyList()))
        assertTrue(outcome is RestoreOutcome.Failed)
        assertEquals(1, store.getDays(2025, null, null).size)
    }
}
