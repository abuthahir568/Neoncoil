package com.example.services

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticService(context: Context?) {
    @Volatile var enabled: Boolean = true

    private val vibrator: Vibrator? = try {
        if (context == null) null
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Throwable) {
        null
    }

    fun lightTick() = pulse(14L, 65)
    fun mediumImpact() = pulse(32L, 150)
    fun heavyCrash() = pulse(85L, 255)

    private fun pulse(durationMs: Long, amplitude: Int) {
        if (!enabled) return
        try {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        } catch (_: Throwable) {
            // Safe ignore if hardware lacks vibrator
        }
    }
}
