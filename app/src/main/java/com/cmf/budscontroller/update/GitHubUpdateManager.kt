package com.cmf.budscontroller.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean = false,
    val latestVersion: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String = "",
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadError: String? = null
)

class GitHubUpdateManager(private val context: Context) {

    private val repoOwner = "copilotfather"
    private val repoName = "cmf-buds-pro-2-manager"

    private val _updateState = MutableStateFlow(UpdateInfo())
    val updateState: StateFlow<UpdateInfo> = _updateState.asStateFlow()

    fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    suspend fun checkForUpdates(): UpdateInfo = withContext(Dispatchers.IO) {
        _updateState.value = _updateState.value.copy(isChecking = true)
        val currentVer = getCurrentVersionName()

        // 1. Try releases/latest (Zero cache, zero API rate limits, instantaneous)
        try {
            val redirectUrl = "https://github.com/$repoOwner/$repoName/releases/latest"
            val conn = (URL(redirectUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                connectTimeout = 5000
                readTimeout = 5000
                useCaches = false
            }
            conn.connect()

            val finalUrl = conn.url.toString()
            val location = conn.getHeaderField("Location") ?: ""
            val target = if (finalUrl.contains("/tag/")) finalUrl else location

            if (target.isNotBlank() && target.contains("/tag/")) {
                val tag = target.substringAfterLast("/tag/").removePrefix("v").trim()
                val hasNew = isNewerVersion(tag, currentVer)
                val dl = "https://github.com/$repoOwner/$repoName/releases/download/v$tag/CMF_Buds_Pro_2_Manager.apk"
                android.util.Log.i("GitHubUpdate", "Latest Check: target=$target, tag=$tag, current=$currentVer, hasNew=$hasNew")

                val res = UpdateInfo(
                    hasUpdate = hasNew,
                    latestVersion = tag,
                    releaseNotes = "Новое обновление v$tag доступно на GitHub.",
                    downloadUrl = dl,
                    isChecking = false
                )
                _updateState.value = res
                return@withContext res
            }
        } catch (e: Exception) {
            android.util.Log.w("GitHubUpdate", "Latest Check failed: ${e.message}")
        }

        // 2. Try raw CDN version.json (Fast worldwide CDN)
        try {
            val rawUrl = "https://raw.githubusercontent.com/$repoOwner/$repoName/main/version.json?t=${System.currentTimeMillis()}"
            val conn = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                setRequestProperty("Pragma", "no-cache")
                connectTimeout = 4000
                readTimeout = 4000
                useCaches = false
            }
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val ver = json.optString("versionName", "").removePrefix("v").trim()
                val notes = json.optString("releaseNotes", "Доступно обновление.")
                val dl = json.optString("downloadUrl", "")
                val hasNew = isNewerVersion(ver, currentVer)
                android.util.Log.i("GitHubUpdate", "CDN Check: latest=$ver, current=$currentVer, hasNew=$hasNew")

                val res = UpdateInfo(
                    hasUpdate = hasNew,
                    latestVersion = ver,
                    releaseNotes = notes,
                    downloadUrl = dl,
                    isChecking = false
                )
                _updateState.value = res
                return@withContext res
            }
        } catch (e: Exception) {
            android.util.Log.w("GitHubUpdate", "CDN Check failed: ${e.message}")
        }

        // 3. Fallback to GitHub REST API
        try {
            val apiUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "CMFBudsPro2Manager-Updater")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val tagName = json.optString("tag_name", "").removePrefix("v").trim()
                val body = json.optString("body", "Новые функции и оптимизация.")
                val assets = json.optJSONArray("assets")

                var apkDownloadUrl = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                val hasNew = isNewerVersion(tagName, currentVer)
                android.util.Log.i("GitHubUpdate", "API Check: latest=$tagName, current=$currentVer, hasNew=$hasNew")
                val res = UpdateInfo(
                    hasUpdate = hasNew,
                    latestVersion = tagName,
                    releaseNotes = body,
                    downloadUrl = apkDownloadUrl,
                    isChecking = false
                )
                _updateState.value = res
                return@withContext res
            } else {
                android.util.Log.w("GitHubUpdate", "GitHub API returned HTTP ${connection.responseCode}")
            }
        } catch (e: Exception) {
            android.util.Log.e("GitHubUpdate", "API Check failed: ${e.message}", e)
        } finally {
            _updateState.value = _updateState.value.copy(isChecking = false)
        }

        return@withContext _updateState.value
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        if (latest.isBlank()) return false
        val lParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(lParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val l = lParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun openInBrowser(url: String = "") {
        try {
            val target = if (url.isNotBlank()) url else "https://github.com/$repoOwner/$repoName/releases/latest"
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            android.util.Log.e("GitHubUpdate", "Failed to open browser: ${e.message}")
        }
    }

    suspend fun downloadAndInstallDirectly(apkUrl: String) = withContext(Dispatchers.IO) {
        val targetUrl = if (apkUrl.isNotBlank()) apkUrl else "https://github.com/$repoOwner/$repoName/releases/latest"

        if (apkUrl.isBlank()) {
            openInBrowser(targetUrl)
            return@withContext
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Начало загрузки обновления...", Toast.LENGTH_SHORT).show()
        }

        _updateState.value = _updateState.value.copy(isDownloading = true, downloadProgress = 0, downloadError = null)

        val destination = File(context.cacheDir, "CMF_Buds_Pro_2_Update.apk")
        if (destination.exists()) destination.delete()

        try {
            var currentUrl = apkUrl
            var connection: HttpURLConnection? = null
            var redirects = 0

            // Follow HTTP redirects (GitHub releases redirect to AWS S3 / objects.githubusercontent.com)
            while (redirects < 6) {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 12000
                connection.readTimeout = 20000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 CMFBudsPro2Manager")

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == 307 || status == 308
                ) {
                    val location = connection.getHeaderField("Location") ?: break
                    currentUrl = location
                    redirects++
                    continue
                }
                break
            }

            val conn = connection ?: throw Exception("Failed to establish connection")
            if (conn.responseCode !in 200..299) {
                throw Exception("HTTP ${conn.responseCode}")
            }

            val totalBytes = conn.contentLength
            val inputStream = BufferedInputStream(conn.inputStream)
            val outputStream = FileOutputStream(destination)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var downloadedBytes: Long = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                if (totalBytes > 0) {
                    val progress = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                    if (progress != _updateState.value.downloadProgress) {
                        _updateState.value = _updateState.value.copy(downloadProgress = progress)
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            _updateState.value = _updateState.value.copy(isDownloading = false, downloadProgress = 100)

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Загрузка завершена! Запуск установщика...", Toast.LENGTH_SHORT).show()
                installApk(destination)
            }
        } catch (e: Exception) {
            android.util.Log.e("GitHubUpdate", "Direct download error: ${e.message}", e)
            _updateState.value = _updateState.value.copy(isDownloading = false, downloadError = e.message)

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Не удалось загрузить напрямую (${e.message}). Открываем браузер...", Toast.LENGTH_LONG).show()
                openInBrowser(apkUrl)
            }
        }
    }

    fun installApk(file: File) {
        try {
            if (!file.exists() || file.length() < 1000) {
                android.util.Log.w("GitHubUpdate", "APK file missing or too small: ${file.length()} bytes")
                openInBrowser()
                return
            }

            // Check if app has permission to install packages on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "Разрешите установку неизвестных приложений для завершения обновления", Toast.LENGTH_LONG).show()
                    val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            android.util.Log.e("GitHubUpdate", "Package installer invocation failed: ${e.message}", e)
            openInBrowser()
        }
    }
}
