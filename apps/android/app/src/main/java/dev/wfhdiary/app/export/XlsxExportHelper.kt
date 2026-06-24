package dev.wfhdiary.app.export

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import dev.wfhdiary.app.R
import java.io.File

object XlsxExportHelper {
    fun saveAndShare(activity: Activity, bytes: ByteArray, filename: String) {
        val dir = File(activity.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, filename)
        file.writeBytes(bytes)
        val uri =
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file,
            )
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        activity.startActivity(
            Intent.createChooser(intent, activity.getString(R.string.settings_export_xlsx)),
        )
    }
}
