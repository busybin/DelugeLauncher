// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import dev.delugelauncher.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the stack trace of the last crash so it can be read and sent with a bug report.
 *
 * A launcher hosts other apps' widgets, which draw with their own code in this process — so a
 * crash here is often not from anything in this repository, and the trace is the only thing
 * that says whose it was. Written to a file rather than held in memory, because the process
 * that would remember it is the one going down.
 *
 * Nothing is sent anywhere. The file is read when someone opens settings and asks for it.
 */
object CrashLog {

    private const val FILE_NAME = "last_crash.txt"

    /** A runaway cause chain is not a backup: cap the trace before writing it. */
    private const val MAX_TRACE_CHARS = 64 * 1024

    /**
     * Records the trace, then lets the process die the way it was going to.
     *
     * Chained rather than replacing what was there: the handler ahead of this one is what
     * actually ends the process and tells the system it crashed. Swallowing that would leave a
     * launcher frozen on screen instead of restarting — so when there is no handler ahead,
     * this one ends the process itself.
     */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, thread, error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                // Nobody else to end the process: do it here rather than limping on with a
                // half-dead launcher that will never restart.
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            // A runaway cause chain is not a backup: cap the trace so a huge file cannot
            // mislead the next read or crowd the disk.
            .take(MAX_TRACE_CHARS)
        val when_ = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        file(context).writeText(
            buildString {
                appendLine("Deluge Launcher ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine(when_)
                appendLine("thread: ${thread.name}")
                appendLine()
                append(stack)
            }
        )
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /**
     * The last crash, or null if there has not been one since it was last cleared.
     *
     * Names belonging to a second profile are taken out here rather than at the copy button:
     * the trace is also shown on screen, and it exists to be pasted somewhere public.
     */
    fun read(context: Context): String? {
        val trace = runCatching { file(context).takeIf { it.isFile }?.readText() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return CrashRedaction.redact(trace, otherProfile(context))
    }

    /** Whether a package is installed in some profile other than the one running this app. */
    private fun otherProfile(context: Context): (String) -> Boolean {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return { false }
        val mine = Process.myUserHandle()
        val others = runCatching { launcherApps.profiles.filter { it != mine } }
            .getOrDefault(emptyList())
        if (others.isEmpty()) return { false }
        return { pkg ->
            others.any { user ->
                runCatching { launcherApps.getApplicationInfo(pkg, 0, user) }.isSuccess
            }
        }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
