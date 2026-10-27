package dev.wfhdiary.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupHealthTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    @Test
    fun no_folder_warns() {
        assertEquals(BackupWarning.NO_FOLDER, BackupHealth.warning(null, now, null, now))
    }

    @Test
    fun last_error_warns() {
        assertEquals(BackupWarning.FAILED, BackupHealth.warning("uri", now, "boom", now))
    }

    @Test
    fun stale_after_three_days() {
        assertNull(BackupHealth.warning("uri", now - 3 * day, null, now))
        assertEquals(BackupWarning.STALE, BackupHealth.warning("uri", now - 3 * day - 1, null, now))
        assertEquals(BackupWarning.STALE, BackupHealth.warning("uri", null, null, now))
    }
}
