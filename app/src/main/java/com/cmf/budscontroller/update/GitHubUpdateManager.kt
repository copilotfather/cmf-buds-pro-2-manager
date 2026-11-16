package com.cmf.budscontroller.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.cmf.budscontroller.i18n.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

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

    private val TAG = "GitHubUpdate"
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

    /**
     * Bulletproof multi-tier update checker.
     * Uses 4 independent channels so updates are NEVER missed:
     * 1. GitHub Releases Atom Feed (Zero rate limit, fast, no API key needed)
     * 2. GitHub 302 Location Redirect on /releases/latest (Direct HTTP header check)
     * 3. Raw version.json CDN
     * 4. GitHub REST API /releases list
     */
    suspend fun checkForUpdates(): UpdateInfo = withContext(Dispatchers.IO) {
        _updateState.value = _updateState.value.copy(isChecking = true, downloadError = null)
        val currentVer = getCurrentVersionName()
        Log.i(TAG, "Starting update check. Current app version: $currentVer")

        // -------------------------------------------------------------
        // Channel 1: GitHub Releases Atom Feed (Fastest, zero rate limit)
        // -------------------------------------------------------------
        try {
            val atomUrl = "https://github.com/$repoOwner/$repoName/releases.atom"
            val conn = (URL(atomUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)")
                connectTimeout = 4000
                readTimeout = 4000
                useCaches = false
            }
            if (conn.responseCode == 200) {
                val xml = conn.inputStream.bufferedReader().use { it.readText() }
                val entryMatcher = Pattern.compile("<entry>.*?<title>(.*?)</title>", Pattern.DOTALL).matcher(xml)
                if (entryMatcher.find()) {
                    val rawTitle = entryMatcher.group(1)?.trim() ?: ""
                    val tag = rawTitle.split(" ").firstOrNull()?.removePrefix("v")?.trim() ?: ""
                    if (tag.isNotBlank()) {
                        val hasNew = isNewerVersion(tag, currentVer)
                        val dlUrl = "https://github.com/$repoOwner/$repoName/releases/download/v$tag/CMF_Buds_Pro_2_Manager.apk"
                        Log.i(TAG, "[Channel 1: Atom] Latest tag: $tag, current: $currentVer, hasUpdate: $hasNew")

                        val res = UpdateInfo(
                            hasUpdate = hasNew,
                            latestVersion = tag,
                            releaseNotes = if (AppStrings.isRussian) "Доступна новая версия v$tag на GitHub. Нажмите для быстрой установки." else "New version v$tag available on GitHub. Tap to install.",
                            downloadUrl = dlUrl,
                            isChecking = false
                        )
                        _updateState.value = res
                        return@withContext res
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Channel 1: Atom] Error: ${e.message}")
        }

        // -------------------------------------------------------------
        // Channel 2: HTTP 302 Redirect Header on /releases/latest
        // -------------------------------------------------------------
        try {
            val latestUrl = "https://github.com/$repoOwner/$repoName/releases/latest"
            val conn = (URL(latestUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false // Keep 302 header intact!
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)")
                connectTimeout = 4000
                readTimeout = 4000
                useCaches = false
            }
            val location = conn.getHeaderField("Location") ?: ""
            if (location.contains("/tag/")) {
                val tag = location.substringAfterLast("/tag/").removePrefix("v").trim()
                val hasNew = isNewerVersion(tag, currentVer)
                val dlUrl = "https://github.com/$repoOwner/$repoName/releases/download/v$tag/CMF_Buds_Pro_2_Manager.apk"
                Log.i(TAG, "[Channel 2: 302 Redirect] Location: $location, tag: $tag, hasUpdate: $hasNew")

                val res = UpdateInfo(
                    hasUpdate = hasNew,
                    latestVersion = tag,
                    releaseNotes = if (AppStrings.isRussian) "Доступна новая версия v$tag на GitHub." else "New version v$tag available on GitHub.",
                    downloadUrl = dlUrl,
                    isChecking = false
                )
                _updateState.value = res
                return@withContext res
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Channel 2: 302 Redirect] Error: ${e.message}")
        }

        // -------------------------------------------------------------
        // Channel 3: version.json via GitHub Raw CDN
        // -------------------------------------------------------------
        try {
            val rawUrl = "https://raw.githubusercontent.com/$repoOwner/$repoName/main/version.json?t=${System.currentTimeMillis()}"
            val conn = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)")
                setRequestProperty("Cache-Control", "no-cache")
                connectTimeout = 4000
                readTimeout = 4000
                useCaches = false
            }
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val ver = json.optString("versionName", "").removePrefix("v").trim()
                val notes = json.optString("releaseNotes", if (AppStrings.isRussian) "Доступно обновление." else "Update available.")
                val dl = json.optString("downloadUrl", "https://github.com/$repoOwner/$repoName/releases/download/v$ver/CMF_Buds_Pro_2_Manager.apk")
                val hasNew = isNewerVersion(ver, currentVer)
                Log.i(TAG, "[Channel 3: CDN version.json] Latest: $ver, current: $currentVer, hasUpdate: $hasNew")

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
            Log.w(TAG, "[Channel 3: CDN] Error: ${e.message}")
        }

        // -------------------------------------------------------------
        // Channel 4: GitHub REST API (/releases array fallback)
        // -------------------------------------------------------------
        try {
            val apiUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases"
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "CMFBudsPro2Manager-Updater")
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(body)
                if (array.length() > 0) {
                    val latestRelease = array.getJSONObject(0)
                    val tagName = latestRelease.optString("tag_name", "").removePrefix("v").trim()
                    val notes = latestRelease.optString("body", if (AppStrings.isRussian) "Новые функции и оптимизация." else "New features and optimizations.")
                    val assets = latestRelease.optJSONArray("assets")

                    var apkUrl = ""
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }
                    if (apkUrl.isBlank()) {
                        apkUrl = "https://github.com/$repoOwner/$repoName/releases/download/v$tagName/CMF_Buds_Pro_2_Manager.apk"
                    }

                    val hasNew = isNewerVersion(tagName, currentVer)
                    Log.i(TAG, "[Channel 4: REST API] Tag: $tagName, current: $currentVer, hasUpdate: $hasNew")

                    val res = UpdateInfo(
                        hasUpdate = hasNew,
                        latestVersion = tagName,
                        releaseNotes = notes,
                        downloadUrl = apkUrl,
                        isChecking = false
                    )
                    _updateState.value = res
                    return@withContext res
                }
            } else {
                Log.w(TAG, "[Channel 4: REST API] HTTP ${conn.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Channel 4: REST API] Error: ${e.message}", e)
        } finally {
            _updateState.value = _updateState.value.copy(isChecking = false)
        }

        return@withContext _updateState.value
    }

    /**
     * Compares two semantic version strings safely (e.g., "2.3.0" vs "2.2.3.3").
     */
    fun isNewerVersion(latest: String, current: String): Boolean {
        if (latest.isBlank()) return false
        val cleanLatest = latest.removePrefix("v").trim()
        val cleanCurrent = current.removePrefix("v").trim()

        val lParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

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
            val target = if (url.isNotBlank()) url else "https://github.com/$repoOwner/$repoName/releases"
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open browser: ${e.message}")
        }
    }

    /**
     * Downloads the APK file directly with smooth progress tracking and follows all CDN redirects.
     */
    suspend fun downloadAndInstallDirectly(apkUrl: String) = withContext(Dispatchers.IO) {
        val targetUrl = if (apkUrl.isNotBlank()) apkUrl else "https://github.com/$repoOwner/$repoName/releases/latest"

        if (apkUrl.isBlank()) {
            openInBrowser(targetUrl)
            return@withContext
        }

        withContext(Dispatchers.Main) {
            val msg = if (AppStrings.isRussian) "Начало загрузки обновления..." else "Starting download..."
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }

        _updateState.value = _updateState.value.copy(isDownloading = true, downloadProgress = 0, downloadError = null)

        val destinationDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        val destination = File(destinationDir, "CMF_Buds_Pro_2_Manager.apk")
        if (destination.exists()) destination.delete()

        try {
            var currentUrl = apkUrl
            var connection: HttpURLConnection? = null
            var redirects = 0

            // Follow HTTP redirects across domains (GitHub releases -> AWS S3 objects.githubusercontent.com)
            while (redirects < 10) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 12000
                    readTimeout = 25000
                    setRequestProperty("User-Agent", "Mozilla/5.0 CMFBudsPro2Manager")
                }

                val status = connection.responseCode
                if (status in 300..399) {
                    val location = connection.getHeaderField("Location") ?: break
                    currentUrl = location
                    redirects++
                    continue
                }
                break
            }

            val conn = connection ?: throw Exception("Не удалось установить соединение")
            if (conn.responseCode !in 200..299) {
                throw Exception("Сервер ответил HTTP ${conn.responseCode}")
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
                val msg = if (AppStrings.isRussian) "Загрузка завершена! Запуск установщика..." else "Download complete! Starting installer..."
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                installApk(destination)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct download error: ${e.message}", e)
            _updateState.value = _updateState.value.copy(isDownloading = false, downloadError = e.message)

            withContext(Dispatchers.Main) {
                val msg = if (AppStrings.isRussian) "Загрузка не удалась (${e.message}). Открываем браузер..." else "Download failed (${e.message}). Opening browser..."
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                openInBrowser(apkUrl)
            }
        }
    }

    /**
     * Invokes Android Package Installer with FileProvider permissions.
     */
    fun installApk(file: File) {
        try {
            if (!file.exists() || file.length() < 1000) {
                Log.w(TAG, "APK file missing or invalid: ${file.length()} bytes")
                openInBrowser()
                return
            }

            // Check if app has permission to install unknown apps on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val msg = if (AppStrings.isRussian) "Разрешите установку приложений для завершения обновления" else "Please allow install unknown apps permission to finish update"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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
            Log.e(TAG, "Package installer invocation failed: ${e.message}", e)
            openInBrowser()
        }
    }
}
