package dev.wfhdiary.app.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

class SafBackupFolder(
    private val context: Context,
    private val treeUri: Uri,
) : BackupFolder {
    private fun root(): DocumentFile =
        DocumentFile.fromTreeUri(context, treeUri)?.takeIf { it.canWrite() }
            ?: throw IOException("Backup folder is not accessible. Choose it again in Settings.")

    override fun listNames(): List<String> = root().listFiles().mapNotNull { it.name }

    override fun write(name: String, text: String) {
        val dir = root()
        val file =
            dir.findFile(name)
                ?: dir.createFile("application/json", name)
                ?: throw IOException("Could not create $name in the backup folder.")
        val stream =
            context.contentResolver.openOutputStream(file.uri, "wt")
                ?: throw IOException("Could not open $name for writing.")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    override fun delete(name: String) {
        root().findFile(name)?.delete()
    }
}
