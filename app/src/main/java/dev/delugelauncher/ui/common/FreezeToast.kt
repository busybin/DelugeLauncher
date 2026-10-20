// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import android.content.Context
import android.widget.Toast

/**
 * Progress bubbles for the freeze list, and the answer that replaces them.
 *
 * A Toast queues: showing the result while "freezing" is still up leaves the progress text
 * on screen for its whole duration after the work is done. Keeping the running bubble here
 * lets the answer cancel it and take its place at once.
 */
object FreezeToast {
    private var progress: Toast? = null

    fun progress(context: Context, textRes: Int) {
        progress?.cancel()
        // The application context: a run outlives the screen that started it, and the static
        // holder must not keep a destroyed activity alive.
        progress = Toast.makeText(context.applicationContext, textRes, Toast.LENGTH_SHORT).also { it.show() }
    }

    fun result(context: Context, text: CharSequence, long: Boolean = false) {
        progress?.cancel()
        progress = null
        Toast.makeText(
            context.applicationContext,
            text,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
        ).show()
    }
}
