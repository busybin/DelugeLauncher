// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import dev.delugelauncher.service.DelugeDeviceAdminReceiver

/** Which policy call a freeze method turns into. */
enum class PolicyOperation { SUSPEND, HIDE }

/**
 * The device-owner API has no equivalent for disabling a package or force-stopping it, so
 * those two keep going through a shell.
 */
object DeviceOwnerMethods {
    fun operation(method: FreezeMethod): PolicyOperation? = when (method) {
        FreezeMethod.SUSPEND -> PolicyOperation.SUSPEND
        FreezeMethod.HIDE -> PolicyOperation.HIDE
        FreezeMethod.DISABLE, FreezeMethod.STOP -> null
    }
}

/**
 * Freezing through the device owner: platform policy calls instead of a root or adb shell.
 *
 * This is the only channel that survives without root, Shizuku or a computer once it is set
 * up, but it is also the narrowest: it can suspend and hide packages in the profile it owns.
 * A second profile still needs a shell, and nothing here can disable or force-stop an app.
 */
class DeviceOwnerPolicy(context: Context) {

    private val appContext = context.applicationContext
    private val manager: DevicePolicyManager? =
        appContext.getSystemService(DevicePolicyManager::class.java)
    private val admin: ComponentName = DelugeDeviceAdminReceiver.component(appContext)

    /** The admin component is enabled, whether or not the phone also made it the owner. */
    val adminActive: Boolean
        get() = runCatching { manager?.isAdminActive(admin) == true }.getOrDefault(false)

    /** The phone is actually run by this app, so the package policy calls are allowed. */
    val active: Boolean
        get() = adminActive && runCatching {
            manager?.isDeviceOwnerApp(appContext.packageName) == true
        }.getOrDefault(false)

    /** A device owner only manages the profile its process runs in. */
    fun handles(userId: Int): Boolean =
        active && userId == Process.myUid() / PER_USER_RANGE

    /**
     * `true` when the call was made, `false` when the platform refused it, `null` when this
     * channel cannot handle the method or the profile at all and the caller should fall
     * through to root or Shizuku.
     */
    fun freeze(packageName: String, method: FreezeMethod, userId: Int): Boolean? {
        val operation = DeviceOwnerMethods.operation(method) ?: return null
        if (!handles(userId) || !FreezeCommands.isPackageName(packageName)) return null
        return runCatching {
            val dpm = manager ?: return@runCatching false
            when (operation) {
                PolicyOperation.SUSPEND ->
                    dpm.setPackagesSuspended(admin, arrayOf(packageName), true).isEmpty()
                PolicyOperation.HIDE -> dpm.setApplicationHidden(admin, packageName, true)
            }
        }.getOrDefault(false)
    }

    /**
     * Lifts both states this channel can create. Asking for the wrong one is harmless, and
     * checking first keeps the number of binder calls down on a whole-list run.
     */
    fun unfreeze(packageName: String, userId: Int): Boolean? {
        if (!handles(userId) || !FreezeCommands.isPackageName(packageName)) return null
        return runCatching {
            val dpm = manager ?: return@runCatching false
            if (dpm.isPackageSuspended(admin, packageName)) {
                dpm.setPackagesSuspended(admin, arrayOf(packageName), false)
            }
            if (dpm.isApplicationHidden(admin, packageName)) {
                dpm.setApplicationHidden(admin, packageName, false)
            }
            true
        }.getOrDefault(false)
    }

    /** Locking needs only an active admin, not the whole device owner. */
    fun lockNow(): Boolean = runCatching {
        val dpm = manager ?: return@runCatching false
        if (!adminActive) return@runCatching false
        dpm.lockNow()
        true
    }.getOrDefault(false)

    /**
     * Gives the phone back. The caller lifts the freeze list first: policy state normally goes
     * with the admin, but an app must not be left hidden because a removal was interrupted.
     */
    fun removeDeviceOwner(): Boolean = runCatching {
        val dpm = manager ?: return@runCatching false
        if (!active) return@runCatching false
        dpm.clearDeviceOwnerApp(appContext.packageName)
        true
    }.getOrDefault(false)

    /** The one command that turns this install into the phone's device owner. */
    fun provisioningCommand(): String =
        "adb shell dpm set-device-owner ${admin.flattenToShortString()}"

    private companion object {
        /** UIDs are userId * this + appId; the SDK keeps the real constant hidden. */
        const val PER_USER_RANGE = 100_000
    }
}
