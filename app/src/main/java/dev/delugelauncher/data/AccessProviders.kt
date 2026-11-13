// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.content.Context
import android.content.pm.PackageManager

/**
 * An installed app that Deluge gets its freeze power from.
 *
 * Uninstalling one of these is not an ordinary uninstall: the freeze list may be left with
 * apps held by a channel that no longer exists, and nothing on the phone can lift them.
 */
enum class AccessProvider {
    /** Shizuku or an API-compatible manager. */
    SHIZUKU,
    /** A root manager: Magisk, KernelSU, APatch and their forks. */
    ROOT_MANAGER,
}

/**
 * Recognises the apps whose removal takes a freeze channel away.
 *
 * The Shizuku family is found by contract, not by package name: a manager must define the
 * API permission, so a future fork needs no change here. The names below are the fallback
 * for ROMs where a custom permission is not visible to another app.
 */
object AccessProviders {

    private val SHIZUKU = setOf(
        "moe.shizuku.privileged.api",
        "com.hamondev.shevery",
    )

    private val ROOT = setOf(
        "com.topjohnwu.magisk",
        "io.github.huskydg.magisk",
        "me.weishu.kernelsu",
        "com.rifsxd.ksunext",
        "me.bmax.apatch",
        "eu.chainfire.supersu",
    )

    /** The answer from names alone, which is what tests can check. */
    fun known(packageName: String): AccessProvider? = when (packageName) {
        in SHIZUKU -> AccessProvider.SHIZUKU
        in ROOT -> AccessProvider.ROOT_MANAGER
        else -> null
    }

    /** The answer for this device, adding a manager that only declares the API permission. */
    fun classify(context: Context, packageName: String): AccessProvider? {
        known(packageName)?.let { return it }
        val declared = runCatching {
            context.packageManager.getPermissionInfo(SHIZUKU_PERMISSION, 0).packageName
        }.getOrNull()
        return if (declared == packageName) AccessProvider.SHIZUKU else null
    }

    private const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"
}
