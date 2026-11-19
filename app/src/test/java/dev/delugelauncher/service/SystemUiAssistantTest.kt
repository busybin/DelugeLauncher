// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.service

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The corner button's voice action: listening first, the assistant's own screen only as a
 * fallback, and a quiet "no" when the phone has neither.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemUiAssistantTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `voice command is asked first`() {
        assertTrue(SystemUi.startVoiceAssistant(app))

        assertEquals(Intent.ACTION_VOICE_COMMAND, shadowOf(app).nextStartedActivity?.action)
    }

    @Test
    fun `a phone with nothing to answer says so instead of crashing`() {
        // With resolution enforced, neither action has a target, which is the case this has
        // to survive: a launcher that throws here takes the home screen with it.
        shadowOf(app).checkActivities(true)

        assertFalse(SystemUi.startVoiceAssistant(app))
    }
}
