package dev.wfhdiary.app.backup

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import dev.wfhdiary.app.R
import java.io.File

object BackupShareHelper {
    fun share(activity: Activity, file: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.backup_share)))
    }
}
