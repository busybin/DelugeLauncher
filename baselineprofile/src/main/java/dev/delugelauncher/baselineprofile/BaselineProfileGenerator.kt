// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code and layout paths a real cold start and a first list open actually
 * touch, so they ship ahead-of-time compiled instead of JITed on the user's first tap.
 *
 * Only the paths that matter on every launch are exercised here: home cold start, then
 * one app-list open by tapping the screen edge the same way a finger does. Settings and
 * pickers stay out — profiling them would grow the profile for screens most launches
 * never visit.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect("dev.delugelauncher") {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }
}
