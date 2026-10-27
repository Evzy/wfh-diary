package dev.wfhdiary.app.backup

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.wfhdiary.app.data.local.WfhDiaryDatabase
import dev.wfhdiary.app.data.remote.UpsertDayEntryRequest
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupServiceTest {
    private lateinit var db: WfhDiaryDatabase
    private lateinit var store: DiaryStore
    private lateinit var prefs: BackupPreferences
    private val folder = FakeBackupFolder()
    private val clock = Clock.fixed(Instant.parse("2026-10-27T01:00:00Z"), ZoneId.of("Australia/Sydney"))
    private lateinit var service: BackupService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, WfhDiaryDatabase::class.java).allowMainThreadQueries().build()
        store = DiaryStore(db)
        prefs = BackupPreferences(context)
        service = BackupService(store, prefs, "test", { folder }, clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun no_folder_returns_no_folder() = runBlocking {
        prefs.folderUri = null
        assertEquals(BackupOutcome.NoFolder, service.backupToFolder())
    }

    @Test
    fun writes_todays_file_and_records_success() = runBlocking {
        prefs.folderUri = "content://tree"
        store.upsertDay(2025, "2025-07-01", UpsertDayEntryRequest("08:00", "17:00", 1.0, null, "Home"))
        val outcome = service.backupToFolder()
        assertEquals(BackupOutcome.Success("wfh-diary-backup-2026-10-27.json"), outcome)
        val decoded = BackupCodec.decode(folder.files.getValue("wfh-diary-backup-2026-10-27.json"))
        assertTrue(decoded is BackupParseResult.Success)
        assertEquals(1, (decoded as BackupParseResult.Success).document.days.size)
        assertEquals(clock.millis(), prefs.lastSuccessMillis)
        assertNull(prefs.lastError)
    }

    @Test
    fun prunes_old_files() = runBlocking {
        prefs.folderUri = "content://tree"
        repeat(31) { i -> folder.files["wfh-diary-backup-2026-09-%02d.json".format(i % 30 + 1)] = "{}" }
        folder.files["wfh-diary-backup-2026-08-31.json"] = "{}"
        service.backupToFolder()
        assertEquals(30, folder.files.keys.count { it.startsWith("wfh-diary-backup-") })
        assertTrue("wfh-diary-backup-2026-10-27.json" in folder.files)
    }

    @Test
    fun failure_is_recorded() = runBlocking {
        prefs.folderUri = "content://tree"
        folder.failWrites = true
        val outcome = service.backupToFolder()
        assertTrue(outcome is BackupOutcome.Failed)
        assertEquals("disk full", prefs.lastError)
    }

    @Test
    fun prune_failure_after_write_is_still_success() = runBlocking {
        prefs.folderUri = "content://tree"
        repeat(30) { i -> folder.files["wfh-diary-backup-2026-09-%02d.json".format(i + 1)] = "{}" }
        folder.failDeletes = true
        val outcome = service.backupToFolder()
        assertEquals(BackupOutcome.Success("wfh-diary-backup-2026-10-27.json"), outcome)
        assertEquals(clock.millis(), prefs.lastSuccessMillis)
        assertNull(prefs.lastError)
    }

    @Test
    fun safety_backup_goes_to_folder() = runBlocking {
        prefs.folderUri = "content://tree"
        val outcome = service.writeSafetyBackup()
        assertTrue(outcome is BackupOutcome.Success)
        val name = (outcome as BackupOutcome.Success).fileName
        assertTrue(name.startsWith("wfh-diary-pre-restore-"))
        assertTrue(name in folder.files)
    }

    @Test
    fun safety_backup_needs_folder_and_reports_write_failure() = runBlocking {
        prefs.folderUri = null
        assertEquals(BackupOutcome.NoFolder, service.writeSafetyBackup())

        prefs.folderUri = "content://tree"
        folder.failWrites = true
        assertEquals(BackupOutcome.Failed("disk full"), service.writeSafetyBackup())
        assertNull("safety failure is not a scheduled-backup failure", prefs.lastError)
    }
}
