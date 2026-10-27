package com.example.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String,
    val sizeBytes: Long = 0L
)

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class Available(val updateInfo: UpdateInfo) : UpdateStatus()
    data class UpToDate(val currentVersionName: String, val currentVersionCode: Int) : UpdateStatus()
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedKbps: Long = 0L
    ) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File, val updateInfo: UpdateInfo) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

class AppUpdateManager(private val context: Context) {

    private val prefs = AppPreferences.getInstance(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    val currentVersionCode: Int = BuildConfig.VERSION_CODE
    val currentVersionName: String = BuildConfig.VERSION_NAME

    /**
     * Checks for updates against the configured URL or default release source.
     */
    suspend fun checkForUpdates(customManifestUrl: String? = null): UpdateStatus = withContext(Dispatchers.IO) {
        _updateStatus.value = UpdateStatus.Checking

        val resolvedUrl = resolveUpdateUrl(
            customManifestUrl
                ?: prefs.customUpdateUrl.takeIf { it.isNotBlank() }
                ?: DEFAULT_UPDATE_MANIFEST_URL
        )

        try {
            val request = Request.Builder()
                .url(resolvedUrl)
                .header("User-Agent", "DNS-Switcher-Android/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/vnd.github.v3+json, application/json")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonBody = response.body?.string() ?: ""
                val json = JSONObject(jsonBody)

                // Case 1: GitHub Releases API format
                if (json.has("tag_name") && json.has("assets")) {
                    val rawTagName = json.optString("tag_name", "")
                    val remoteVersionName = rawTagName.removePrefix("v").removePrefix("V")
                    val changelog = json.optString("body", "• New features and performance improvements.")
                    val assets = json.optJSONArray("assets")
                    var downloadUrl = ""
                    var sizeBytes = 0L

                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val assetName = asset.optString("name", "")
                            if (assetName.endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = asset.optString("browser_download_url", "")
                                sizeBytes = asset.optLong("size", 0L)
                                break
                            }
                        }
                    }

                    val isNewer = isVersionNewer(remoteVersionName, currentVersionName)
                    if (isNewer && downloadUrl.isNotBlank()) {
                        val info = UpdateInfo(
                            versionCode = currentVersionCode + 1,
                            versionName = remoteVersionName,
                            downloadUrl = downloadUrl,
                            changelog = changelog,
                            sizeBytes = sizeBytes
                        )
                        val status = UpdateStatus.Available(info)
                        _updateStatus.value = status
                        return@withContext status
                    } else {
                        val status = UpdateStatus.UpToDate(currentVersionName, currentVersionCode)
                        _updateStatus.value = status
                        return@withContext status
                    }
                }

                // Case 2: Standard JSON manifest format
                val remoteVersionCode = json.optInt("versionCode", currentVersionCode)
                val remoteVersionName = json.optString("versionName", currentVersionName)
                val downloadUrl = json.optString("downloadUrl", "")
                val changelog = json.optString("changelog", "Bug fixes, performance improvements, and network engine stability enhancements.")
                val sizeBytes = json.optLong("sizeBytes", 0L)

                val isNewer = remoteVersionCode > currentVersionCode || isVersionNewer(remoteVersionName, currentVersionName)
                if (isNewer && downloadUrl.isNotBlank()) {
                    val info = UpdateInfo(
                        versionCode = remoteVersionCode,
                        versionName = remoteVersionName,
                        downloadUrl = downloadUrl,
                        changelog = changelog,
                        sizeBytes = sizeBytes
                    )
                    val status = UpdateStatus.Available(info)
                    _updateStatus.value = status
                    return@withContext status
                } else {
                    val status = UpdateStatus.UpToDate(currentVersionName, currentVersionCode)
                    _updateStatus.value = status
                    return@withContext status
                }
            } else {
                val status = UpdateStatus.UpToDate(currentVersionName, currentVersionCode)
                _updateStatus.value = status
                return@withContext status
            }
        } catch (e: Exception) {
            val status = UpdateStatus.UpToDate(currentVersionName, currentVersionCode)
            _updateStatus.value = status
            return@withContext status
        }

    }

    /**
     * Downloads an APK with real-time percentage progress.
     */
    suspend fun downloadUpdate(info: UpdateInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }
            val targetApk = File(updatesDir, "DNS_Switcher_${info.versionName}.apk")
            if (targetApk.exists()) {
                targetApk.delete()
            }

            _updateStatus.value = UpdateStatus.Downloading(0, 0, info.sizeBytes)

            val request = Request.Builder()
                .url(info.downloadUrl)
                .header("User-Agent", "DNS-Switcher-Android/${BuildConfig.VERSION_NAME}")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                _updateStatus.value = UpdateStatus.Error("Failed to download APK: HTTP ${response.code}")
                return@withContext false
            }

            val body = response.body!!
            val totalBytes = if (body.contentLength() > 0) body.contentLength() else info.sizeBytes
            var downloadedBytes = 0L
            val buffer = ByteArray(8192)
            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(targetApk)

            var lastReportTime = System.currentTimeMillis()
            var bytesSinceReport = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        bytesSinceReport += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastReportTime
                        if (elapsed >= 250 || (totalBytes > 0 && downloadedBytes == totalBytes)) {
                            val percent = if (totalBytes > 0) {
                                ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                -1
                            }
                            val speedKbps = if (elapsed > 0) (bytesSinceReport * 1000) / (elapsed * 1024) else 0L
                            _updateStatus.value = UpdateStatus.Downloading(
                                progressPercent = percent,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                speedKbps = speedKbps
                            )
                            lastReportTime = now
                            bytesSinceReport = 0L
                        }
                    }
                    output.flush()
                }
            }

            _updateStatus.value = UpdateStatus.ReadyToInstall(targetApk, info)
            return@withContext true
        } catch (e: Exception) {
            _updateStatus.value = UpdateStatus.Error(e.message ?: "Download interrupted")
            return@withContext false
        }
    }

    /**
     * Checks if install from unknown sources is allowed, and starts installation.
     */
    fun installApk(apkFile: File): InstallResult {
        if (!apkFile.exists()) {
            return InstallResult.FileNotFound
        }

        // Check permission on Android 8.0+ (Oreo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return InstallResult.PermissionRequired
            }
        }

        return try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            InstallResult.Started
        } catch (e: Exception) {
            InstallResult.Error(e.message ?: "Failed to open package installer")
        }
    }

    /**
     * Triggers a simulated direct update test so users and testers can test the complete
     * in-app download and installation workflow immediately.
     */
    fun triggerSimulatedUpdate(targetVersionName: String = "1.1") {
        _updateStatus.value = UpdateStatus.Available(
            UpdateInfo(
                versionCode = currentVersionCode + 1,
                versionName = targetVersionName,
                downloadUrl = prefs.customUpdateUrl.ifBlank { "https://example.com/DNS_Switcher.apk" },
                changelog = "• Modern Android 17 frosted glass design\n• Simplified 1-tap Automatic vs Manual guides\n• Instant DNS latency testing\n• In-app seamless auto-updates",
                sizeBytes = 8_400_000L
            )
        )
    }

    fun resetStatus() {
        _updateStatus.value = UpdateStatus.Idle
    }

    sealed class InstallResult {
        object Started : InstallResult()
        object PermissionRequired : InstallResult()
        object FileNotFound : InstallResult()
        data class Error(val message: String) : InstallResult()
    }

    companion object {
        const val DEFAULT_UPDATE_MANIFEST_URL = "https://raw.githubusercontent.com/adnanmd/dns-switcher/main/version.json"

        /**
         * Converts standard GitHub repo links into the direct GitHub releases API endpoint:
         * https://github.com/owner/repo -> https://api.github.com/repos/owner/repo/releases/latest
         */
        fun resolveUpdateUrl(inputUrl: String): String {
            val trimmed = inputUrl.trim()
            if (trimmed.startsWith("https://github.com/") && !trimmed.contains("/releases/download/")) {
                val parts = trimmed.removePrefix("https://github.com/").removeSuffix("/").split("/")
                if (parts.size >= 2) {
                    val owner = parts[0]
                    val repo = parts[1]
                    return "https://api.github.com/repos/$owner/$repo/releases/latest"
                }
            } else if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && trimmed.contains("/")) {
                val parts = trimmed.split("/")
                if (parts.size == 2) {
                    return "https://api.github.com/repos/${parts[0]}/${parts[1]}/releases/latest"
                }
            }
            return trimmed
        }

        /**
         * Compares semantic version strings (e.g., "1.1" vs "1.0", "2.0.1" vs "2.0.0").
         */
        fun isVersionNewer(remote: String, current: String): Boolean {
            val remoteParts = remote.trim().removePrefix("v").removePrefix("V").split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = current.trim().removePrefix("v").removePrefix("V").split(".").mapNotNull { it.toIntOrNull() }
            val length = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until length) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }
    }
}

