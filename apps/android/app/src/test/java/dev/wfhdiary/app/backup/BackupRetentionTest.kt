package dev.wfhdiary.app.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BackupRetentionTest {
    @Test
    fun file_name_uses_iso_date() {
        assertEquals("wfh-diary-backup-2026-10-27.json", BackupRetention.fileNameFor(LocalDate.of(2026, 10, 27)))
    }

    @Test
    fun keeps_newest_thirty_and_ignores_other_files() {
        val names =
            (1..35).map { BackupRetention.fileNameFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong())) } +
                listOf("wfh-diary-pre-restore-20260101-120000.json", "notes.txt")
        val toDelete = BackupRetention.filesToDelete(names.shuffled())
        assertEquals(5, toDelete.size)
        assertEquals(
            (1..5).map { BackupRetention.fileNameFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong())) }.toSet(),
            toDelete.toSet(),
        )
    }
}
