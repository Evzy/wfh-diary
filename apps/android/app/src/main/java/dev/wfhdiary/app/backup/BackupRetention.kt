package dev.wfhdiary.app.backup

import java.time.LocalDate

object BackupRetention {
    const val KEEP = 30
    private const val PREFIX = "wfh-diary-backup-"
    private val AUTO_NAME = Regex("""^wfh-diary-backup-\d{4}-\d{2}-\d{2}\.json$""")

    fun fileNameFor(date: LocalDate): String = "$PREFIX$date.json"

    /** Only automatic daily files are pruned; anything else in the folder is left alone. */
    fun filesToDelete(names: List<String>, keep: Int = KEEP): List<String> =
        names.filter { AUTO_NAME.matches(it) }.sortedDescending().drop(keep)
}
