// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import dev.delugelauncher.data.AppFont

fun AppFont.toFontFamily(): FontFamily = when (this) {
    AppFont.SYSTEM -> FontFamily.Default
    AppFont.SANS_SERIF -> FontFamily.SansSerif
    AppFont.SERIF -> FontFamily.Serif
    AppFont.MONOSPACE -> FontFamily.Monospace
    // Without the file this cannot be resolved here; callers that have it use fontFamilyOf.
    AppFont.CUSTOM -> FontFamily.Default
}

/**
 * The typeface to draw with, including one the user supplied.
 *
 * The file is loaded once per path rather than per text: Typeface.createFromFile parses the
 * whole font, and the app list asks for this on every row. A file that will not parse falls
 * back to the default rather than failing — a font picked months ago may since have gone.
 */
fun fontFamilyOf(font: AppFont, fontFile: String?): FontFamily {
    if (font != AppFont.CUSTOM) return font.toFontFamily()
    val path = fontFile ?: return FontFamily.Default
    return customFontCache.getOrPut(path) {
        runCatching { FontFamily(Typeface.createFromFile(File(path))) }.getOrDefault(FontFamily.Default)
    }
}

private val customFontCache = mutableMapOf<String, FontFamily>()

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF00325B),
    primaryContainer = Color(0xFF00497F),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFB9C7DC),
    onSecondary = Color(0xFF243141),
    background = Color.Transparent,
    surface = Color(0xFF101418),
    surfaceContainer = Color(0xFF1B2027),
    surfaceContainerHigh = Color(0xFF262B33),
    surfaceContainerHighest = Color(0xFF31363E),
    onSurface = Color(0xFFE2E2E9),
    onSurfaceVariant = Color(0xFFC2C7CF),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF565F71),
    onSecondary = Color(0xFFFFFFFF),
    background = Color.Transparent,
    surface = Color(0xFFF8F9FF),
    surfaceContainer = Color(0xFFEFF3F9),
    surfaceContainerHigh = Color(0xFFE7ECF4),
    surfaceContainerHighest = Color(0xFFDFE5EF),
    onSurface = Color(0xFF191C20),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
)

/**
 * Corners, so a menu is as round as the things it opens over.
 *
 * Material gives a menu its extraSmall shape, which is 4dp — against rows and cards drawn here
 * at 18 to 22dp it read as a different app's menu. Everything the app draws by hand sits in
 * that range, so the scale is set to meet it rather than each menu being told separately.
 */
private val DelugeShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(20.dp),
)

@Composable
fun DelugeTheme(
    font: AppFont = AppFont.SYSTEM,
    fontFile: String? = null,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    // Deluge blue, always, on every Android version: the identity of this launcher is its
    // colour, and the wallpaper-derived palette would put somebody else's in its place.
    // The two hand-written schemes above are the whole palette.
    val colors = (if (darkTheme) DarkColors else LightColors).copy(background = Color.Transparent)
    val baseTypography = MaterialTheme.typography
    val fontFamily = fontFamilyOf(font, fontFile)
    val typography = baseTypography.copy(
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = fontFamily),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = fontFamily),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = fontFamily),
    )
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = DelugeShapes,
        content = content,
    )
}

/**
 * The shadow to put behind the launcher's own text, or null for none.
 *
 * Only behind light text: a shadow under dark text on a pale wallpaper muddies the letters
 * instead of separating them. The setting asks for contrast, and this is where it is decided
 * whether a shadow would actually gain any.
 */
@Composable
internal fun launcherTextShadow(enabled: Boolean, textColor: Color, strength: Int = DEFAULT_SHADOW_STRENGTH): Shadow? {
    if (!enabled) return null
    if (textColor.luminance() < 0.5f) return null
    // One number moving darkness and spread together. Five is what it always was, so the
    // default reads exactly as before; either side of it is darker and wider, or lighter and
    // tighter, which is the one thing anyone adjusting it is after.
    val k = strength.coerceIn(SHADOW_STRENGTH_RANGE) / DEFAULT_SHADOW_STRENGTH.toFloat()
    return Shadow(
        color = Color.Black.copy(alpha = (0.55f * k).coerceIn(0.1f, 0.95f)),
        offset = Offset(0f, 1f),
        blurRadius = 4f * k,
    )
}

const val DEFAULT_SHADOW_STRENGTH = 5
val SHADOW_STRENGTH_RANGE = 1..10
