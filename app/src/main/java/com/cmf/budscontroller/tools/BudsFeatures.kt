package com.cmf.budscontroller.tools

import android.content.Context
import com.cmf.budscontroller.protocol.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

 data class BatterySample(val time: Long, val left: Int, val right: Int, val case: Int)
 data class ListeningProfile(val id: String, val mode: AncMode, val bass: Int, val game: Boolean, val bands: List<Int>)

class BudsFeatures(context: Context) {
    private val prefs = context.getSharedPreferences("buds_features", Context.MODE_PRIVATE)
    private val _history = MutableStateFlow(loadHistory())
    val history = _history.asStateFlow()
    private fun loadHistory(): List<BatterySample> = runCatching {
        val array = JSONArray(prefs.getString("history", "[]"))
        (0 until array.length()).map { val row = array.getJSONObject(it)
            BatterySample(row.getLong("t"), row.getInt("l"), row.getInt("r"), row.getInt("c")) }
    }.getOrDefault(emptyList())

    @Synchronized fun record(status: DeviceStatus) {
        if (!status.isConnected) return
        val b = status.battery
        if (b.leftPercent !in 0..100 && b.rightPercent !in 0..100) return
        val now = System.currentTimeMillis()
        val last = _history.value.lastOrNull()
        if (last != null && now - last.time < 300_000 && last.left == b.leftPercent && last.right == b.rightPercent && last.case == b.casePercent) return
        val rows = (_history.value + BatterySample(now, b.leftPercent, b.rightPercent, b.casePercent))
            .filter { now - it.time < 7 * 86_400_000L }.takeLast(1000)
        _history.value = rows
        val json = JSONArray()
        rows.forEach { json.put(JSONObject().put("t", it.time).put("l", it.left).put("r", it.right).put("c", it.case)) }
        prefs.edit().putString("history", json.toString()).apply()
    }
    fun profile(id: String): ListeningProfile {
        val default = when (id) {
            "metro" -> ListeningProfile(id, AncMode.NOISE_CANCELLATION, 3, false, listOf(3, 1, 0, 1, 2))
            "work" -> ListeningProfile(id, AncMode.TRANSPARENCY, 1, false, listOf(-2, 0, 3, 2, 0))
            "games" -> ListeningProfile(id, AncMode.NOISE_CANCELLATION, 2, true, listOf(2, 0, 1, 3, 1))
            else -> ListeningProfile(id, AncMode.OFF, 0, false, listOf(-3, 0, 2, 1, -3))
        }
        return runCatching {
            val json = JSONObject(prefs.getString("profile_$id", null) ?: return default)
            default.copy(mode = AncMode.valueOf(json.getString("mode")), bass = json.getInt("bass").coerceIn(0,5), game = json.getBoolean("game"), bands = (0..4).map { json.getJSONArray("bands").getInt(it).coerceIn(-10,10) })
        }.getOrDefault(default)
    }
    fun saveProfile(id: String, status: DeviceStatus, bands: List<Int>) {
        prefs.edit().putString("profile_$id", JSONObject().put("mode", status.ancMode.name).put("bass",status.ultraBassLevel).put("game",status.isGameModeEnabled).put("bands",JSONArray(bands)).toString()).apply()
    }
}
