// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import dev.delugelauncher.BuildConfig
import dev.delugelauncher.R

object SystemUi {
    /** Set once reflection has actually opened the shade on this device; see below. */
    @Volatile
    private var reflectionWorked = false

    /**
     * Pulls down the notification shade.
     *
     * There is no public API for this. StatusBarManager.expandNotificationsPanel is a hidden
     * method, and whether reflection can still reach it is entirely up to the build: stock
     * Android blocks it, while a number of OEM and custom ROMs either leave it off the
     * restricted list or exempt whichever app currently holds the home role. That is the whole
     * explanation for it working on one phone and not the next — same app, same permission,
     * different ROM policy. The accessibility action is the only sanctioned route, so it is
     * tried first and reflection is the fallback rather than the other way round.
     *
     * @return true if the shade was actually opened.
     */
    fun expandNotificationShade(context: Context): Boolean {
        if (DelugeAccessibilityService.openNotificationShade()) return true

        return runCatching {
            val service = context.getSystemService("statusbar")
            val method = Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
            method.invoke(service)
            reflectionWorked = true
            true
        }.getOrDefault(false)
    }

    /**
     * Whether the gesture has a way through; drives the prompt in settings. Reflection counts
     * once it has been seen to work, so a device where the pull-down already opens the shade
     * stops being told to turn on an accessibility service it does not need.
     */
    fun canExpandShade(): Boolean = DelugeAccessibilityService.isConnected || reflectionWorked

    /** Locking has no fallback: the accessibility action is the only way to do it at all. */
    fun canLockScreen(): Boolean = DelugeAccessibilityService.isConnected

    fun lockScreen(): Boolean = DelugeAccessibilityService.lockScreen()

    /**
     * Heals the accessibility binding after an update, and nags once when the grant itself
     * is gone.
     *
     * Two different things break across an install, and only one needs the user. Some
     * builds keep the grant but never rebind the dead service — the toggle reads on and
     * nothing works, which is exactly what "re-grant after every update" feels like.
     * Cycling our own component forces the system to rebind, grant untouched. Only when
     * the grant is truly gone does this say so, and only once per version: someone who
     * switched it off on purpose does not get nagged every launch.
     *
     * Read from Settings.Secure, which any app may read; the grant itself can only ever
     * be given by hand, so this never pretends otherwise.
     */
    fun refreshAccessibilityBinding(context: Context) {
        if (DelugeAccessibilityService.isConnected) {
            markAccessibilitySeen(context)
            return
        }
        val granted = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )?.split(':')?.any { entry ->
                entry.equals(
                    ComponentName(context, DelugeAccessibilityService::class.java)
                        .flattenToShortString(),
                    ignoreCase = true,
                )
            } == true
        }.getOrDefault(false)
        if (granted) {
            // Grant alive, binding dead: bounce our own component (no kill) and the
            // system binds it again, touching nothing else.
            runCatching {
                val pm = context.packageManager
                val component = ComponentName(context, DelugeAccessibilityService::class.java)
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            return
        }
        // Truly gone: one toast per version, and only if it was ever on — a fresh
        // install that never had it is not a loss to report.
        runCatching {
            val seen = context.getSharedPreferences(A11Y_SEEN_FILE, Context.MODE_PRIVATE)
            if (!seen.getBoolean(A11Y_SEEN_KEY, false)) return@runCatching
            if (seen.getInt(A11Y_NUDGED_KEY, 0) >= BuildConfig.VERSION_CODE) return@runCatching
            seen.edit().putInt(A11Y_NUDGED_KEY, BuildConfig.VERSION_CODE).apply()
            Toast.makeText(
                context,
                context.getString(R.string.settings_lock_setup),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun markAccessibilitySeen(context: Context) {
        runCatching {
            context.getSharedPreferences(A11Y_SEEN_FILE, Context.MODE_PRIVATE)
                .edit().putBoolean(A11Y_SEEN_KEY, true).apply()
        }
    }

    private const val A11Y_SEEN_FILE = "deluge_a11y_seen"
    private const val A11Y_SEEN_KEY = "seen"
    private const val A11Y_NUDGED_KEY = "nudged_for"
}