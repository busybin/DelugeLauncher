// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

/** Where a freeze command is sent. */
enum class FreezeExecutor {
    /** The device-owner policy call, no external process. */
    POLICY,
    /** Shizuku's binder or its process API. */
    BINDER,
    /** `su -c`, used by hiding and by an explicit root choice. */
    ROOT_SHELL,
    /** Whichever of Shizuku and root answers: the automatic path's last resort. */
    SHELL,
}

/**
 * Turns a method and a channel into the executors that may run it.
 *
 * Auto keeps the order that existed before channels were configurable. An explicit channel
 * is a promise, not a hint: it gets exactly its own executors, so a user who picks Shizuku
 * for speed is not quietly sent down a root shell and left with a suspension recorded under
 * the other identity. The page greys a choice the device cannot honour instead.
 *
 * A plain shell cannot hide a package, so AUTO reaches for root there.
 */
object FreezeRoute {

    fun executors(method: FreezeMethod, channel: FreezeChannel): List<FreezeExecutor> =
        when (channel) {
            FreezeChannel.AUTO -> if (method == FreezeMethod.HIDE) {
                listOf(
                    FreezeExecutor.POLICY,
                    FreezeExecutor.BINDER,
                    FreezeExecutor.ROOT_SHELL,
                )
            } else {
                listOf(
                    FreezeExecutor.POLICY,
                    FreezeExecutor.BINDER,
                    FreezeExecutor.SHELL,
                )
            }
            FreezeChannel.OWNER -> listOf(FreezeExecutor.POLICY)
            FreezeChannel.SHIZUKU -> listOf(FreezeExecutor.BINDER)
            FreezeChannel.ROOT -> listOf(FreezeExecutor.ROOT_SHELL)
        }
}
