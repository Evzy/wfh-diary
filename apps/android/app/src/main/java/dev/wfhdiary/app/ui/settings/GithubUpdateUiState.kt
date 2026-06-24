package dev.wfhdiary.app.ui.settings

import dev.wfhdiary.app.update.GithubApkReleaseInfo

data class GithubUpdateUiState(
    val busy: Boolean = false,
    val status: String = "",
    val pendingRelease: GithubApkReleaseInfo? = null,
)
