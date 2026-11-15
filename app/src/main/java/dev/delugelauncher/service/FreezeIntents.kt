// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import dev.delugelauncher.DelugeApp
import java.security.MessageDigest

/**
 * The small external surface, so a script, Tasker or a hardware button can drive the freeze
 * list without opening the launcher.
 *
 * Two ways in, one implementation: a broadcast and a `deluge://` link. Both are exported, so
 * both are closed unless the user turned external control on and sent the token from settings.
 * Without that, any installed app — and any web page, through `deluge://` — could lift the
 * whole list with one intent.
 *
 * The broadcast must name the receiver:
 * `adb shell am broadcast -n dev.delugelauncher/.service.FreezeActionReceiver -a …`.
 * Android drops implicit broadcasts to manifest receivers, so the bare `-a <action>` form
 * never arrives, however the process is running.
 *
 * Only things the user could do by hand are exposed: no hidden state, no lock verb, and a
 * package never leaves the list, so a freeze can always be undone from the screen.
 */
object FreezeIntents {
    const val ACTION_FREEZE = "dev.delugelauncher.action.FREEZE"
    const val ACTION_UNFREEZE = "dev.delugelauncher.action.UNFREEZE"
    const val ACTION_FREEZE_ALL = "dev.delugelauncher.action.FREEZE_ALL"
    const val ACTION_UNFREEZE_ALL = "dev.delugelauncher.action.UNFREEZE_ALL"

    /** Both `-e package com.example` and `deluge://freeze?package=com.example` use this name. */
    const val EXTRA_PACKAGE = "package"

    /** The shared secret from settings; every external door refuses to act without it. */
    const val EXTRA_TOKEN = "token"

    /** Constant-time compare, so a wrong token cannot be narrowed down by timing. */
    fun matches(expected: String?, given: String?): Boolean {
        if (expected.isNullOrEmpty() || given.isNullOrEmpty()) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            given.toByteArray(Charsets.UTF_8),
        )
    }

    /**
     * Runs an external command, or nothing when the switch is off or the token is wrong.
     *
     * Runs off the main thread; callers that must not outlive the work await it.
     */
    suspend fun dispatch(context: Context, action: String?, packageName: String?, token: String?) {
        val app = context.applicationContext as? DelugeApp ?: return
        if (!app.prefs.externalControlEnabledNow()) return
        if (!matches(app.prefs.externalControlTokenNow(), token)) return
        when (action) {
            ACTION_FREEZE -> packageName?.let { freezeOne(app, it) }
            ACTION_UNFREEZE -> packageName?.let { unfreezeOne(app, it) }
            // Not the screen-off run, but a remote trigger may arrive while the phone is in
            // use: skip what is in front and anything with a notification, as that run does.
            ACTION_FREEZE_ALL -> app.freezeManager.freezeAll(auto = true)
            ACTION_UNFREEZE_ALL -> app.freezeManager.unfreezeAll()
        }
    }

    /**
     * The intent the link door hands to the broadcast door.
     *
     * A finished activity is a cached process, and the system freezes it long before a
     * multi-pass lift can return — which is why the activity used to swallow most unfreezes.
     * The receiver holds the process open with `goAsync`, so both doors share its lifetime.
     */
    fun receiverIntent(
        context: Context,
        action: String,
        packageName: String?,
        token: String?,
    ): Intent = Intent(context, FreezeActionReceiver::class.java).apply {
        this.action = action
        putExtra(EXTRA_PACKAGE, packageName)
        putExtra(EXTRA_TOKEN, token)
    }

    /**
     * The per-app shortcut target is exported, because the launcher showing the shortcut is the
     * one that starts it. A stranger's launch comes from its own package, so a toggle is only
     * allowed from this app itself or with the token written into newer shortcuts.
     */
    fun toggleAllowed(
        expectedToken: String?,
        givenToken: String?,
        launchedFrom: String?,
        ownPackage: String,
    ): Boolean = (launchedFrom != null && launchedFrom == ownPackage) ||
        matches(expectedToken, givenToken)

    /** Maps a `deluge://` link onto the same actions. */
    fun actionOf(uri: Uri?): String? = when (uri?.host) {
        "freeze" -> ACTION_FREEZE
        "unfreeze" -> ACTION_UNFREEZE
        "freeze_all" -> ACTION_FREEZE_ALL
        "unfreeze_all" -> ACTION_UNFREEZE_ALL
        else -> null
    }

    /**
     * Only an app the user already listed can be driven from outside. The list is the
     * authority: neither verb may add a package, and freeze/unfreeze both re-check it.
     */
    private suspend fun freezeOne(app: DelugeApp, key: String) {
        if (key !in app.prefs.freezeMethodsNow()) return
        app.freezeManager.freezeKey(key)
    }

    private suspend fun unfreezeOne(app: DelugeApp, key: String) {
        if (key !in app.prefs.freezeMethodsNow()) return
        app.freezeManager.unfreezeKey(key)
    }
}

/** The broadcast door: `am broadcast -n dev.delugelauncher/.service.FreezeActionReceiver -a …`. */
class FreezeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? DelugeApp ?: return
        val pending = goAsync()
        app.runInBackground {
            try {
                FreezeIntents.dispatch(
                    context,
                    intent.action,
                    intent.getStringExtra(FreezeIntents.EXTRA_PACKAGE),
                    intent.getStringExtra(FreezeIntents.EXTRA_TOKEN),
                )
            } finally {
                pending.finish()
            }
        }
    }
}

/** The link door: `adb shell am start -a android.intent.action.VIEW -d deluge://freeze_all`. */
class FreezeActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = FreezeIntents.actionOf(intent.data) ?: intent.action
        val packageName = intent.getStringExtra(FreezeIntents.EXTRA_PACKAGE)
            ?: intent.data?.getQueryParameter(FreezeIntents.EXTRA_PACKAGE)
        val token = intent.getStringExtra(FreezeIntents.EXTRA_TOKEN)
            ?: intent.data?.getQueryParameter(FreezeIntents.EXTRA_TOKEN)
        if (action != null) {
            sendBroadcast(FreezeIntents.receiverIntent(this, action, packageName, token))
        }
        finish()
    }
}
