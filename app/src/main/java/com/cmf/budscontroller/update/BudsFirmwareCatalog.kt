package com.cmf.budscontroller.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class FirmwareCheckResult(
    val currentVersion: String,
    val latestVersion: String?,
    val sourceUrl: String?,
    val checkedAtMillis: Long,
    val error: String? = null
) {
    private val versionsComparable: Boolean
        get() = VERSION_PATTERN.matches(currentVersion) && latestVersion?.let(VERSION_PATTERN::matches) == true

    val isUpdateAvailable: Boolean
        get() = versionsComparable && compareVersions(currentVersion, latestVersion!!) < 0

    val isCurrent: Boolean
        get() = versionsComparable && compareVersions(currentVersion, latestVersion!!) >= 0

    private companion object {
        val VERSION_PATTERN = Regex("\\d+(?:\\.\\d+){1,3}")

        fun compareVersions(left: String, right: String): Int {
            val leftParts = left.split('.').mapNotNull(String::toIntOrNull)
            val rightParts = right.split('.').mapNotNull(String::toIntOrNull)
            for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
                val comparison = (leftParts.getOrNull(index) ?: 0).compareTo(rightParts.getOrNull(index) ?: 0)
                if (comparison != 0) return comparison
            }
            return 0
        }
    }
}

class BudsFirmwareCatalog(context: Context) {
    private val preferences = context.getSharedPreferences("cmf_firmware_catalog", Context.MODE_PRIVATE)

    suspend fun check(currentVersion: String): FirmwareCheckResult = withContext(Dispatchers.IO) {
        val checkedAt = System.currentTimeMillis()
        try {
            val connection = (URL(CATALOG_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "CMF-Buds-Pro-2-Manager")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    error("Catalog returned HTTP ${connection.responseCode}")
                }
                val catalog = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                require(catalog.optInt("schemaVersion") == 1) { "Unsupported firmware catalog schema" }
                val model = catalog.getJSONObject("models").optJSONObject(MODEL_ID)
                    ?: return@withContext FirmwareCheckResult(currentVersion, null, null, checkedAt)
                val entries = model.getJSONArray("confirmedMinimumVersions")
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    if (entry.getString("modelRevision") == MODEL_REVISION) {
                        val latest = entry.getString("version")
                        require(VERSION_PATTERN.matches(latest)) { "Invalid version in catalog" }
                        if (!currentVersion.startsWith(entry.getString("versionFamilyPrefix"))) {
                            return@withContext FirmwareCheckResult(currentVersion, null, null, checkedAt)
                        }
                        preferences.edit()
                            .putLong(KEY_CHECKED_AT, checkedAt)
                            .putString(KEY_LATEST_VERSION, latest)
                            .putString(KEY_SOURCE_URL, entry.getString("sourceUrl"))
                            .apply()
                        return@withContext FirmwareCheckResult(
                            currentVersion,
                            latest,
                            entry.getString("sourceUrl"),
                            checkedAt
                        )
                    }
                }
                preferences.edit().putLong(KEY_CHECKED_AT, checkedAt).apply()
                FirmwareCheckResult(currentVersion, null, null, checkedAt)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            FirmwareCheckResult(
                currentVersion = currentVersion,
                latestVersion = preferences.getString(KEY_LATEST_VERSION, null)
                    ?.takeIf { currentVersion.startsWith(VERSION_FAMILY_PREFIX) },
                sourceUrl = preferences.getString(KEY_SOURCE_URL, null)
                    ?.takeIf { currentVersion.startsWith(VERSION_FAMILY_PREFIX) },
                checkedAtMillis = preferences.getLong(KEY_CHECKED_AT, 0L),
                error = e.message ?: "Firmware catalog check failed"
            )
        }
    }

    companion object {
        private const val CATALOG_URL = "https://raw.githubusercontent.com/copilotfather/cmf-buds-pro-2-manager/main/firmware-catalog.json"
        private const val MODEL_ID = "cmf-buds-pro-2"
        private const val MODEL_REVISION = "2025"
        private const val VERSION_FAMILY_PREFIX = "1.0.1."
        private val VERSION_PATTERN = Regex("\\d+(?:\\.\\d+){1,3}")
        private const val KEY_CHECKED_AT = "checked_at"
        private const val KEY_LATEST_VERSION = "latest_version"
        private const val KEY_SOURCE_URL = "source_url"
    }
}
