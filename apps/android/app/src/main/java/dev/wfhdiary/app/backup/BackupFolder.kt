package dev.wfhdiary.app.backup

interface BackupFolder {
    fun listNames(): List<String>

    /** Creates or overwrites [name]. */
    fun write(name: String, text: String)

    fun delete(name: String)
}
