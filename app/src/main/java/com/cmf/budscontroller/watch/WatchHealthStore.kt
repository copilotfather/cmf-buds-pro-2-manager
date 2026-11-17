package com.cmf.budscontroller.watch

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WatchHealthSummary(val steps: Long = 0, val distanceMeters: Long = 0, val kcal: Long = 0,
    val heartRate: Int? = null, val spo2: Int? = null, val stress: Int? = null, val sleepMinutes: Long = 0,
    val records: Int = 0, val lastSync: Long = 0, val storageNeedsAttention: Boolean = false)

class WatchHealthStore(context: Context) {
    private val prefs = context.getSharedPreferences("watch_health", Context.MODE_PRIVATE)
    private val keyAlias = "cmf_watch_health_v1"
    private var address = ""
    private var storageReadable = true
    private var rows = linkedMapOf<String, JSONObject>()
    private val _summary = MutableStateFlow(WatchHealthSummary())
    val summary = _summary.asStateFlow()
    fun select(device: String) {
        address = device
        rows.clear()
        storageReadable = true
        runCatching {
            val stored = prefs.getString(device, null) ?: "[]"
            val array = JSONArray(decrypt(device, stored))
            for (i in 0 until array.length().coerceAtMost(12000)) {
                val row = array.getJSONObject(i)
                if (row.getLong("t") >= System.currentTimeMillis()/1000 - 31*86400L) rows["${row.getInt("type")}:${row.getLong("t")}"] = row
            }
            if (!stored.startsWith("enc1:") && array.length() > 0) {
                prefs.edit().putString(device, encrypt(device, JSONArray(rows.values.toList()).toString())).apply()
            }
        }.onFailure { storageReadable = false }
        summarize()
    }
    fun accept(type: Int, payload: ByteArray) {
        val width = when(type) { 0x56 -> 32; 0x53,0xe0,0x55,0x9d -> 8; 0xda -> 5; 0x58 -> 0; else -> return }
        fun record(time: Long, values: List<Long>) {
            val now = System.currentTimeMillis()/1000
            if (time < now - 31*86400 || time > now+86400) return
            rows["$type:$time"] = JSONObject().put("type",type).put("t",time).put("v",JSONArray(values))
        }
        val data = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        if (type == 0x58) {
            if (payload.size < 18 || (payload.size-18)%8 != 0) return
            val start = data.int.toLong() and 0xffffffffL
            val end = data.int.toLong() and 0xffffffffL
            val deep = data.short.toInt() and 65535
            val core = data.short.toInt() and 65535
            val rem = data.short.toInt() and 65535
            if (end > start && end-start <= 86400) record(start,listOf(end,deep.toLong(),core.toLong(),rem.toLong()))
        } else {
            if (payload.size % width != 0) return
            while(data.remaining() >= width) {
                val start = data.position()
                val time = data.int.toLong() and 0xffffffffL
                val value = if (type == 0xda) (data.get().toInt() and 255).toLong() else data.int.toLong() and 0xffffffffL
                if (type == 0x56) {
                    val distance = data.int.toLong() and 0xffffffffL
                    val calories = data.int.toLong() and 0xffffffffL
                    if (value <= 100000 && distance <= 1000000 && calories <= 100000000) record(time,listOf(value,distance,calories))
                } else {
                    val valid = when(type) { 0x55 -> value in 1..100; 0x9d -> value in 0..100; else -> value in 20..250 }
                    if (valid) record(time,listOf(value))
                }
                data.position(start+width)
            }
        }
        rows = rows.entries.filter { it.value.getLong("t") >= System.currentTimeMillis()/1000 - 31*86400L }
            .sortedBy { it.value.getLong("t") }.takeLast(12000).associateTo(linkedMapOf()) { it.key to it.value }
        summarize()
    }
    fun flush(completed: Boolean = false): Boolean {
        if (address.isBlank() || !storageReadable) return false
        var saved = false
        try {
            val serialized = JSONArray(rows.values.toList()).toString()
            prefs.edit().putString(address, encrypt(address, serialized)).apply()
            if (completed) prefs.edit().putLong("sync_$address",System.currentTimeMillis()).apply()
            saved = true
        } catch (error: Exception) {
            storageReadable = false
            Log.e("WatchHealthStore", "Could not persist health data securely", error)
        }
        summarize()
        return saved
    }
    fun clear() {
        if (address.isBlank()) return
        prefs.edit().remove(address).remove("sync_$address").apply()
        rows.clear()
        storageReadable = true
        summarize()
    }
    private fun summarize() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun day(seconds: Long) = Instant.ofEpochSecond(seconds).atZone(zone).toLocalDate()
        val daily = rows.values.filter { day(it.getLong("t")) == today }
        fun sum(index: Int) = daily.filter { it.getInt("type") == 0x56 }.sumOf { it.getJSONArray("v").getLong(index) }
        fun latest(types: Set<Int>): Int? = daily.filter { it.getInt("type") in types }.maxByOrNull { it.getLong("t") }?.getJSONArray("v")?.getInt(0)
        val sleep = rows.values.filter { it.getInt("type") == 0x58 && day(it.getJSONArray("v").getLong(0)) == today }
            .sumOf { val v=it.getJSONArray("v"); v.getLong(1)+v.getLong(2)+v.getLong(3) } / 60
        _summary.value = WatchHealthSummary(
            sum(0), sum(1), sum(2) / 1000,
            latest(setOf(0x53, 0xe0)), latest(setOf(0x55)), latest(setOf(0x9d)),
            sleep, rows.size, prefs.getLong("sync_$address", 0), !storageReadable
        )
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun existingEncryptionKey(): SecretKey =
        (KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .getKey(keyAlias, null) as? SecretKey)
            ?: error("Health encryption key is unavailable")

    private fun encrypt(device: String, plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        cipher.updateAAD(device.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        return "enc1:" + Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(device: String, stored: String): String {
        if (!stored.startsWith("enc1:")) return stored
        val packed = Base64.decode(stored.removePrefix("enc1:"), Base64.NO_WRAP)
        require(packed.size >= GCM_IV_LENGTH + GCM_TAG_LENGTH_BITS / 8) { "Invalid encrypted health record" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            existingEncryptionKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, packed.copyOfRange(0, GCM_IV_LENGTH))
        )
        cipher.updateAAD(device.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(packed.copyOfRange(GCM_IV_LENGTH, packed.size)).toString(Charsets.UTF_8)
    }

    private companion object {
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}

/** Only holds an active, explicitly authenticated watch session. Never queues notification content. */
object WatchNotificationBridge {
    @Volatile var send: ((String,String,String) -> Unit)? = null
}
