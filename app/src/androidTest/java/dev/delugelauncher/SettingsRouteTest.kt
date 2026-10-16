package dev.delugelauncher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The settings screen, opened the way a person opens it: app list, then its own row.
 *
 * This screen is the widest combination in the app — 186 parameters, 91 of them callbacks.
 * Upgrading to AGP 9 / Compose 1.11 in October 2026 pushed its call site past the JVM's
 * 64 KB per-method limit (`Method too large: DelugeNavHost$lambda$…`), because Compose
 * generates roughly 600 bytes of lambda-memoisation bookkeeping per lambda argument.
 * The parameters were then moved into [dev.delugelauncher.ui.settings.SettingsScreenModel]
 * and the callbacks split across two builders.
 *
 * That is a change no compiler error would ever catch again — a mis-assigned callback
 * still compiles. So this test exists to keep the screen reachable and drawing.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRouteTest {

    @Before
    fun setUp() {
        LauncherTestUtils.setAsDefaultHome()
    }

    @Test
    fun tappingTheSettingsRowOpensTheSettingsScreen() {
        LauncherTestUtils.goHome()
        LauncherTestUtils.openAppList()
        // Wherever it sorts among what is installed, this brings the row on screen.
        LauncherTestUtils.filterAppList("Deluge")

        val row = LauncherTestUtils.uiDevice()
            .wait(Until.findObject(By.text("Deluge Launcher settings")), 5_000L)
            ?: error("the app list never showed a Deluge settings row")
        row.click()

        if (!LauncherTestUtils.waitForText("Search settings", 10_000L)) {
            val seen = LauncherTestUtils.uiDevice()
                .findObjects(By.clazz("android.widget.TextView"))
                .mapNotNull { it.text }
                .distinct()
                .take(40)
            error("the settings screen did not draw; on screen: $seen")
        }
    }
}
