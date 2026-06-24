package dev.wfhdiary.app.update

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.wfhdiary.app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

@Serializable
private data class GithubLatestRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

object GithubApkReleaseUpdater {
    private val json = Json { ignoreUnknownKeys = true }

    private fun httpClient(): HttpClient =
        HttpClient(Android) {
            expectSuccess = true
        }

    fun isConfigured(): Boolean =
        BuildConfig.UPDATE_GITHUB_OWNER.isNotBlank() && BuildConfig.UPDATE_GITHUB_REPO.isNotBlank()

    suspend fun fetchLatestApkRelease(
        owner: String = BuildConfig.UPDATE_GITHUB_OWNER,
        repo: String = BuildConfig.UPDATE_GITHUB_REPO,
        token: String = BuildConfig.UPDATE_GITHUB_TOKEN,
    ): Result<GithubApkReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val o = owner.trim()
                val r = repo.trim()
                require(o.isNotEmpty() && r.isNotEmpty()) { "GitHub owner/repo not configured" }
                val url = "https://api.github.com/repos/$o/$r/releases/latest"
                httpClient().use { client ->
                    val raw =
                        client.get(url) {
                            header("Accept", "application/vnd.github+json")
                            header("X-GitHub-Api-Version", "2022-11-28")
                            header("User-Agent", "WfhDiary/${BuildConfig.VERSION_NAME}")
                            if (token.isNotBlank()) {
                                header("Authorization", "Bearer $token")
                            }
                        }.body<String>()
                    val release = json.decodeFromString<GithubLatestRelease>(raw)
                    val apk =
                        release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                            ?: error("No .apk asset on latest release (${release.tagName})")
                    GithubApkReleaseInfo(
                        tagName = release.tagName,
                        releaseTitle = release.name,
                        assetName = apk.name,
                        downloadUrl = apk.browserDownloadUrl,
                    )
                }
            }
        }

    fun ensureInstallFromUnknownSources(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        if (activity.packageManager.canRequestPackageInstalls()) return true
        val intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
        activity.startActivity(intent)
        return false
    }

    suspend fun downloadApkToCache(activity: Activity, downloadUrl: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(activity.cacheDir, "github_apk_updates").apply { mkdirs() }
                val out = File(dir, "WfhDiary-update.apk")
                httpClient().use { client ->
                    val bytes =
                        client.get(downloadUrl) {
                            header("User-Agent", "WfhDiary/${BuildConfig.VERSION_NAME}")
                            if (BuildConfig.UPDATE_GITHUB_TOKEN.isNotBlank()) {
                                header("Authorization", "Bearer ${BuildConfig.UPDATE_GITHUB_TOKEN}")
                            }
                            header("Accept", "application/octet-stream")
                        }.body<ByteArray>()
                    out.writeBytes(bytes)
                }
                out
            }
        }

    fun startPackageInstall(activity: Activity, apkFile: File) {
        val uri =
            FileProvider.getUriForFile(
                activity,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                apkFile,
            )
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        activity.startActivity(intent)
    }
}
