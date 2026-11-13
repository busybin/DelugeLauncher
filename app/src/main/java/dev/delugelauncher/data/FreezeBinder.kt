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

    /**
     * Whether hiding apps can go over the binder.
     *
     * Hiding is a root operation: a shell-mode manager cannot do it, but one running as root
     * (Shevery, or Shizuku in root mode) can, and then it is one IPC instead of a `pm` fork.
     */
    fun canHide(): Boolean = available() && runCatching { Shizuku.getUid() == 0 }.getOrDefault(false)

    fun freeze(
        packageName: String,
        method: FreezeMethod,
        userId: Int,
        uid: Int,
    ): Boolean? = attempt { it.freeze(packageName, method, userId, uid) }

    /** Sets the platform's hidden flag directly; root only, like the `pm hide` command. */
    fun setHidden(packageName: String, hidden: Boolean, userId: Int = 0): Boolean? =
        attempt { it.setHidden(packageName, hidden, userId) }

    /**
     * Re-applies the pause with the dialog on an app paused before the dialog existed.
     * Called once per app by the upgrade pass; a pause by the same suspender replaces the
     * stored parameters, so this is what gives old records the unpause button. The record's
     * own identity is used, not this channel's: re-pausing under another name would leave
     * the old, dialog-less record in place.
     */
    fun refreshSuspend(packageName: String, userId: Int): Boolean? = attempt { proxy ->
        proxy.setSuspended(
            packageName, suspended = true, userId = userId,
            suspender = proxy.currentSuspender(packageName, userId) ?: proxy.suspender,
        )
    }

    /** Lifts every kind of freeze, because the stored method can be wrong. */
    fun unfreeze(packageName: String, userId: Int, uid: Int): Boolean? = attempt { proxy ->
        proxy.liftSuspension(packageName, userId)
        proxy.setEnabled(packageName, enabled = true, userId = userId)
        runCatching { proxy.setHidden(packageName, hidden = false, userId = userId) }
        if (uid > 0) runCatching { proxy.setBackgroundRestricted(packageName, restricted = false, uid) }
        true
    }

    /** Lifts the two background app-ops a pause left behind, without touching the pause. */
    fun setBackgroundAllowed(packageName: String, uid: Int): Boolean? = attempt { proxy ->
        proxy.setBackgroundRestricted(packageName, restricted = false, uid)
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

        private val root: Boolean = runCatching { Shizuku.getUid() == 0 }.getOrDefault(false)

        /**
         * Who the system records for SUSPEND; has to match for a later lift to take.
         *
         * Always the shell name, even when this binder runs as root: a suspension recorded
         * against "root" (or against this app) outlives the root grant, and once root is gone
         * nothing but root can name that identity again -- the app stays suspended for good.
         * `com.android.shell` is the name both `pm suspend` writes and every shell can lift.
         */
        val suspender = SHELL_PACKAGE
        private val caller = SHELL_PACKAGE
        private val packageManager = service("package", "android.content.pm.IPackageManager")
        private val activityManager = service("activity", "android.app.IActivityManager")
        private val appOps = runCatching {
            service("appops", "com.android.internal.app.IAppOpsService")
        }.getOrNull()

        fun forceStop(packageName: String, userId: Int) {
            call(activityManager, "forceStopPackage", packageName, userId)
        }

        fun setEnabled(packageName: String, enabled: Boolean, userId: Int) {
            // Always the user-level state: it is what `pm disable-user` writes, it is what
            // Settings offers an "Enable" button for, and it survives losing root. The
            // outright DISABLED state hides that button and leaves the user with no way back.
            val state = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            }
            call(
                packageManager, "setApplicationEnabledSetting",
                packageName, state, 0, userId, caller,
            )
        }

        fun setHidden(packageName: String, hidden: Boolean, userId: Int): Boolean =
            call(packageManager, "setApplicationHiddenSettingAsUser", packageName, hidden, userId) == true

        fun setSuspended(
            packageName: String,
            suspended: Boolean,
            userId: Int,
            suspender: String = this.suspender,
        ): Boolean {
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

        /** The identity that put the pause in place, as the platform reports it. */
        fun currentSuspender(packageName: String, userId: Int): String? = runCatching {
            call(packageManager, "getSuspendingPackage", packageName, userId) as? String
        }.getOrNull()?.takeIf { it.isNotBlank() }

        /** The platform's answer, or null when it could not be asked. */
        fun isSuspended(packageName: String, userId: Int): Boolean? = runCatching {
            call(packageManager, "isPackageSuspendedForUser", packageName, userId) as? Boolean
        }.getOrNull()

        /**
         * Lifts the pause even when another identity added it.
         *
         * The platform keys suspensions by the identity that added them, and the call to
         * lift one answers cleanly while changing nothing when the name does not match.
         * Root is allowed to name any suspender, so the identity the platform reports and
         * then both names `pm` can write are tried until the state reads back clear.
         */
        fun liftSuspension(packageName: String, userId: Int) {
            for (name in suspendersToClear(currentSuspender(packageName, userId), suspender)) {
                runCatching {
                    setSuspended(packageName, suspended = false, userId = userId, suspender = name)
                }
                if (isSuspended(packageName, userId) == false) return
            }
        }

        /** The same two app-ops `pm suspend` leaves behind, so scheduled work stops too. */
        fun setBackgroundRestricted(packageName: String, restricted: Boolean, uid: Int) {
            val ops = appOps ?: return
            if (uid <= 0) return
            val mode = if (restricted) AppOpsManager.MODE_IGNORED else AppOpsManager.MODE_ALLOWED
            for (name in BACKGROUND_OPS) {
                val op = opCode(name) ?: continue
                runCatching { call(ops, "setMode", op, uid, packageName, mode) }
            }
        }

        fun freeze(
            packageName: String,
            method: FreezeMethod,
            userId: Int,
            uid: Int,
        ): Boolean = when (method) {
            FreezeMethod.STOP -> {
                forceStop(packageName, userId)
                true
            }
            FreezeMethod.DISABLE -> {
                forceStop(packageName, userId)
                setEnabled(packageName, false, userId)
                true
            }
            FreezeMethod.HIDE -> {
                forceStop(packageName, userId)
                setHidden(packageName, true, userId)
            }
            FreezeMethod.SUSPEND -> {
                runCatching { setBackgroundRestricted(packageName, restricted = true, uid) }
                forceStop(packageName, userId)
                setSuspended(packageName, suspended = true, userId)
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
        /** The name `pm` records when it runs as root. */
        private const val ROOT_SUSPENDER = "root"

        /**
         * The suspender names worth clearing, in order.
         *
         * The reported owner comes first; the rest are the two names `pm` writes, so a
         * record left by the other channel is still cleared. Without a reported owner the
         * channel's own name is not added here — the caller passes it in.
         */
        internal fun suspendersToClear(current: String?, own: String? = null): List<String> {
            val order = LinkedHashSet<String>()
            current?.takeIf { it.isNotBlank() }?.let(order::add)
            own?.takeIf { it.isNotBlank() }?.let(order::add)
            order += ROOT_SUSPENDER
            order += SHELL_PACKAGE
            return order.toList()
        }

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
