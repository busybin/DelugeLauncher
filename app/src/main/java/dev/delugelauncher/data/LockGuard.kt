// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.content.Context
import android.os.Process
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What the device can enforce for a locked app right now.
 *
 * The password gate always works. Hiding and suspension are the system layers; the guard is
 * the boot script that keeps them applied while Deluge is stopped.
 */
data class LockCapabilities(
    val canHide: Boolean = false,
    val canSuspend: Boolean = false,
    val canGuard: Boolean = false,
) {
    val isFull: Boolean get() = canHide && canSuspend && canGuard
}

/**
 * Keeps the lock outside Deluge's own process.
 *
 * Deluge already asks the package manager to suspend and hide a locked app. Those flags survive
 * the app being force-stopped, but a few OEM Settings builds offer their own "unpause" button,
 * and the launcher's repair receiver cannot run while it is stopped. This writes a tiny root
 * script into the kernel manager's `service.d` directory; it rereads the list every few
 * seconds, re-hides anything the system made visible, and periodically refreshes suspension.
 * A root-only file next to it names the one app whose authenticated launch is allowed through,
 * until the launcher clears it or the token expires.
 */
class LockGuard(
    private val context: Context,
    private val shell: FreezeShell,
) {
    private val listFile = File(context.filesDir, LIST_FILE)
    private val allowFile = File(context.filesDir, ALLOW_FILE)

    @Volatile
    private var running = false

    /**
     * Writes the current list and starts the guard when it is both needed and possible.
     *
     * The list is written even when root is missing: the next start after access is granted
     * finds it already there and only has to install the script.
     */
    suspend fun sync(locked: Set<String>): Boolean = withContext(Dispatchers.IO) {
        synchronized(this@LockGuard) {
            val hasPackages = writeList(locked)
            if (!hasPackages) {
                if (running) stop()
                return@synchronized false
            }
            // Keep an already-installed guard alive when the root probe is temporarily
            // unavailable: it runs as root and can keep enforcing the list.
            if (!shell.rootAvailable()) return@synchronized false
            install()
        }
    }

    /** Whether this root setup can hold the script across a stop. */
    fun canInstall(): Boolean = shell.runAsRoot(
        "mkdir -p /data/adb/service.d && [ -w /data/adb/service.d ]",
    )

    /** Lets one authenticated launch through until Deluge comes back or the screen goes off. */
    fun allow(packageName: String) {
        if (!FreezeCommands.isPackageName(packageName)) return
        synchronized(this) {
            val now = epochSeconds()
            val kept = readAllow().filter { it.expiresAt > now && it.packageName != packageName }
            writeAllow(kept + AllowEntry(packageName, now + ALLOW_SECONDS))
        }
    }

    fun clearAllow(packageName: String? = null) {
        synchronized(this) {
            if (packageName == null) {
                allowFile.delete()
                return
            }
            val kept = readAllow().filter { it.packageName != packageName }
            writeAllow(kept)
        }
    }

    private fun install(): Boolean {
        if (running && guardAlive()) return true
        running = false
        val encoded = Base64.encodeToString(
            SCRIPT_TEMPLATE
                .replace("__APP__", context.packageName)
                .replace("__DATA__", context.filesDir.absolutePath)
                .replace("__USER__", Process.myUserHandle().hashCode().toString())
                .replace('§', '$')
                .toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
        )
        val ok = shell.runAsRoot(
            """
            if [ -f $PID_FILE ]; then kill -9 $(cat $PID_FILE) 2>/dev/null; rm -f $PID_FILE; fi
            mkdir -p /data/adb/service.d
            echo $encoded | base64 -d > $SCRIPT_FILE
            chmod 755 $SCRIPT_FILE
            nohup sh $SCRIPT_FILE >/dev/null 2>&1 &
            sleep 1
            [ -f $PID_FILE ] && kill -0 $(cat $PID_FILE) 2>/dev/null
            """.trimIndent(),
        )
        running = ok
        return ok
    }

    private fun stop() {
        shell.runAsRoot(
            """
            if [ -f $PID_FILE ]; then kill -9 $(cat $PID_FILE) 2>/dev/null; rm -f $PID_FILE; fi
            rm -f $SCRIPT_FILE
            """.trimIndent(),
        )
        running = false
    }

    private fun guardAlive(): Boolean = shell.runAsRoot(
        """[ -f $PID_FILE ] && kill -0 $(cat $PID_FILE) 2>/dev/null""",
    )

    private fun writeList(locked: Set<String>): Boolean {
        val packages = locked.asSequence()
            .filterNot { EntryKeys.isShortcut(it) || EntryKeys.isPrivateSpace(it) }
            .filter { EntryKeys.userSerial(it) == 0L }
            .map { it.substringBefore('/') }
            .filter { FreezeCommands.isPackageName(it) }
            .distinct()
            .sorted()
            .toList()
        writeAtomic(listFile, if (packages.isEmpty()) "" else packages.joinToString("\n") + "\n")
        return packages.isNotEmpty()
    }

    private fun readAllow(): List<AllowEntry> = runCatching {
        allowFile.readLines().mapNotNull { line ->
            val parts = line.trim().split(' ')
            val packageName = parts.getOrNull(0) ?: return@mapNotNull null
            val expiresAt = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            if (!FreezeCommands.isPackageName(packageName)) return@mapNotNull null
            AllowEntry(packageName, expiresAt)
        }
    }.getOrDefault(emptyList())

    private fun writeAllow(entries: List<AllowEntry>) {
        if (entries.isEmpty()) {
            allowFile.delete()
            return
        }
        writeAtomic(
            allowFile,
            entries.joinToString("\n") { "${it.packageName} ${it.expiresAt}" } + "\n",
        )
    }

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    private fun epochSeconds(): Long = System.currentTimeMillis() / 1000L

    private data class AllowEntry(val packageName: String, val expiresAt: Long)

    private companion object {
        const val LIST_FILE = "lock_guard.list"
        const val ALLOW_FILE = "lock_guard.allow"
        const val SCRIPT_FILE = "/data/adb/service.d/deluge-lock-guard.sh"
        const val PID_FILE = "/data/adb/deluge-lock-guard.pid"

        /** Long enough for a long session, short enough that a killed launcher does not leave one open. */
        const val ALLOW_SECONDS = 2L * 60L * 60L

        /**
         * String is a shell script; `§` stands in for `$` so Kotlin does not read shell
         * variables as its own interpolation. `__APP__` is the installed package name.
         */
        val SCRIPT_TEMPLATE = """
            #!/system/bin/sh
            APP=__APP__
            DATA=__DATA__
            USER=__USER__
            LIST=§DATA/lock_guard.list
            ALLOW=§DATA/lock_guard.allow
            PIDFILE=$PID_FILE
            if [ -f "§PIDFILE" ]; then
              old=§(cat "§PIDFILE" 2>/dev/null)
              if [ -n "§old" ] && kill -0 "§old" 2>/dev/null; then exit 0; fi
            fi
            echo §§ > "§PIDFILE"
            trap 'rm -f "§PIDFILE"' EXIT
            trap 'rm -f "§PIDFILE"; exit 0' INT TERM
            is_allowed() {
              [ -f "§ALLOW" ] || return 1
              exp=§(awk -v p="§1" '§1 == p { e = §2 } END { if (e != "") print e }' "§ALLOW")
              [ -n "§exp" ] && [ "§exp" -gt "§now" ]
            }
            missing=0
            empty=0
            loop=0
            while true; do
              if ! pm list packages --user "§USER" 2>/dev/null | grep -q "package:§APP"; then
                rm -f "§0" "§PIDFILE"
                exit 0
              fi
              if [ ! -f "§LIST" ]; then
                missing=§((missing + 1))
                if [ "§missing" -gt 100 ]; then
                  rm -f "§0" "§PIDFILE"
                  exit 0
                fi
                sleep 3
                continue
              fi
              missing=0
              if [ ! -s "§LIST" ]; then
                empty=§((empty + 1))
                if [ "§empty" -gt 20 ]; then
                  rm -f "§0" "§PIDFILE"
                  exit 0
                fi
                sleep 3
                continue
              fi
              empty=0
              awake=0
              if dumpsys power 2>/dev/null | grep -q 'mWakefulness=Awake'; then
                awake=1
              fi
              if [ "§awake" -eq 0 ]; then
                rm -f "§ALLOW"
                # Nothing here needs repairing while the screen is off; waking checks again.
                sleep 15
                continue
              fi
              now=§(date +%s)
              visible=§(pm list packages --user "§USER" 2>/dev/null)
              loop=§((loop + 1))
              while IFS= read -r pkg; do
                [ -n "§pkg" ] || continue
                if is_allowed "§pkg"; then continue; fi
                if printf '%s\n' "§visible" | grep -Fqx "package:§pkg"; then
                  pm suspend --user "§USER" "§pkg" >/dev/null 2>&1
                  pm hide --user "§USER" "§pkg" >/dev/null 2>&1
                  if is_allowed "§pkg"; then
                    pm unsuspend --user "§USER" "§pkg" >/dev/null 2>&1
                    pm unhide --user "§USER" "§pkg" >/dev/null 2>&1
                  fi
                elif [ "§loop" -ge 20 ] && [ §((loop % 20)) -eq 0 ]; then
                  pm suspend --user "§USER" "§pkg" >/dev/null 2>&1
                  if is_allowed "§pkg"; then
                    pm unsuspend --user "§USER" "§pkg" >/dev/null 2>&1
                  fi
                fi
              done < "§LIST"
              sleep 3
            done
        """.trimIndent()
    }
}
