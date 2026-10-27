package dev.wfhdiary.app.backup

import java.io.IOException

class FakeBackupFolder : BackupFolder {
    val files = linkedMapOf<String, String>()
    var failWrites = false

    override fun listNames(): List<String> = files.keys.toList()

    override fun write(name: String, text: String) {
        if (failWrites) throw IOException("disk full")
        files[name] = text
    }

    override fun delete(name: String) {
        files.remove(name)
    }
}
