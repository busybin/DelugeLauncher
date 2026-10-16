// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The release page's reply, read without a network.
 *
 * The live call itself is a single request whose wiring is easier to see than to test; what is
 * worth pinning down is what the app makes of the answer, including the answers that are not
 * the one expected.
 */
class UpdateCheckerTest {

    private fun releaseJson(tag: String) =
        """{"tag_name":"$tag","name":"${tag.uppercase()}","html_url":"https://example.invalid/$tag"}"""

    @Test
    fun `a newer tag is an update`() {
        assertEquals(
            UpdateResult.Available(24, "v24"),
            UpdateChecker.parse(releaseJson("v24"), currentVersion = 22),
        )
    }

    @Test
    fun `the same tag is up to date`() {
        assertEquals(
            UpdateResult.UpToDate(22),
            UpdateChecker.parse(releaseJson("v22"), currentVersion = 22),
        )
    }

    @Test
    fun `an older tag is up to date rather than an update`() {
        // A release page can be pointed at something older than what is installed — a build
        // made by hand, or a page left behind. Neither is a reason to offer a downgrade.
        assertEquals(
            UpdateResult.UpToDate(21),
            UpdateChecker.parse(releaseJson("v21"), currentVersion = 22),
        )
    }

    @Test
    fun `a reply without a version is unexpected rather than an update`() {
        assertEquals(
            UpdateResult.Failed(UpdateError.UNEXPECTED),
            UpdateChecker.parse("""{"name":"V24"}""", currentVersion = 22),
        )
    }

    @Test
    fun `a reply that is not JSON at all is unexpected`() {
        assertEquals(
            UpdateResult.Failed(UpdateError.UNEXPECTED),
            UpdateChecker.parse("<html>503</html>", currentVersion = 22),
        )
    }

    @Test
    fun `tags are read with or without a v prefix`() {
        assertEquals(22, UpdateChecker.versionFromTag("v22"))
        assertEquals(24, UpdateChecker.versionFromTag("V24"))
        assertEquals(22, UpdateChecker.versionFromTag("22"))
        assertEquals(23, UpdateChecker.versionFromTag(" v23 "))
    }

    @Test
    fun `a tag that is not a plain number has no version`() {
        // The fork's own releases are integers, but the upstream history is not: a semver tag
        // reaching this must not be read as "version 0".
        assertEquals(null, UpdateChecker.versionFromTag("v0.84.1"))
        assertEquals(null, UpdateChecker.versionFromTag(""))
        assertEquals(null, UpdateChecker.versionFromTag("latest"))
    }
}
