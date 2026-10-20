// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.settings

import android.content.ComponentName
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.FreezeAccess
import dev.delugelauncher.data.FreezeMethod
import dev.delugelauncher.data.FreezeSort
import dev.delugelauncher.data.FreezeSourceFilter
import dev.delugelauncher.data.FreezeStateFilter
import dev.delugelauncher.data.FreezeStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Choosing an app and freezing it are two steps, and the screen has to keep them apart: a row
 * can be on the list and not frozen, and then a trigger is what freezes it. Getting this wrong
 * is how a frozen app ended up offering to freeze itself again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class FreezeAppsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun app(id: String) = AppInfo(
        componentName = ComponentName("com.example.$id", "com.example.$id.Main"),
        label = id,
    )

    private val frozen = app("frozen")
    private val pending = app("pending")
    private val free = app("free")

    private val plan = mapOf(
        frozen.packageName to FreezeMethod.STOP,
        pending.packageName to FreezeMethod.STOP,
    )

    private class Calls {
        var removed: AppInfo? = null
        var added: AppInfo? = null
        var addedMethod: FreezeMethod? = null
    }

    private fun showScreen(
        calls: Calls = Calls(),
        apps: List<AppInfo> = listOf(frozen, pending, free),
        methods: Map<String, FreezeMethod> = plan,
        applied: Set<String> = setOf(frozen.packageName),
        systemAppsEnabled: Boolean = false,
        sourceFilter: FreezeSourceFilter = FreezeSourceFilter.USER,
        stateFilter: FreezeStateFilter = FreezeStateFilter.ALL,
        sort: FreezeSort = FreezeSort.NAME_ASC,
        query: String = "",
    ) {
        compose.setContent {
            FreezeAppsScreen(
                allApps = apps,
                freezeMethods = methods,
                freezeApplied = applied,
                nameOverrides = emptyMap(),
                iconSizeDp = 48,
                status = FreezeStatus(access = FreezeAccess.ROOT, rootGranted = true),
                systemAppsEnabled = systemAppsEnabled,
                sourceFilter = sourceFilter,
                stateFilter = stateFilter,
                sort = sort,
                query = query,
                autoEnabled = false,
                autoDelaySeconds = 60,
                unfreezeOnUnlock = false,
                autoSkipCharging = true,
                autoSkipForeground = true,
                autoSkipNotifications = true,
                onRefreshAccess = {},
                onOpenShizuku = {},
                onAddToPlan = { target, method -> calls.added = target; calls.addedMethod = method },
                onAddManyToPlan = { _, _ -> },
                onInvertSelection = { _, _, _ -> },
                onSetApplied = { _, _ -> },
                onRemoveFromPlan = { target -> calls.removed = target },
                onFreezeAll = {},
                onUnfreezeAll = {},
                onSetAutoEnabled = {},
                onSetAutoDelay = {},
                onSetUnfreezeOnUnlock = {},
                onSetAutoSkipCharging = {},
                onSetAutoSkipForeground = {},
                onSetAutoSkipNotifications = {},
                onSetSystemAppsEnabled = {},
                onSetSourceFilter = {},
                onSetStateFilter = {},
                onSetSort = {},
                onSetQuery = {},
                onBack = {},
            )
        }
    }

    @Test
    fun `the method count is its own label so a long name cannot clip it`() {
        showScreen()
        // English keeps the stop tab at two listed apps; the count is a sibling of the name,
        // not part of it, so the name is what gives way when the tab is narrow.
        compose.onNodeWithText("Stop", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(" (2)", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "fr-rFR-w411dp-h891dp-xxhdpi")
    fun `the count is still there under a long method name`() {
        showScreen()
        compose.onNodeWithText("Suspendre", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(" (2)", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a listed app says whether it is frozen yet`() {
        showScreen()
        compose.onNodeWithText("In the list (2)").assertExists()
        // "Frozen" is both the filter chip and the row's state; the row is the second one.
        compose.onAllNodesWithText("Frozen")[1].assertExists()
        compose.onNodeWithText("Frozen when triggered").assertExists()
        compose.onNodeWithText("Other apps").assertExists()
    }

    @Test
    fun `unchecking a listed app takes it off the list`() {
        val calls = Calls()
        showScreen(calls)
        compose.onNodeWithText("pending").performClick()
        assertEquals(pending, calls.removed)
    }

    @Test
    fun `checking an app outside the list only adds it`() {
        val calls = Calls()
        showScreen(calls)
        compose.onNodeWithText("free").performClick()
        assertEquals(free, calls.added)
        assertEquals(FreezeMethod.STOP, calls.addedMethod)
    }

    @Test
    fun `an app listed under another method says so`() {
        compose.setContent {
            FreezeAppsScreen(
                allApps = listOf(frozen),
                freezeMethods = mapOf(frozen.packageName to FreezeMethod.SUSPEND),
                freezeApplied = emptySet(),
                nameOverrides = emptyMap(),
                iconSizeDp = 48,
                status = FreezeStatus(access = FreezeAccess.ROOT, rootGranted = true),
                systemAppsEnabled = false,
                sourceFilter = FreezeSourceFilter.USER,
                stateFilter = FreezeStateFilter.ALL,
                sort = FreezeSort.NAME_ASC,
                query = "",
                autoEnabled = false,
                autoDelaySeconds = 60,
                unfreezeOnUnlock = false,
                autoSkipCharging = true,
                autoSkipForeground = true,
                autoSkipNotifications = true,
                onRefreshAccess = {},
                onOpenShizuku = {},
                onAddToPlan = { _, _ -> },
                onAddManyToPlan = { _, _ -> },
                onInvertSelection = { _, _, _ -> },
                onSetApplied = { _, _ -> },
                onRemoveFromPlan = {},
                onFreezeAll = {},
                onUnfreezeAll = {},
                onSetAutoEnabled = {},
                onSetAutoDelay = {},
                onSetUnfreezeOnUnlock = {},
                onSetAutoSkipCharging = {},
                onSetAutoSkipForeground = {},
                onSetAutoSkipNotifications = {},
                onSetSystemAppsEnabled = {},
                onSetSourceFilter = {},
                onSetStateFilter = {},
                onSetSort = {},
                onSetQuery = {},
                onBack = {},
            )
        }
        // The tabs lead with Stop, so a suspended app sits under "Other apps" and is named there.
        compose.onNodeWithText("Listed as Suspend").assertExists()
    }

    @Test
    fun `an app listed under another method is not moved by this tab`() {
        // Moving the method of a frozen app leaves the record naming a method that did not
        // freeze it, and the lift that follows then runs nothing. It is changed where it
        // is listed, not here.
        val calls = Calls()
        showScreen(
            calls,
            apps = listOf(frozen),
            methods = mapOf(frozen.packageName to FreezeMethod.SUSPEND),
            applied = emptySet(),
        )
        compose.onNodeWithText("frozen").performClick()
        assertEquals(null, calls.added)
        assertEquals(null, calls.removed)
    }

    @Test
    fun `the source filter keeps system and user apps apart`() {
        val system = app("system").copy(system = true)
        showScreen(
            apps = listOf(frozen, system),
            methods = emptyMap(),
            applied = emptySet(),
            systemAppsEnabled = true,
            sourceFilter = FreezeSourceFilter.SYSTEM,
        )
        compose.onNodeWithText("system").assertExists()
        compose.onNodeWithText("frozen").assertDoesNotExist()
    }

    @Test
    fun `the frozen filter drops everything that is not frozen`() {
        showScreen(stateFilter = FreezeStateFilter.FROZEN)
        compose.onNodeWithText("In the list (1)").assertExists()
        compose.onNodeWithText("frozen").assertExists()
        compose.onNodeWithText("pending").assertDoesNotExist()
        compose.onNodeWithText("free").assertDoesNotExist()
    }

    @Test
    fun `the search narrows by name`() {
        showScreen(query = "pen")
        compose.onNodeWithText("pending").assertExists()
        compose.onNodeWithText("free").assertDoesNotExist()
    }

    @Test
    fun `a search that matches nothing says so`() {
        showScreen(query = "zzz")
        compose.onNodeWithText("No app matches the search").assertExists()
    }
}
