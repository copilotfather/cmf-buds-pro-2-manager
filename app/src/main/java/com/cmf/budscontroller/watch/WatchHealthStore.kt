package com.cmf.budscontroller.watch

import android.content.Context
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
    val records: Int = 0, val lastSync: Long = 0)

class WatchHealthStore(context: Context) {
    private val prefs = context.getSharedPreferences("watch_health", Context.MODE_PRIVATE)
    private var address = ""
    private var rows = linkedMapOf<String, JSONObject>()
    private val _summary = MutableStateFlow(WatchHealthSummary())
    val summary = _summary.asStateFlow()
    fun select(device: String) {
        address = device
        rows.clear()
        runCatching {
            val array = JSONArray(prefs.getString(device, "[]"))
            for (i in 0 until array.length().coerceAtMost(12000)) {
                val row = array.getJSONObject(i)
                if (row.getLong("t") >= System.currentTimeMillis()/1000 - 31*86400L) rows["${row.getInt("type")}:${row.getLong("t")}"] = row
            }
        }
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
    fun flush(completed: Boolean = false) {
        if (address.isBlank()) return
        prefs.edit().putString(address,JSONArray(rows.values.toList()).toString()).apply()
        if (completed) prefs.edit().putLong("sync_$address",System.currentTimeMillis()).apply()
        summarize()
    }
    fun clear() { prefs.edit().remove(address).remove("sync_$address").apply(); rows.clear(); summarize() }
    private fun summarize() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun day(seconds: Long) = Instant.ofEpochSecond(seconds).atZone(zone).toLocalDate()
        val daily = rows.values.filter { day(it.getLong("t")) == today }
        fun sum(index: Int) = daily.filter { it.getInt("type") == 0x56 }.sumOf { it.getJSONArray("v").getLong(index) }
        fun latest(types: Set<Int>): Int? = daily.filter { it.getInt("type") in types }.maxByOrNull { it.getLong("t") }?.getJSONArray("v")?.getInt(0)
        val sleep = rows.values.filter { it.getInt("type") == 0x58 && day(it.getJSONArray("v").getLong(0)) == today }
            .sumOf { val v=it.getJSONArray("v"); v.getLong(1)+v.getLong(2)+v.getLong(3) } / 60
        _summary.value = WatchHealthSummary(sum(0),sum(1),sum(2)/1000,latest(setOf(0x53,0xe0)),latest(setOf(0x55)),latest(setOf(0x9d)),sleep,rows.size,prefs.getLong("sync_$address",0))
    }
}

/** Only holds an active, explicitly authenticated watch session. Never queues notification content. */
object WatchNotificationBridge {
    @Volatile var send: ((String,String,String) -> Unit)? = null
}
