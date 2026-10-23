// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.delugelauncher.R
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Freeze operations as direct binder calls through Shizuku.
 *
 * The shell path forks one `pm` process per command, and freezing a suspended app is four
 * commands — the process start is most of the wait. These calls are one IPC each, which is
 * what makes a whole-list run take a fraction of a second instead of several.
 *
 * SUSPEND records the caller as the suspender, and the name here is the one `pm` itself
 * uses, so an app frozen this way can still be lifted by either path afterwards.
 *
 * Each call answers null when the binder path could not be used at all (no Shizuku, a dead
 * binder, or a ROM that refuses the call), so the caller can fall back to the shell.
 */
class FreezeBinder(private val context: Context) {

    private var proxy: Proxy? = null

    fun available(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun freeze(packageName: String, method: FreezeMethod): Boolean? =
        attempt { it.freeze(packageName, method) }

    /**
     * Re-applies the pause with the dialog on an app paused before the dialog existed.
     * Called once per app by the upgrade pass; a pause by the same suspender replaces the
     * stored parameters, so this is what gives old records the unpause button.
     */
    fun refreshSuspend(packageName: String): Boolean? =
        attempt { it.setSuspended(packageName, suspended = true) }

    /** Lifts every kind of freeze, because the stored method can be wrong. */
    fun unfreeze(packageName: String): Boolean? = attempt { proxy ->
        proxy.setSuspended(packageName, false)
        proxy.setEnabled(packageName, true)
        runCatching { proxy.setHidden(packageName, false) }
        runCatching { proxy.setBackgroundRestricted(packageName, false) }
        true
    }

    private fun <T> attempt(block: (Proxy) -> T): T? = try {
        block(proxy ?: Proxy().also { proxy = it })
    } catch (t: Throwable) {
        // Shizuku restarted or the ROM refused a hidden call: forget the proxies so the next
        // attempt starts clean, and let the caller run the command path instead.
        proxy = null
        null
    }

    /**
     * The dialog the system shows when something tries to open a paused app.
     *
     * Without one the system offers only "More details", so a launch from another app dead-ends
     * with a message naming root as the manager and no way out. The unpause button lifts the
     * pause and continues the launch.
     */
    private val suspendDialog: Any? by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@lazy null
        runCatching {
            val builderClass = Class.forName("android.content.pm.SuspendDialogInfo\$Builder")
            val builder = HiddenApiBypass.newInstance(builderClass)
            HiddenApiBypass.invoke(
                builderClass, builder, "setMessage",
                context.getString(R.string.freeze_suspend_dialog_message),
            )
            HiddenApiBypass.invoke(
                builderClass, builder, "setNeutralButtonText",
                context.getString(R.string.freeze_suspend_dialog_button),
            )
            HiddenApiBypass.invoke(builderClass, builder, "setNeutralButtonAction", UNPAUSE_ACTION)
            HiddenApiBypass.invoke(builderClass, builder, "build")
        }.getOrNull()
    }

    private inner class Proxy {

        // The process's own uid carries its user: plid/multiuser offsets are 100000 apart.
        private val userId = android.os.Process.myUid() / USER_RANGE
        private val root: Boolean = runCatching { Shizuku.getUid() == 0 }.getOrDefault(false)
        /** Who the system records for SUSPEND; has to match for a later lift to take. */
        private val suspender = if (root) ROOT_SUSPENDER else SHELL_PACKAGE
        private val caller = if (root) context.packageName else SHELL_PACKAGE
        private val packageManager = service("package", "android.content.pm.IPackageManager")
        private val activityManager = service("activity", "android.app.IActivityManager")
        private val appOps = runCatching {
            service("appops", "com.android.internal.app.IAppOpsService")
        }.getOrNull()

        fun forceStop(packageName: String) {
            call(activityManager, "forceStopPackage", packageName, userId)
        }

        fun setEnabled(packageName: String, enabled: Boolean) {
            val state = when {
                enabled -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                // Root may disable outright; a shell only ever disables for the user, which
                // is the state `pm disable-user` writes and what Settings offers to undo.
                root -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            }
            call(
                packageManager, "setApplicationEnabledSetting",
                packageName, state, 0, userId, caller,
            )
        }

        fun setHidden(packageName: String, hidden: Boolean): Boolean =
            call(packageManager, "setApplicationHiddenSettingAsUser", packageName, hidden, userId) == true

        fun setSuspended(packageName: String, suspended: Boolean): Boolean {
            val names = arrayOf(packageName)
            // Android 15 added the flags argument and split the suspender from the user it
            // acts on; before that the only suspender parameter is the calling package.
            val dialog = if (suspended) suspendDialog else null
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                call(
                    packageManager, "setPackagesSuspendedAsUser",
                    names, suspended, null, null, dialog, 0, suspender, userId, userId,
                )
            } else {
                call(
                    packageManager, "setPackagesSuspendedAsUser",
                    names, suspended, null, null, dialog, suspender, userId,
                )
            }
            return (result as? Array<*>)?.isEmpty() ?: false
        }

        /** The same two app-ops `pm suspend` leaves behind, so scheduled work stops too. */
        fun setBackgroundRestricted(packageName: String, restricted: Boolean) {
            val ops = appOps ?: return
            val uid = FreezeState.applicationInfo(context, packageName)?.uid ?: return
            val mode = if (restricted) AppOpsManager.MODE_IGNORED else AppOpsManager.MODE_ALLOWED
            for (name in BACKGROUND_OPS) {
                val op = opCode(name) ?: continue
                runCatching { call(ops, "setMode", op, uid, packageName, mode) }
            }
        }

        fun freeze(packageName: String, method: FreezeMethod): Boolean = when (method) {
            FreezeMethod.STOP -> {
                forceStop(packageName)
                true
            }
            FreezeMethod.DISABLE -> {
                forceStop(packageName)
                setEnabled(packageName, false)
                true
            }
            FreezeMethod.HIDE -> {
                forceStop(packageName)
                setHidden(packageName, true)
            }
            FreezeMethod.SUSPEND -> {
                runCatching { setBackgroundRestricted(packageName, true) }
                forceStop(packageName)
                setSuspended(packageName, true)
            }
        }

        private fun service(name: String, className: String): Any {
            val binder = SystemServiceHelper.getSystemService(name)
                ?: throw IllegalStateException("no $name service")
            val stub = Class.forName("$className\$Stub")
            return HiddenApiBypass.invoke(stub, null, "asInterface", ShizukuBinderWrapper(binder))
                ?: throw IllegalStateException("no $className")
        }

        private fun call(target: Any, method: String, vararg args: Any?): Any? =
            HiddenApiBypass.invoke(target.javaClass, target, method, *args)
    }

    companion object {
        /** Android's per-user uid range; the constant itself is not in the public SDK. */
        private const val USER_RANGE = 100_000

        /** The name `pm` records when it runs as root. */
        private const val ROOT_SUSPENDER = "root"

        /** `SuspendDialogInfo.BUTTON_ACTION_UNSUSPEND`, the button that lifts and opens. */
        private const val UNPAUSE_ACTION = 1

        /** The shell's package, which is both the caller and the suspender outside root mode. */
        private const val SHELL_PACKAGE = "com.android.shell"

        private val BACKGROUND_OPS = listOf(
            "android:run_any_in_background",
            "android:run_in_background",
        )

        private fun opCode(name: String): Int? = runCatching {
            HiddenApiBypass.invoke(AppOpsManager::class.java, null, "strOpToOp", name) as? Int
        }.getOrNull()
    }
}
