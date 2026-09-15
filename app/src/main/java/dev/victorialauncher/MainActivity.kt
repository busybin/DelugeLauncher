// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import dev.victorialauncher.data.AppFont
import dev.victorialauncher.service.StatusBarFader
import dev.victorialauncher.ui.VictoriaNavHost
import dev.victorialauncher.ui.common.IconConfig
import dev.victorialauncher.ui.common.LocalIconConfig
import dev.victorialauncher.ui.theme.VictoriaTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    /** Bumped whenever HOME is pressed while we're already showing, so overlays can close. */
    private var homeIntentTick by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as VictoriaApp

        setContent {
            val hideStatusBar by app.prefs.hideStatusBar.collectAsState(initial = false)
            val hideStatusBarAppList by app.prefs.hideStatusBarAppList.collectAsState(initial = false)
            val peekSeconds by app.prefs.statusBarPeekSeconds.collectAsState(initial = 5)
            // A short pull-down peeks the status bar, then it slides away again.
            var statusBarPeek by remember { mutableStateOf(false) }
            // The two surfaces choose separately, so the overlay can keep the bar the home
            // screen hides.
            var appListOpen by remember { mutableStateOf(false) }
            LaunchedEffect(statusBarPeek, peekSeconds) {
                if (statusBarPeek) {
                    delay(peekSeconds * 1000L)
                    statusBarPeek = false
                }
            }
            val hideHere = if (appListOpen) hideStatusBarAppList else hideStatusBar
            val statusBarVisible = !hideHere || statusBarPeek
            // Keyed on the answer, not on what went into it. Re-asking for a state the bar is
            // already in restarts the fade, and a fade out begins by holding the bar fully
            // shown — so opening the list with both set to hide flashed it into view.
            LaunchedEffect(statusBarVisible) {
                StatusBarFader.setVisible(window, visible = statusBarVisible)
            }

            val font by app.prefs.font.collectAsState(initial = AppFont.SYSTEM)
            val fontFile by app.prefs.fontFile.collectAsState(initial = null)
            val iconPackPackage by app.prefs.iconPackPackage.collectAsState(initial = null)
            val iconOverrides by app.prefs.iconOverrides.collectAsState(initial = emptyMap())
            val showAppIcons by app.prefs.showAppIcons.collectAsState(initial = true)
            val iconConfig = remember(iconPackPackage, iconOverrides, showAppIcons) {
                IconConfig(iconPackPackage, iconOverrides, showAppIcons)
            }

            VictoriaTheme(font = font, fontFile = fontFile) {
                CompositionLocalProvider(LocalIconConfig provides iconConfig) {
                    VictoriaNavHost(
                        app = app,
                        homeIntentTick = homeIntentTick,
                        font = font,
                        hideStatusBar = hideStatusBar,
                        hideStatusBarAppList = hideStatusBarAppList,
                        iconPackPackage = iconPackPackage,
                        iconOverrides = iconOverrides,
                        onPeekStatusBar = { statusBarPeek = true },
                        onAppListVisibleChange = { appListOpen = it },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing HOME re-delivers the intent to us; treat it as "go back to the home screen".
        homeIntentTick++
    }

    override fun onStart() {
        super.onStart()
        (application as VictoriaApp).widgetHost.startListening()
    }

    override fun onStop() {
        (application as VictoriaApp).widgetHost.stopListening()
        // The fader holds a static controller for this window; don't outlive the Activity.
        StatusBarFader.release()
        super.onStop()
    }
}