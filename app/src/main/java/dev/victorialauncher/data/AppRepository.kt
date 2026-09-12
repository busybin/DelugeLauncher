// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings

class AppRepository(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    fun queryAllApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        return resolveInfos
            .mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                AppInfo(
                    componentName = ComponentName(ai.packageName, ai.name),
                    label = ri.loadLabel(pm)?.toString() ?: ai.packageName,
                )
            }
            // Launching ourselves through the MAIN+LAUNCHER filter starts a task that isn't
            // rooted at HOME: it shows up in the app switcher and leaves the system unsure
            // which task is home until the default launcher is set again. Nothing good comes
            // of listing the launcher inside its own app list.
            .filterNot { it.componentName.packageName == context.packageName }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
    }

    fun loadIcon(componentName: ComponentName): Drawable {
        return try {
            pm.getActivityIcon(componentName)
        } catch (e: PackageManager.NameNotFoundException) {
            try {
                pm.getApplicationIcon(componentName.packageName)
            } catch (e2: PackageManager.NameNotFoundException) {
                pm.defaultActivityIcon
            }
        }
    }

    /** Returns false if the app could not be started, so callers can undo whatever they hid. */
    fun launch(componentName: ComponentName): Boolean {
        if (componentName.packageName == context.packageName) return false
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(componentName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // App may have been uninstalled since the list was built; ignore.
            false
        }
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}