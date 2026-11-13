package dev.delugelauncher.ui.home

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.delugelauncher.R
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.CornerAction
import dev.delugelauncher.data.Folder
import dev.delugelauncher.data.HapticFeedback
import dev.delugelauncher.data.HomeAlignment
import dev.delugelauncher.data.HomePaddings
import dev.delugelauncher.data.IconSide
import dev.delugelauncher.data.PaddingSlot
import dev.delugelauncher.data.QuickLaunchSlot
import dev.delugelauncher.data.ShortcutSwipe
import dev.delugelauncher.widget.WidgetSlotActions
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A folder opened near the bottom of the screen used to expand its members past the
 * bottom edge, out of view. The list now scrolls them fully into view — and a folder
 * at the top of the list, which has room below it, is left where it is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class HomeFolderExpandScrollTest {

    @get:Rule
    val compose = createComposeRule()

    /** Height of the window the home screen fills. */
    private var rootHeightDp: Dp = Dp.Unspecified

    private fun app(id: String) = AppInfo(
        componentName = ComponentName("com.example.$id", "com.example.$id.Main"),
        label = id,
    )

    private val folderName = "Bottom Folder"
    private val members = listOf("m1", "m2", "m3").map(::app)

    /** Thirty rows above it: the folder row itself is still on screen, its members are not. */
    private fun bottomFolders() = (1..30).map { app("a$it") }
        .map(FavoriteEntry::App) + FavoriteEntry.FolderRef(
        Folder(id = "f", name = folderName, apps = members.map { it.key }),
    )

    /** The home screen over the given favorites, not in edit layout. */
    private fun showHome(favorites: List<FavoriteEntry>) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Touch the string table on the same context the screen uses, so a missing resource
        // fails here rather than at first composition.
        context.getString(R.string.home_folder_choose_apps)
        val apps = favorites.flatMap {
            when (it) {
                is FavoriteEntry.App -> listOf(it.app)
                is FavoriteEntry.FolderRef -> emptyList()
            }
        } + members
        compose.setContent {
            rootHeightDp = LocalConfiguration.current.screenHeightDp.dp
            HomeScreen(
                homeIntentTick = 0,
                closeFolderOnLaunch = true,
                showFavoriteIcons = false,
                showEmptyHint = false,
                avoidStatusBar = false,
                markWorkApps = false,
                textShadow = false,
                compactTapArea = false,
                textShadowStrength = 0,
                favoritesReorderable = false,
                shortcutSwipe = ShortcutSwipe.OFF,
                favorites = favorites,
                nameOverrides = emptyMap(),
                iconSizeDp = 48,
                labelSizeSp = 12,
                itemSpacingDp = 8,
                sidePaddingDp = 16,
                widgetSidePaddingDp = 8,
                widgetOffsetXDp = 0,
                onSetSidePadding = {},
                onSetWidgetSidePadding = {},
                onSetWidgetOffsetX = {},
                paddings = HomePaddings.Default,
                widgetIds = emptyList(),
                homeTwoColumnPortrait = false,
                homeTwoColumnLandscape = false,
                disguiseNames = emptyMap(),
                folderDisguise = emptyMap(),
                widgetPosition = 0,
                widgetHeightDp = 180,
                widgetsStacked = false,
                widgetTick = 0,
                hapticFeedback = HapticFeedback.OFF,
                nowPlayingEnabled = false,
                nowPlayingHeightDp = 0,
                onResizeNowPlaying = {},
                widgetActions = WidgetSlotActions(
                    onAddWidget = {},
                    onRemoveWidget = {},
                    onWidgetSettings = {},
                    onAppInfo = {},
                    onResize = {},
                    onOpenSettings = {},
                ),
                onLaunch = {},
                onRemoveFavorite = {},
                onHideFavorite = {},
                onOpenFolderApp = {},
                onRemoveFromFolder = { _, _ -> },
                onRenameFolder = { _, _ -> },
                onChangeFolderIcon = {},
                onResetFolderIcon = {},
                onDeleteFolder = {},
                onManageFolder = {},
                onMoveToFolder = {},
                appsByKey = apps.associateBy { it.key },
                onReorderHome = { _, _ -> },
                onCommitPadding = { _: PaddingSlot, _: Int -> },
                onFavoritesBoundsChanged = { _, _ -> },
                onFavoritesCleared = {},
                nowPlayingHasContent = false,
                contentColor = Color.White,
                showFavoriteLabels = true,
                dimPausedApps = true,
                pinchToResize = false,
                onSetIconSize = {},
                lockedApps = emptySet(),
                onToggleLock = {},
                alignment = HomeAlignment.LEFT,
                iconSide = IconSide.LEFT,
                editMode = false,
                onEditModeChange = {},
                onEditScrubBand = {},
                centerFavorites = false,
                swipeUpOpensAppList = false,
                onSwipeUpDrag = { _, _ -> },
                onSwipeUpEnd = {},
                quickLaunchEnabled = false,
                onQuickLaunch = { _: QuickLaunchSlot -> },
                onPeekStatusBar = {},
                onExpandShade = {},
                onDoubleTapBackground = null,
                onManageFavorites = {},
                onChangeWallpaper = {},
                onSetName = { _, _ -> },
                onChangeIcon = {},
                onAppInfo = {},
                onUninstall = {},
                onUnpinShortcut = {},
                onOpenSettings = {},
                cornerButtonApp = null,
                cornerButtonIsSearch = false,
                cornerTapAction = CornerAction.DO_NOTHING,
                cornerSwipeAction = CornerAction.DO_NOTHING,
                onCornerAction = {},
                onCornerSwipeAction = {},
                cornerPos = null,
                onSetCornerPos = { _, _ -> },
                onCornerBoundsChanged = { _: Rect? -> },
                cornerSwipeBound = false,
                onCornerOwnershipChanged = {},
            )
        }
        compose.waitForIdle()
    }

    @Test
    fun bottomFolderExpandScrollsMembersIntoView() {
        showHome(bottomFolders())
        val folderBefore = compose.onNodeWithText(folderName).getBoundsInRoot()

        // The reveal waits the expansion out frame by frame before it scrolls, and a
        // frame wait is not recomposition: waitForIdle alone comes back before it ends.
        // The clock is therefore driven by hand past both.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(folderName).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        // Every member now fits above the bottom edge. Two dp of slack for the
        // rounding of a placement target that lands exactly on it.
        for (member in members) {
            val bounds = compose.onNodeWithText(member.label).getBoundsInRoot()
            assertTrue(
                "${member.label} bottom=${bounds.bottom} over height=$rootHeightDp",
                bounds.bottom <= rootHeightDp + 2.dp,
            )
            assertTrue("${member.label} top=${bounds.top}", bounds.top >= 0.dp)
        }
        // The list moved up to make room — the row itself climbed, it did not just
        // grow taller where it sat.
        val folderAfter = compose.onNodeWithText(folderName).getBoundsInRoot()
        assertTrue(
            "folder top went $folderBefore -> $folderAfter",
            folderAfter.top < folderBefore.top - 1.dp,
        )
    }

    @Test
    fun topFolderExpandLeavesListWhereItIs() {
        val favorites = listOf(FavoriteEntry.FolderRef(
            Folder(id = "f", name = folderName, apps = members.map { it.key }),
        )) + (1..3).map { app("b$it") }.map(FavoriteEntry::App)
        showHome(favorites)
        val folderBefore = compose.onNodeWithText(folderName).getBoundsInRoot()

        compose.onNodeWithText(folderName).performClick()
        compose.waitForIdle()

        // A folder with the whole screen below it needs no scroll: the list stays put.
        val folderAfter = compose.onNodeWithText(folderName).getBoundsInRoot()
        assertTrue(
            "top moved $folderBefore -> $folderAfter",
            abs(folderBefore.top.value - folderAfter.top.value) <= 1f,
        )
        for (member in members) {
            val bounds = compose.onNodeWithText(member.label).getBoundsInRoot()
            assertTrue(
                "${member.label} bottom=${bounds.bottom} over height=$rootHeightDp",
                bounds.bottom <= rootHeightDp,
            )
        }
    }
}
