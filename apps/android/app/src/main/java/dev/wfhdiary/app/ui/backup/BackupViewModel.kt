package dev.wfhdiary.app.ui.backup

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dev.wfhdiary.app.BuildConfig
import dev.wfhdiary.app.R
import dev.wfhdiary.app.WfhDiaryApplication
import dev.wfhdiary.app.backup.BackupCodec
import dev.wfhdiary.app.backup.BackupHealth
import dev.wfhdiary.app.backup.BackupOutcome
import dev.wfhdiary.app.backup.BackupParseResult
import dev.wfhdiary.app.backup.BackupShareHelper
import dev.wfhdiary.app.backup.RestoreOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WfhDiaryApplication
    private val prefs = app.backupPreferences

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update {
            it.copy(
                folderChosen = prefs.folderUri != null,
                folderLabel = prefs.folderLabel,
                lastSuccessMillis = prefs.lastSuccessMillis,
                lastError = prefs.lastError,
                warning = BackupHealth.warning(prefs.folderUri, prefs.lastSuccessMillis, prefs.lastError, System.currentTimeMillis()),
            )
        }
    }

    fun onFolderChosen(uri: Uri?) {
        if (uri == null) return
        val label =
            try {
                app.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                DocumentFile.fromTreeUri(app, uri)?.name
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = app.getString(R.string.backup_folder_failed, e.message ?: e.javaClass.simpleName))
                }
                return
            }
        prefs.folderUri = uri.toString()
        prefs.folderLabel = label
        backupNow()
    }

    fun backupNow() =
        runBusy {
            when (val outcome = app.backupService.backupToFolder()) {
                is BackupOutcome.Success -> app.getString(R.string.backup_done, outcome.fileName)
                is BackupOutcome.Failed -> app.getString(R.string.backup_failed, outcome.message)
                BackupOutcome.NoFolder -> app.getString(R.string.backup_no_folder)
            }
        }

    fun share(activity: Activity) =
        runBusy {
            val file = app.backupService.writeShareFile(File(app.cacheDir, "exports"))
            BackupShareHelper.share(activity, file)
            null
        }

    fun onRestoreFileChosen(uri: Uri?) {
        if (uri == null) return
        runBusy {
            val text =
                withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } ?: return@runBusy app.getString(R.string.restore_unreadable)
            when (val result = BackupCodec.decode(text)) {
                is BackupParseResult.Failure -> result.message
                is BackupParseResult.Success -> {
                    _uiState.update { it.copy(pendingRestore = PendingRestore(result.document, RestoreSource.FILE)) }
                    null
                }
            }
        }
    }

    fun importFromServer() =
        runBusy {
            val document =
                try {
                    if (BuildConfig.GOOGLE_SERVICES_CONFIGURED) {
                        val token = FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()?.token
                        app.firebaseIdTokenHolder.setToken(token)
                    }
                    app.legacyServerImporter.fetch()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@runBusy app.getString(R.string.import_failed, e.message ?: e.javaClass.simpleName)
                }
            when (val result = BackupCodec.validate(document)) {
                is BackupParseResult.Failure -> app.getString(R.string.import_failed, result.message)
                is BackupParseResult.Success -> {
                    _uiState.update { it.copy(pendingRestore = PendingRestore(result.document, RestoreSource.SERVER)) }
                    null
                }
            }
        }

    fun cancelRestore() {
        _uiState.update { it.copy(pendingRestore = null) }
    }

    fun confirmRestore() {
        val pending = _uiState.value.pendingRestore ?: return
        _uiState.update { it.copy(pendingRestore = null) }
        runBusy {
            when (val outcome = app.restoreService.restore(pending.document)) {
                is RestoreOutcome.Failed -> outcome.message
                is RestoreOutcome.Success -> {
                    if (pending.source == RestoreSource.SERVER) app.backupService.backupToFolder()
                    _uiState.update { it.copy(restoreGeneration = it.restoreGeneration + 1) }
                    app.getString(R.string.restore_done, outcome.days, outcome.leavePeriods)
                }
            }
        }
    }

    private fun runBusy(block: suspend () -> String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            val message =
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.message ?: e.javaClass.simpleName
                }
            _uiState.update { it.copy(busy = false, message = message) }
            refresh()
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = BackupViewModel(application) as T
            }
    }
}
