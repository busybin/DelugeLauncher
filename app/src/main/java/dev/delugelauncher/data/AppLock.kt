// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

/**
 * Which locked apps are open right now, and since when.
 *
 * Held in memory on purpose: a restart, or the screen going off, is exactly when the answer
 * should stop being trusted. Nothing here is written to the store, so nothing survives the
 * launcher being killed.
 */
object AppLock {

    private val opened = mutableMapOf<String, Long>()

    @Synchronized
    fun markOpen(key: String, now: Long = System.currentTimeMillis()) {
        opened[key] = now
    }

    /**
     * Whether [key] may open without asking again.
     *
     * [timeoutSeconds] of 0 means every tap asks, which is also what an unknown key gets.
     */
    @Synchronized
    fun isOpen(key: String, timeoutSeconds: Int, now: Long = System.currentTimeMillis()): Boolean {
        val at = opened[key] ?: return false
        if (timeoutSeconds <= 0) return false
        if (now - at >= timeoutSeconds * 1000L) {
            opened.remove(key)
            return false
        }
        return true
    }

    @Synchronized
    fun forget(key: String) {
        opened.remove(key)
    }

    @Synchronized
    fun forgetAll() {
        opened.clear()
    }
}
