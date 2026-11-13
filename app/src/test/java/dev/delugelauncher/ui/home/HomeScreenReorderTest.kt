package dev.delugelauncher.ui.home

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import dev.delugelauncher.R
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.CornerAction
import dev.delugelauncher.data.HapticFeedback
import dev.delugelauncher.data.HomeAlignment
import dev.delugelauncher.data.HomePaddings
import dev.delugelauncher.data.IconSide
import dev.delugelauncher.data.PaddingSlot
import dev.delugelauncher.data.QuickLaunchSlot
import dev.delugelauncher.data.ShortcutSwipe
import dev.delugelauncher.widget.WidgetSlotActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real home screen in edit layout: dragging a favorite's grab handle past its neighbour
 * swaps them, and letting go commits the order it reached.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class HomeScreenReorderTest {

    @get:Rule
    val compose = createComposeRule()

    private val commits = mutableListOf<List<String>>()

    private fun app(id: String) = AppInfo(
        componentName = ComponentName("com.example.$id", "com.example.$id.Main"),
        label = id,
    )

    @Test
    fun dragHandleSwapsAndCommits() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val handleDesc = context.getString(R.string.home_drag_handle)
        val apps = listOf("a", "b", "c", "d").map(::app)
        val favorites = apps.map { FavoriteEntry.App(it) }

        compose.setContent {
            var editMode by remember { mutableStateOf(true) }
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
                favoritesReorderable = true,
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
                // Last, so the favorites the drag needs are the rows on screen.
                widgetPosition = 4,
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
                onReorderHome = { keys, _ -> commits += keys },
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
                editMode = editMode,
                onEditModeChange = { editMode = it },
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

        // Second favorite's handle. One injected batch: the framework ends a gesture whose
        // pointer leaves the grabbed node, which a real finger is not bound by.
        compose.onAllNodesWithContentDescription(handleDesc, useUnmergedTree = true)[1]
            .performTouchInput {
                down(center)
                moveBy(Offset(0f, 40f))
                moveBy(Offset(0f, 40f))
                moveBy(Offset(0f, 40f))
                up()
            }
        compose.waitForIdle()

        assertEquals("commits=$commits", 1, commits.size)
        // Keys, not labels: what the store is handed is the app keys.
        assertEquals(
            listOf(apps[0].key, apps[2].key, apps[1].key, apps[3].key),
            commits.first(),
        )
    }
}
