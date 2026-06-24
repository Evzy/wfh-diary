package dev.wfhdiary.app.update

data class GithubApkReleaseInfo(
    val tagName: String,
    val releaseTitle: String?,
    val assetName: String,
    val downloadUrl: String,
)
