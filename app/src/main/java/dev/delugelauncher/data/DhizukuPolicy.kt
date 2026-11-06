// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener

/**
 * Freezing through Dhizuku: another app holds device ownership and delegates package access
 * to this one.
 *
 * The delegation is the whole mechanism. `setPackagesSuspended` and `setApplicationHidden`
 * accept a null admin from any package that holds DELEGATION_PACKAGE_ACCESS, so the system
 * grants exactly those two calls and nothing else — no hidden API, no Xposed module, and no
 * second device owner. A Dhizuku owner cannot lock the screen or uninstall an app for one
 * profile, so this channel offers suspend and hide only.
 */
class DhizukuPolicy(context: Context) {

    private val appContext = context.applicationContext
    private val manager: DevicePolicyManager? =
        appContext.getSystemService(DevicePolicyManager::class.java)

    /** The Dhizuku app is installed, running and answering. */
    val installed: Boolean
        get() = connected()

    /** This app may talk to Dhizuku at all; the user grants that in Dhizuku's own dialog. */
    val permitted: Boolean
        get() = installed && runCatching { Dhizuku.isPermissionGranted() }.getOrDefault(false)

    /** The owner gave this app the package-access scope, which is what makes the calls work. */
    val delegated: Boolean
        get() = permitted && runCatching {
            Dhizuku.getDelegatedScopes().contains(DevicePolicyManager.DELEGATION_PACKAGE_ACCESS)
        }.getOrDefault(false)

    /** Suspend and hide both answer through the system, so this is a usable channel. */
    val active: Boolean
        get() = delegated

    /**
     * Asks Dhizuku for the scope once. Harmless when it is already there, and the only write
     * this channel makes through Dhizuku besides the freeze calls themselves.
     */
    fun ensureDelegation(): Boolean {
        if (!permitted) return false
        if (delegated) return true
        return runCatching {
            val scopes = Dhizuku.getDelegatedScopes().toMutableSet()
            scopes += DevicePolicyManager.DELEGATION_PACKAGE_ACCESS
            Dhizuku.setDelegatedScopes(scopes.toTypedArray())
            true
        }.getOrDefault(false)
    }

    /** Dhizuku's permission dialog; its listener answers on the main thread. */
    fun requestPermission(onResult: (Boolean) -> Unit) {
        if (!installed) {
            onResult(false)
            return
        }
        val handed = runCatching {
            Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
                override fun onRequestPermission(grantResult: Int) {
                    val granted = grantResult == PackageManager.PERMISSION_GRANTED
                    onResult(granted && ensureDelegation())
                }
            })
        }
        if (handed.isFailure) onResult(false)
    }

    /** Opens Dhizuku itself, where the user turns it on or changes what it shares. */
    fun open(): Boolean = runCatching {
        val intent = appContext.packageManager.getLaunchIntentForPackage(DHIZUKU_PACKAGE)
            ?: return false
        appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    /**
     * Same shape as [DeviceOwnerPolicy.freeze]: `true` when the system took the call, `false`
     * when it refused, `null` when this channel cannot handle the method or the profile.
     */
    fun freeze(packageName: String, method: FreezeMethod, userId: Int): Boolean? {
        val operation = DeviceOwnerMethods.operation(method) ?: return null
        if (!active || !handles(userId) || !FreezeCommands.isPackageName(packageName)) return null
        return runCatching {
            val dpm = manager ?: return@runCatching false
            when (operation) {
                // No admin component is named: the system checks the delegation instead.
                PolicyOperation.SUSPEND ->
                    dpm.setPackagesSuspended(null, arrayOf(packageName), true).isEmpty()
                PolicyOperation.HIDE -> dpm.setApplicationHidden(null, packageName, true)
            }
        }.getOrDefault(false)
    }

    /** Clears both states this channel can create; each call is harmless when unset. */
    fun unfreeze(packageName: String, userId: Int): Boolean? {
        if (!active || !handles(userId) || !FreezeCommands.isPackageName(packageName)) return null
        return runCatching {
            val dpm = manager ?: return@runCatching false
            dpm.setPackagesSuspended(null, arrayOf(packageName), false)
            dpm.setApplicationHidden(null, packageName, false)
            true
        }.getOrDefault(false)
    }

    /** A Dhizuku owner manages the profile this process runs in, like the owner channel. */
    private fun handles(userId: Int): Boolean = userId == Process.myUid() / PER_USER_RANGE

    /** `Dhizuku.init` reaches another app; the answer holds for this process. */
    private fun connected(): Boolean = initAnswer ?: runCatching {
        Dhizuku.init(appContext)
    }.getOrDefault(false).also { initAnswer = it }

    /** Re-asks after the user installed or started Dhizuku. */
    fun refresh() {
        initAnswer = null
    }

    private companion object {
        const val DHIZUKU_PACKAGE = "com.rosan.dhizuku"
        /** UIDs are userId * this + appId; the SDK keeps the real constant hidden. */
        const val PER_USER_RANGE = 100_000

        @Volatile
        private var initAnswer: Boolean? = null
    }
}
