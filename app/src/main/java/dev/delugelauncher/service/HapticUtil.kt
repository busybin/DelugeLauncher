// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.service

import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import dev.delugelauncher.data.HapticFeedback

object HapticUtil {
    // Coalesces a scrub storm: crossing a letter per frame would queue dozens of binder
    // round trips a second. Deliberate taps are far slower than this floor, so they feel
    // exactly the same; only a fast slide stops buzzing per letter.
    private const val MIN_TICK_INTERVAL_MS = 40L
    private var lastTickMs = 0L

    /**
     * A short tap back as the finger crosses into a new letter on the A-Z strip.
     *
     * All three strengths are raw one-shots with rising duration and amplitude, so the
     * order is guaranteed by hardware instead of by whatever each device maps the
     * feedback constants to (on some phones the "soft" tick hits harder than a raw
     * buzz — which is exactly how light and strong got swapped). Explicit choice wins
     * over the system toggle here: this setting exists to pick a strength.
     */
    fun tick(view: View, strength: HapticFeedback) {
        val (millis, amplitude) = when (strength) {
            HapticFeedback.OFF -> return
            HapticFeedback.LIGHT -> 15L to 90
            HapticFeedback.MEDIUM -> 25L to 140
            HapticFeedback.STRONG -> 40L to 220
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastTickMs < MIN_TICK_INTERVAL_MS) return
        lastTickMs = now
        val vibrator = runCatching {
            view.context.getSystemService(Vibrator::class.java)
        }.getOrNull() ?: return
        runCatching {
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(millis, amplitude))
            }
        }
    }
}