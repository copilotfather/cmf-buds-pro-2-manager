package com.cmf.budscontroller.ui.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * High-precision tactile haptic feedback engine for sliders, dials, and studio controls.
 */
class CmfHapticManager(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Subtle mechanical tick when rotating dials or sliding equalizer steps.
     */
    fun tick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(8, 40))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(8)
            }
        } catch (_: Exception) {}
    }

    /**
     * Crisp click when toggling switches or selecting buttons.
     */
    fun click() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(16, 90))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(16)
            }
        } catch (_: Exception) {}
    }

    /**
     * Heavy tactile snap when reaching limits or confirming actions.
     */
    fun heavyClick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(24, 180))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(24)
            }
        } catch (_: Exception) {}
    }
}
