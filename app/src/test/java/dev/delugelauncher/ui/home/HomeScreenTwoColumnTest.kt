package dev.delugelauncher.ui.home

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two-column favorites: an odd favorite left at the tail — and every widget — has no
 * neighbour for the other half, so it must render alone. It used to be read as `pair[1]`
 * regardless, which is what took the app down the moment the two-column switch was turned on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class HomeScreenTwoColumnTest {

    @get:Rule
    val compose = createComposeRule()

    private fun app(id: String) = AppInfo(
        componentName = ComponentName("com.example.$id", "com.example.$id.Main"),
        label = id,
    )

    @Test
    fun oddFavoriteTailRendersAlone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Touch the string table on the same context the screen uses, so a missing resource
        // fails here rather than at first composition.
        context.getString(dev.delugelauncher.R.string.home_drag_handle)
        // Three favorites: one full pair and one left over.
        val apps = listOf("a", "b", "c").map(::app)
        val favorites = apps.map { FavoriteEntry.App(it) }

        compose.setContent {
            var editMode by remember { mutableStateOf(false) }
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
                // Portrait, and the portrait switch on: this is the path the bug was in.
                homeTwoColumnPortrait = true,
                homeTwoColumnLandscape = false,
                disguiseNames = emptyMap(),
                folderDisguise = emptyMap(),
                widgetPosition = 0,
                widgetHeightDp = 180,
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
        // Composition throws on the buggy branch, so reaching here is the first assertion.
        compose.waitForIdle()

        compose.onNodeWithText("a").assertExists()
        compose.onNodeWithText("c").assertExists()
    }
}
