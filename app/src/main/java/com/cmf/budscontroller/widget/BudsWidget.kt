package com.cmf.budscontroller.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.cmf.budscontroller.R
import com.cmf.budscontroller.bluetooth.CmfBluetoothManager
import com.cmf.budscontroller.protocol.AncMode
import com.cmf.budscontroller.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BatteryWidget : BudsWidget()
class AncWidget : BudsWidget()
class GameWidget : BudsWidget()
class BassWidget : BudsWidget()
class TransparencyWidget : BudsWidget()
class ConnectionWidget : BudsWidget()

open class BudsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAll(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = updateAll(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in actions) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(6000) {
                    commandMutex.withLock {
                        val manager = CmfBluetoothManager.getInstance()
                        val status = manager.deviceStatus.value
                        if (status.isConnected) when (action) {
                            ANC -> manager.setAnc(when (status.ancMode) {
                                AncMode.OFF -> AncMode.TRANSPARENCY
                                AncMode.TRANSPARENCY -> AncMode.NOISE_CANCELLATION
                                AncMode.NOISE_CANCELLATION -> AncMode.OFF
                            }, status.ancLevel)
                            GAME -> manager.setGameMode(!status.isGameModeEnabled)
                            BASS -> manager.setUltraBass((status.ultraBassLevel + 1) % 6)
                            TRANSPARENCY -> manager.setAnc(if (status.ancMode == AncMode.TRANSPARENCY) AncMode.OFF else AncMode.TRANSPARENCY, status.ancLevel)
                            REFRESH -> manager.refreshStatus()
                        }
                        updateAll(context)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("BudsWidget", "Widget command failed", e)
            } finally { pending.finish() }
        }
    }

    companion object {
        private const val ANC = "com.cmf.budscontroller.widget.ANC"
        private const val GAME = "com.cmf.budscontroller.widget.GAME"
        private const val BASS = "com.cmf.budscontroller.widget.BASS"
        private const val TRANSPARENCY = "com.cmf.budscontroller.widget.TRANSPARENCY"
        private const val REFRESH = "com.cmf.budscontroller.widget.REFRESH"
        private val actions = setOf(ANC, GAME, BASS, TRANSPARENCY, REFRESH)
        private val commandMutex = Mutex()
        private val providers = listOf(BudsWidget::class.java, BatteryWidget::class.java, AncWidget::class.java,
            GameWidget::class.java, BassWidget::class.java, TransparencyWidget::class.java, ConnectionWidget::class.java)

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val settings = context.getSharedPreferences("cmf_widgets", Context.MODE_PRIVATE)
            val light = settings.getBoolean("light", false)
            val accent = settings.getInt("accent", 0xFFFF5555.toInt())
            val foreground = if (light) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            val status = CmfBluetoothManager.getInstance().deviceStatus.value
            val ru = when (context.getSharedPreferences("cmf_prefs", Context.MODE_PRIVATE).getString("app_language", "auto")) {
                "ru" -> true
                "en" -> false
                else -> java.util.Locale.getDefault().language == "ru"
            }
            fun text(russian: String, english: String) = if (ru) russian else english
            fun battery(value: Int) = if (status.isConnected && value in 0..100) "$value%" else "—"
            val mode = when (status.ancMode) {
                AncMode.OFF -> text("Выкл", "Off")
                AncMode.TRANSPARENCY -> text("Прозрачность", "Transparency")
                AncMode.NOISE_CANCELLATION -> text("Шумоподавление", "Noise cancelling")
            }
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            fun command(action: String): PendingIntent = if (!status.isConnected) open else PendingIntent.getBroadcast(
                context, action.hashCode(), Intent(context, BudsWidget::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            providers.forEach { provider ->
                val ids = manager.getAppWidgetIds(ComponentName(context, provider))
                if (ids.isEmpty()) return@forEach
                val tile = provider != BudsWidget::class.java && provider != BatteryWidget::class.java
                val batteryOnly = provider == BatteryWidget::class.java
                val views = RemoteViews(context.packageName, when {
                    tile -> R.layout.buds_widget_tile
                    batteryOnly -> R.layout.buds_widget_battery
                    else -> R.layout.buds_widget
                })
                views.setInt(R.id.widget_root, "setBackgroundResource", if (light) R.drawable.widget_background_light else R.drawable.widget_background)
                views.setTextColor(R.id.widget_title, accent)
                views.setTextColor(R.id.widget_status, if (light) 0xFF555555.toInt() else 0xFFAAAAAA.toInt())
                val connectedText = if (status.isConnected) text("Подключено", "Connected") else text("Открыть для подключения", "Tap to connect")
                views.setOnClickPendingIntent(R.id.widget_root, open)
                if (tile) {
                    val (title, value, action) = when (provider) {
                        AncWidget::class.java -> Triple("ANC", mode, ANC)
                        GameWidget::class.java -> Triple(text("ИГРОВОЙ РЕЖИМ", "GAME MODE"), if (status.isGameModeEnabled) text("ВКЛ", "ON") else text("ВЫКЛ", "OFF"), GAME)
                        BassWidget::class.java -> Triple("ULTRA BASS", "${status.ultraBassLevel} / 5", BASS)
                        TransparencyWidget::class.java -> Triple(text("ПРОЗРАЧНОСТЬ", "TRANSPARENCY"), if (status.ancMode == AncMode.TRANSPARENCY) text("ВКЛ", "ON") else text("ВЫКЛ", "OFF"), TRANSPARENCY)
                        else -> Triple("CMF BUDS", if (status.isConnected) text("В СЕТИ", "ONLINE") else text("НЕ В СЕТИ", "OFFLINE"), REFRESH)
                    }
                    views.setTextViewText(R.id.widget_title, title)
                    views.setTextViewText(R.id.widget_value, if (status.isConnected || provider == ConnectionWidget::class.java) value else "—")
                    views.setTextViewText(R.id.widget_status, connectedText)
                    views.setOnClickPendingIntent(R.id.widget_root, command(action))
                    views.setTextColor(R.id.widget_value, if (status.isConnected) foreground else android.graphics.Color.GRAY)
                } else {
                    views.setTextViewText(R.id.widget_status, connectedText)
                    views.setTextViewText(R.id.widget_battery, "L ${battery(status.battery.leftPercent)}   ·   ${text("КЕЙС", "CASE")} ${battery(status.battery.casePercent)}   ·   R ${battery(status.battery.rightPercent)}")
                    views.setTextColor(R.id.widget_battery, foreground)
                    if (!batteryOnly) {
                    listOf(R.id.widget_anc, R.id.widget_game, R.id.widget_bass).forEach { button ->
                        views.setInt(button, "setBackgroundResource", if (light) R.drawable.widget_button_light else R.drawable.widget_button)
                        views.setTextColor(button, foreground)
                    }
                    views.setTextColor(R.id.widget_anc, accent)
                    views.setTextViewText(R.id.widget_anc, if (status.isConnected) "ANC · $mode" else "ANC · —")
                    views.setTextViewText(R.id.widget_game, text("Игра", "Game") + if (status.isConnected) " · ${if (status.isGameModeEnabled) "ON" else "OFF"}" else " · —")
                    views.setTextViewText(R.id.widget_bass, if (status.isConnected) "BASS · ${status.ultraBassLevel}/5" else "BASS · —")
                    views.setOnClickPendingIntent(R.id.widget_anc, command(ANC))
                    views.setOnClickPendingIntent(R.id.widget_game, command(GAME))
                    views.setOnClickPendingIntent(R.id.widget_bass, command(BASS))
                    when (settings.getString("third", "bass")) {
                        "transparency" -> {
                            views.setTextViewText(R.id.widget_bass, text("Прозрачность", "Transparency"))
                            views.setOnClickPendingIntent(R.id.widget_bass, command(TRANSPARENCY))
                        }
                        "refresh" -> {
                            views.setTextViewText(R.id.widget_bass, text("Обновить", "Refresh"))
                            views.setOnClickPendingIntent(R.id.widget_bass, command(REFRESH))
                        }
                    }
                    }
                }
                manager.updateAppWidget(ids, views)
            }
        }
    }
}
