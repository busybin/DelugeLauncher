// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.freeze

import android.content.ComponentName
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.FreezeAccess
import dev.delugelauncher.data.FreezeMethod
import dev.delugelauncher.data.FreezeSort
import dev.delugelauncher.data.FreezeSourceFilter
import dev.delugelauncher.data.FreezeStateFilter
import dev.delugelauncher.data.FreezeStatus
import dev.delugelauncher.ui.common.BrickWarningDialog
import dev.delugelauncher.ui.common.BrickWarningState
import dev.delugelauncher.ui.common.LocalBrickWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The page as a table: one card per app, the method picker, the permanent switch and the
 * freeze switch all on the row.
 *
 * What matters here is the rules the table keeps, not the drawing — which rows the default
 * view shows, that choosing a method is not freezing, and that the permanent flag only
 * reaches a row that is actually on the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xxhdpi")
class FreezeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun app(
        id: String,
        system: Boolean = false,
        userSerial: Long = 0L,
        work: Boolean = false,
    ) = AppInfo(
        componentName = ComponentName("com.example.$id", "com.example.$id.Main"),
        label = id,
        system = system,
        userSerial = userSerial,
        work = work,
    )

    private val frozen = app("frozen")
    private val pending = app("pending")
    private val free = app("free")
    private val settings = app("settings", system = true)
    private val workChat = app("workchat", userSerial = 10L, work = true)

    private val plan = mapOf(
        frozen.packageName to FreezeMethod.SUSPEND,
        pending.packageName to FreezeMethod.SUSPEND,
    )

    private class Calls {
        var added: AppInfo? = null
        var addedMethod: FreezeMethod? = null
        var applied: Pair<AppInfo, Boolean>? = null
        var permanent: Pair<AppInfo, Boolean>? = null
        var frozenMany: List<AppInfo>? = null
        var addedMany: List<AppInfo>? = null
        var removedMany: List<AppInfo>? = null
        var addedAndFrozen: Pair<AppInfo, FreezeMethod>? = null
        var defaultMethod: FreezeMethod? = null
        var defaultCleared = false
    }

    private fun showScreen(
        calls: Calls = Calls(),
        apps: List<AppInfo> = listOf(frozen, pending, free),
        methods: Map<String, FreezeMethod> = plan,
        applied: Set<String> = setOf(frozen.packageName),
        permanent: Set<String> = setOf(frozen.packageName),
        sourceFilter: FreezeSourceFilter = FreezeSourceFilter.USER,
        stateFilter: FreezeStateFilter = FreezeStateFilter.ALL,
        defaultMethod: FreezeMethod? = FreezeMethod.SUSPEND,
        query: String = "",
        protected: Set<String> = emptySet(),
        brick: BrickWarningState = BrickWarningState(emptySet()),
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalBrickWarning provides brick) {
            BrickWarningDialog(brick)
            FreezeAppsScreen(
                allApps = apps,
                freezeMethods = methods,
                freezeApplied = applied,
                freezePermanent = permanent,
                protectedPackages = protected,
                nameOverrides = emptyMap(),
                iconSizeDp = 48,
                status = FreezeStatus(
                    checked = true,
                    access = FreezeAccess.ROOT,
                    rootGranted = true,
                ),
                systemAppsEnabled = false,
                sourceFilter = sourceFilter,
                stateFilter = stateFilter,
                sort = FreezeSort.NAME_ASC,
                query = query,
                defaultMethod = defaultMethod,
                autoEnabled = false,
                autoDelaySeconds = 60,
                unfreezeOnUnlock = false,
                autoSkipCharging = true,
                autoSkipForeground = true,
                autoSkipNotifications = true,
                protectEssentials = true,
                onAddToPlan = { target, method ->
                    calls.added = target
                    calls.addedMethod = method
                },
                onAddAndFreeze = { target, method -> calls.addedAndFrozen = target to method },
                onSetApplied = { target, on -> calls.applied = target to on },
                onSetMethod = { _, _ -> },
                onSetPermanent = { target, on -> calls.permanent = target to on },
                onFreezeManySelected = { calls.frozenMany = it },
                onUnfreezeManySelected = {},
                onAddManySelected = { apps, _ -> calls.addedMany = apps },
                onRemoveManySelected = { calls.removedMany = it },
                onSetPermanentMany = { _, _ -> },
                onRemoveFromPlan = {},
                onFreezeAll = {},
                onUnfreezeAll = {},
                onSetAutoEnabled = {},
                onSetAutoDelay = {},
                onSetUnfreezeOnUnlock = {},
                onSetAutoSkipCharging = {},
                onSetAutoSkipForeground = {},
                onSetAutoSkipNotifications = {},
                onSetProtectEssentials = {},
                onSetSystemAppsEnabled = {},
                onExportPlan = {},
                onImportPlan = {},
                onSetSourceFilter = {},
                onSetStateFilter = {},
                onSetSort = {},
                onSetQuery = {},
                onSetChannel = { _, _ -> },
                onSetDefaultMethod = { calls.defaultMethod = it },
                onNavigateAccess = {},
                onNavigateMethods = {},
                onBack = {},
            )
            }
        }
    }

    @Test
    fun `a package with two launcher activities is one row`() {
        // An app can register two launcher activities; both arrive as separate [AppInfo]s and
        // the table keys its rows by package, so keeping both would hand the list the same
        // item key twice and throw as soon as the second row was composed.
        val radio = app("radio")
        val second = AppInfo(
            componentName = ComponentName("com.example.radio", "com.example.radio.RadioActivity"),
            label = "radio",
        )

        showScreen(apps = listOf(radio, second))

        compose.onAllNodesWithText("radio").assertCountEquals(1)
    }

    @Test
    fun `the default view leaves system apps and other profiles out`() {
        showScreen(apps = listOf(frozen, pending, free, settings, workChat))

        compose.onNodeWithText("frozen").assertExists()
        compose.onNodeWithText("free").assertExists()
        compose.onNodeWithText("settings").assertDoesNotExist()
        compose.onNodeWithText("workchat", substring = true).assertDoesNotExist()
    }

    @Test
    fun `other profiles are reachable through their own filter`() {
        showScreen(apps = listOf(frozen, free, workChat), sourceFilter = FreezeSourceFilter.OTHER_PROFILE)

        compose.onNodeWithText("workchat", substring = true).assertExists()
        compose.onNodeWithText("free").assertDoesNotExist()
    }

    @Test
    fun `freezing an app that holds the phone together asks first`() {
        val calls = Calls()
        val system = app("systemui")
        val brick = BrickWarningState(setOf(system.packageName))
        showScreen(
            calls = calls,
            apps = listOf(system),
            methods = mapOf(system.packageName to FreezeMethod.SUSPEND),
            applied = emptySet(),
            permanent = emptySet(),
            brick = brick,
        )

        compose.onNodeWithTag("freeze-row-switch-${system.freezeKey}").performClick()

        // Nothing happened yet: the row's own switch is what asked.
        assertEquals(null, calls.applied)
        assertTrue(brick.pending != null)
        compose.onNodeWithText("Freeze anyway").performClick()
        assertEquals(system to true, calls.applied)
    }

    @Test
    fun `the switch freezes and lifts one row only`() {
        val calls = Calls()
        showScreen(calls = calls, apps = listOf(frozen))

        compose.onNodeWithTag("freeze-row-switch-${frozen.freezeKey}").performClick()

        assertEquals(frozen to false, calls.applied)
    }

    @Test
    fun `an unlisted row says none instead of borrowing the default method`() {
        showScreen(apps = listOf(free), methods = emptyMap(), defaultMethod = FreezeMethod.DISABLE)

        // The row, not the whole page: the channel tile also names the default method.
        compose.onNodeWithTag("freeze-list")
            .performScrollToNode(hasTestTag("freeze-row-switch-${free.freezeKey}"))
        compose.onNodeWithTag("freeze-row-method-${free.freezeKey}", useUnmergedTree = true)
            .assertTextEquals("None")
    }

    @Test
    fun `the table header names the three decisions`() {
        showScreen(apps = listOf(frozen))

        compose.onNodeWithTag("freeze-table-header").assertExists()
        compose.onNodeWithText("Freeze switch").assertExists()
        compose.onNodeWithText("App").assertExists()
        compose.onNodeWithText("Method").assertExists()
    }

    @Test
    fun `a protected row cannot be frozen or made permanent`() {
        val calls = Calls()
        showScreen(calls = calls, apps = listOf(pending), protected = setOf(pending.packageName))

        compose.onNodeWithTag("freeze-row-switch-${pending.freezeKey}").performClick()
        compose.onNodeWithTag("freeze-row-permanent-${pending.freezeKey}").performClick()

        assertEquals(null, calls.applied)
        assertEquals(null, calls.permanent)
    }

    @Test
    fun `choosing a method on an unlisted row joins the list and does not freeze`() {
        val calls = Calls()
        showScreen(calls = calls, apps = listOf(free))

        compose.onNodeWithText("None").performClick()
        compose.onNodeWithText("Disable").performClick()

        assertEquals(free, calls.added)
        assertEquals(FreezeMethod.DISABLE, calls.addedMethod)
        assertEquals(null, calls.applied)
    }

    @Test
    fun `the permanent switch asks for exactly that row`() {
        val calls = Calls()
        showScreen(calls = calls, apps = listOf(pending), permanent = emptySet())

        compose.onNodeWithTag("freeze-row-permanent-${pending.freezeKey}").performClick()

        assertEquals(pending to true, calls.permanent)
    }

    @Test
    fun `select all and freeze stay inside the current view and the list`() {
        val calls = Calls()
        showScreen(calls = calls, apps = listOf(frozen, pending, free))

        compose.onNodeWithText("free").performTouchInput { longClick() }
        compose.onNodeWithText("Select all").performClick()
        compose.onAllNodesWithText("Freeze now")[0].performClick()

        // All three rows are selected, but only the two on the list can be frozen.
        assertEquals(setOf(frozen, pending), calls.frozenMany?.toSet())
    }

    @Test
    fun `bulk add only takes the rows that are not on the list yet`() {
        val calls = Calls()
        showScreen(calls = calls, apps = listOf(frozen, free))

        compose.onNodeWithText("free").performTouchInput { longClick() }
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("Add to the list").performClick()

        assertEquals(listOf(free), calls.addedMany)
    }

    @Test
    fun `bulk remove takes every listed row in the selection`() {
        val calls = Calls()
        showScreen(calls = calls, apps = listOf(frozen, pending))

        compose.onNodeWithText("frozen").performTouchInput { longClick() }
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("Remove from the list").performClick()

        assertEquals(setOf(frozen, pending), calls.removedMany?.toSet())
    }
}
