// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale
import android.os.Process
import android.os.UserManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppRepository(
    private val context: Context,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {

    private val pm: PackageManager get() = context.packageManager
    private val launcherApps: LauncherApps
        get() = context.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager
        get() = context.getSystemService(UserManager::class.java)

    /**
     * Every launchable activity across every profile the launcher can see.
     *
     * LauncherApps rather than PackageManager, because queryIntentActivities only ever sees
     * the profile we are running in — a work profile or a private space is invisible to it.
     * LauncherApps also hands back the badged icon and the per-profile label, which is what
     * marks a work app as a work app.
     *
     * A locked private space simply drops out of getUserProfiles, so its apps disappear from
     * the list until it is unlocked. That is the intended behavior, not a failure to handle.
     */
    fun queryAllApps(): List<AppInfo> {
        val profiles = runCatching { userManager.userProfiles }.getOrNull().orEmpty()
        return profiles
            .flatMap { user ->
                val serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrDefault(0L)
                // Asking about a profile we are not the launcher for throws rather than
                // returning nothing, and one inaccessible profile must not lose the rest.
                runCatching { launcherApps.getActivityList(null, user) }
                    .getOrNull()
                    .orEmpty()
                    .map { info ->
                        AppInfo(
                            componentName = info.componentName,
                            label = info.label?.toString() ?: info.componentName.packageName,
                            user = user,
                            userSerial = serial,
                        )
                    }
            }
            // Launching ourselves through the MAIN+LAUNCHER filter starts a task that isn't
            // rooted at HOME: it shows up in the app switcher and leaves the system unsure
            // which task is home until the default launcher is set again. Nothing good comes
            // of listing the launcher inside its own app list.
            .filterNot { it.componentName.packageName == context.packageName }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
    }

    /** Badged by the system, so a work or private-space app is recognizable at a glance. */
    fun loadIcon(app: AppInfo): Drawable {
        val user = app.user
        if (user != null) {
            val activity = runCatching {
                launcherApps.getActivityList(app.componentName.packageName, user)
                    .firstOrNull { it.componentName == app.componentName }
            }.getOrNull()
            activity?.let { info ->
                runCatching { info.getBadgedIcon(0) }.getOrNull()?.let { return it }
            }
        }
        return try {
            pm.getActivityIcon(app.componentName)
        } catch (e: PackageManager.NameNotFoundException) {
            try {
                pm.getApplicationIcon(app.componentName.packageName)
            } catch (e2: PackageManager.NameNotFoundException) {
                pm.defaultActivityIcon
            }
        }
    }

    // An app's label comes back in the device's language, so on a Japanese phone the English
    // name is nowhere in the list. Loading it means asking the app's own resources for the
    // label a second time through an English configuration, which is a whole resource table
    // per app — so it is only ever asked for a name that has no A-Z letter of its own, and the
    // answer is kept.
    private val englishLabels = mutableMapOf<String, String?>()

    /** The app's name in English, or null if it has none or it is the name we already have. */
    fun englishLabel(app: AppInfo): String? = englishLabels.getOrPut(app.key) {
        runCatching {
            val info = pm.getActivityInfo(app.componentName, 0)
            val labelRes = if (info.labelRes != 0) info.labelRes else info.applicationInfo.labelRes
            if (labelRes == 0) return@runCatching null
            val res = pm.getResourcesForApplication(info.applicationInfo)
            val config = Configuration(res.configuration).apply { setLocale(Locale.ENGLISH) }
            res.getString(labelRes).takeIf { it.isNotBlank() }?.let { english ->
                // The context-adjusted resources fall back to the default language when an app
                // ships no English, which just hands the same name back.
                Resources(res.assets, res.displayMetrics, config).getString(labelRes)
            }
        }.getOrNull()
    }

    /** Returns false if the app could not be started, so callers can undo whatever they hid. */
    fun launch(app: AppInfo): Boolean {
        if (app.componentName.packageName == context.packageName) return false
        val started = runCatching {
            // Through LauncherApps so an app in another profile starts as that profile; a
            // plain startActivity would look for it in ours and find nothing.
            launcherApps.startMainActivity(app.componentName, app.user ?: Process.myUserHandle(), null, null)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(app.componentName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }.getOrDefault(false)
        }
        if (started) {
            // Counted whether or not the usage sort is on, so switching it on later has a
            // history to order by instead of starting from nothing.
            scope.launch { prefs.incrementLaunchCount(app.key) }
        }
        return started
    }

    fun openAppInfo(app: AppInfo) {
        val user = app.user
        if (user != null) {
            val shown = runCatching {
                launcherApps.startAppDetailsActivity(app.componentName, user, null, null)
                true
            }.getOrDefault(false)
            if (shown) return
        }
        openAppInfo(app.componentName.packageName)
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
