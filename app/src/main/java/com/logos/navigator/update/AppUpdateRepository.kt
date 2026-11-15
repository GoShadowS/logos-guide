package com.logos.navigator.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.logos.navigator.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal data class AvailableUpdate(
    val versionName: String,
    val assetName: String,
    val downloadUrl: String,
)

internal class AppUpdateRepository(context: Context) {
    private val appContext = context.applicationContext

    suspend fun findLatestUpdate(): AvailableUpdate? = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECTION_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "LogosGuideAndroid/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub release lookup returned HTTP ${connection.responseCode}")
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val versionName = release.optString("tag_name").removePrefix("v")
            if (!isNewerVersion(versionName, BuildConfig.VERSION_NAME)) return@withContext null
            val expectedAssetName = "logos-guide-$versionName.apk"
            val assets = release.optJSONArray("assets") ?: throw IOException("GitHub release has no assets")
            var asset: JSONObject? = null
            for (index in 0 until assets.length()) {
                val candidate = assets.optJSONObject(index) ?: continue
                if (candidate.optString("name") == expectedAssetName) {
                    asset = candidate
                    break
                }
            }
            val selectedAsset = asset ?: throw IOException("GitHub release is missing $expectedAssetName")
            val downloadUrl = selectedAsset.optString("browser_download_url")
            val downloadUri = Uri.parse(downloadUrl)
            if (
                downloadUri.scheme != "https" ||
                downloadUri.host?.equals("github.com", ignoreCase = true) != true ||
                downloadUri.path?.startsWith("/GoShadowS/logos-guide/releases/download/") != true
            ) {
                throw IOException("GitHub release asset URL is not trusted")
            }
            if (selectedAsset.optLong("size") <= 0L) throw IOException("GitHub release APK is empty")
            AvailableUpdate(versionName, expectedAssetName, downloadUrl)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Downloaded APKs are only referenced from in-memory state, so after a restart (or a completed
     * update) they are orphaned and would keep occupying the cache. Remove them on startup.
     */
    suspend fun deleteStaleDownloads() = withContext(Dispatchers.IO) {
        File(appContext.cacheDir, UPDATE_DIRECTORY).listFiles()?.forEach { file -> runCatching { file.delete() } }
        Unit
    }

    suspend fun downloadUpdate(update: AvailableUpdate, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val updateDirectory = File(appContext.cacheDir, UPDATE_DIRECTORY)
        if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
            throw IOException("Could not create the update cache")
        }
        val apkFile = File(updateDirectory, update.assetName)
        val connection = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECTION_TIMEOUT_MS
            readTimeout = DOWNLOAD_READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "LogosGuideAndroid/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("APK download returned HTTP ${connection.responseCode}")
            }
            val totalBytes = connection.contentLengthLong
            if (totalBytes > MAX_APK_BYTES) throw IOException("APK download exceeds the size limit")
            var downloadedBytes = 0L
            var lastProgress = -1
            connection.inputStream.use { input ->
                apkFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloadedBytes += count
                        if (downloadedBytes > MAX_APK_BYTES) throw IOException("APK download exceeds the size limit")
                        output.write(buffer, 0, count)
                        if (totalBytes > 0L) {
                            val progress = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
            if (downloadedBytes == 0L) throw IOException("APK download is empty")
            @Suppress("DEPRECATION")
            val packageInfo = appContext.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                ?: throw IOException("Downloaded file is not a valid Android package")
            if (packageInfo.packageName != appContext.packageName || packageInfo.versionName != update.versionName) {
                throw IOException("Downloaded package identity does not match the release")
            }
            if (!isNewerVersion(packageInfo.versionName.orEmpty(), BuildConfig.VERSION_NAME)) {
                throw IOException("Downloaded package is not newer than the installed version")
            }
            onProgress(100)
            apkFile
        } catch (error: Exception) {
            apkFile.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun openInstaller(apkFile: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !appContext.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(settingsIntent)
            return false
        }
        val apkUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            apkFile,
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            clipData = ClipData.newUri(appContext.contentResolver, apkFile.name, apkUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(installIntent)
        return true
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/GoShadowS/logos-guide/releases/latest"
        const val UPDATE_DIRECTORY = "updates"
        const val CONNECTION_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        const val DOWNLOAD_READ_TIMEOUT_MS = 45_000
        const val MAX_APK_BYTES = 250L * 1024L * 1024L
        const val BUFFER_SIZE = 16 * 1024
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
