package com.example.ainote.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.NetworkOnMainThreadException
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class GitHubReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long
)

data class GitHubReleaseInfo(
    val versionName: String,
    val releaseUrl: String,
    val body: String,
    val asset: GitHubReleaseAsset
)

class AppUpdateRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
        val latest = latestVersion.trim().trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        val current = currentVersion.trim().trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        val maxSize = maxOf(latest.size, current.size)
        for (index in 0 until maxSize) {
            val latestPart = latest.getOrElse(index) { 0 }
            val currentPart = current.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    @Throws(IOException::class)
    suspend fun fetchLatestRelease(): GitHubReleaseInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LatestReleaseApiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "WCR-Note-Updater")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("获取版本信息失败：HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("GitHub 返回为空。")
            val json = JSONObject(body)
            val assets = json.optJSONArray("assets")
            val asset = (0 until (assets?.length() ?: 0))
                .asSequence()
                .mapNotNull { index -> assets?.optJSONObject(index) }
                .firstOrNull { assetJson ->
                    assetJson.optString("name").endsWith(".apk", ignoreCase = true)
                }
                ?: throw IOException("最新版本未找到可下载的 APK。")
            GitHubReleaseInfo(
                versionName = json.optString("tag_name").ifBlank { json.optString("name") }.trim().trimStart('v', 'V'),
                releaseUrl = json.optString("html_url"),
                body = json.optString("body"),
                asset = GitHubReleaseAsset(
                    name = asset.optString("name"),
                    downloadUrl = asset.optString("browser_download_url"),
                    sizeBytes = asset.optLong("size")
                )
            )
        }
    }

    @Throws(IOException::class)
    suspend fun downloadReleaseApk(
        release: GitHubReleaseInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val targetDirectory = File(context.cacheDir, "update_apks").apply { mkdirs() }
        val safeVersion = release.versionName.ifBlank { "latest" }.replace(Regex("[^0-9A-Za-z._-]"), "_")
        val targetFile = File(targetDirectory, "WCR-Note-$safeVersion.apk")
        val request = Request.Builder()
            .url(release.asset.downloadUrl)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "WCR-Note-Updater")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("下载更新失败：HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("下载响应为空。")
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: release.asset.sizeBytes
            var downloadedBytes = 0L
            body.byteStream().use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        onProgress(downloadedBytes, totalBytes)
                    }
                    output.flush()
                }
            }
            onProgress(downloadedBytes, totalBytes)
            targetFile
        }
    }

    fun buildInstallIntent(apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    }

    fun buildUnknownSourcesIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun formatNetworkError(error: Throwable): String {
        return when (error) {
            is NetworkOnMainThreadException -> "更新请求误跑在主线程，请重试。"
            is UnknownHostException -> "无法连接 GitHub，请检查网络或代理。"
            is SocketTimeoutException -> "连接 GitHub 超时，请稍后重试。"
            is IOException -> error.message ?: "网络请求失败。"
            else -> error.message ?: "更新失败。"
        }
    }

    private companion object {
        const val LatestReleaseApiUrl = "https://api.github.com/repos/WCRfamfih/WCR-Note/releases/latest"
    }
}
