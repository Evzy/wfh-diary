package dev.wfhdiary.app.ui.update

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.wfhdiary.app.BuildConfig
import dev.wfhdiary.app.R
import dev.wfhdiary.app.update.GithubApkReleaseUpdater
import dev.wfhdiary.app.update.GithubApkReleaseInfo
import dev.wfhdiary.app.update.ReleaseVersionCompare
import dev.wfhdiary.app.update.ReleaseVersusInstalled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUpdateUiState(
    val busy: Boolean = false,
    val status: String = "",
    val pendingRelease: GithubApkReleaseInfo? = null,
    val showUpdateDialog: Boolean = false,
) {
    val hasUpdate: Boolean
        get() = pendingRelease != null
}

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState = _uiState.asStateFlow()

    private var autoCheckStarted = false

    fun checkOnLoginIfNeeded() {
        if (autoCheckStarted || !GithubApkReleaseUpdater.isConfigured()) return
        autoCheckStarted = true
        checkForUpdate(manual = false)
    }

    fun checkForUpdate(manual: Boolean = false) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            if (!GithubApkReleaseUpdater.isConfigured()) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        pendingRelease = null,
                        showUpdateDialog = false,
                        status = app.getString(R.string.github_update_not_configured),
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(busy = true, status = app.getString(R.string.github_update_checking))
            }
            GithubApkReleaseUpdater.fetchLatestApkRelease().fold(
                onSuccess = { info -> applyReleaseInfo(info, autoPrompt = !manual) },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            pendingRelease = null,
                            showUpdateDialog = false,
                            status = e.message ?: app.getString(R.string.github_update_failed),
                        )
                    }
                },
            )
        }
    }

    private fun applyReleaseInfo(info: GithubApkReleaseInfo, autoPrompt: Boolean) {
        val app = getApplication<Application>()
        when (ReleaseVersionCompare.compare(info.tagName, BuildConfig.VERSION_NAME)) {
            ReleaseVersusInstalled.UPDATE_AVAILABLE -> {
                _uiState.update {
                    it.copy(
                        busy = false,
                        pendingRelease = info,
                        showUpdateDialog = autoPrompt,
                        status =
                            if (autoPrompt) {
                                ""
                            } else {
                                app.getString(R.string.github_update_found, info.tagName, info.assetName)
                            },
                    )
                }
            }
            ReleaseVersusInstalled.UP_TO_DATE -> {
                _uiState.update {
                    it.copy(
                        busy = false,
                        pendingRelease = null,
                        showUpdateDialog = false,
                        status =
                            if (autoPrompt) {
                                ""
                            } else {
                                app.getString(R.string.github_update_up_to_date)
                            },
                    )
                }
            }
            ReleaseVersusInstalled.INSTALLED_NEWER -> {
                _uiState.update {
                    it.copy(
                        busy = false,
                        pendingRelease = null,
                        showUpdateDialog = false,
                        status = app.getString(R.string.github_update_installed_newer, info.tagName),
                    )
                }
            }
            ReleaseVersusInstalled.UNKNOWN -> {
                _uiState.update {
                    it.copy(
                        busy = false,
                        pendingRelease = info,
                        showUpdateDialog = autoPrompt,
                        status = app.getString(R.string.github_update_found, info.tagName, info.assetName),
                    )
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = false) }
    }

    fun downloadAndInstall(activity: Activity) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val pending = _uiState.value.pendingRelease
            if (pending == null) {
                _uiState.update { it.copy(status = app.getString(R.string.github_update_check_first)) }
                return@launch
            }
            if (!GithubApkReleaseUpdater.ensureInstallFromUnknownSources(activity)) {
                _uiState.update {
                    it.copy(status = app.getString(R.string.github_update_enable_unknown_sources))
                }
                return@launch
            }
            _uiState.update {
                it.copy(busy = true, showUpdateDialog = false, status = app.getString(R.string.github_update_downloading))
            }
            val result =
                withContext(Dispatchers.IO) {
                    GithubApkReleaseUpdater.downloadApkToCache(activity, pending.downloadUrl)
                }
            result.fold(
                onSuccess = { file ->
                    _uiState.update {
                        it.copy(busy = false, status = app.getString(R.string.github_update_opening_installer))
                    }
                    GithubApkReleaseUpdater.startPackageInstall(activity, file)
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            status = e.message ?: app.getString(R.string.github_update_failed),
                        )
                    }
                },
            )
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppUpdateViewModel(application) as T
            }
    }
}
