package com.tfg.motioncontroller.data.local

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VibrationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun hasVibrator(): Boolean = vibrator?.hasVibrator() ?: false

    fun vibrate(pattern: LongArray, repeat: Int = -1) {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, repeat)
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, repeat)
            }
        }
    }

    fun vibrateClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            vibrate(longArrayOf(0, 50))
        }
    }

    fun vibrateHeavyClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            vibrate(longArrayOf(0, 100))
        }
    }

    fun vibrateDoubleClick() {
        vibrate(longArrayOf(0, 100, 50, 100))
    }

    fun vibrateLong(durationMs: Long = 5000) {
        vibrate(longArrayOf(0, durationMs))
    }

    fun vibrateSOS() {
        vibrate(longArrayOf(
            0, 100, 100, 100, 100, 100,  // S (tres cortos)
            100, 300, 100, 300, 100, 300, // O (tres largos)
            100, 100, 100, 100, 100, 100  // S (tres cortos)
        ))
    }

    fun cancel() {
        vibrator?.cancel()
    }

    companion object {
        // Patrones predefinidos (ms)
        val PATTERN_SHORT = longArrayOf(0, 200)
        val PATTERN_DOUBLE = longArrayOf(0, 100, 50, 100)
        val PATTERN_LONG = longArrayOf(0, 5000)
        val PATTERN_BLOW = longArrayOf(0, 2000)
    }
}
