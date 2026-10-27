// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import dev.delugelauncher.data.AppFont
import dev.delugelauncher.data.ColorSchemeChoice

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

/**
 * The same choice as a platform typeface, for the views a widget is drawn with.
 *
 * A RemoteViews cannot carry a typeface, let alone a font file the user picked; the host
 * inflates our clock, so the font is set on its views after each update instead.
 */
fun widgetTypeface(font: AppFont, fontFile: String?): Typeface = when (font) {
    AppFont.SYSTEM -> Typeface.DEFAULT
    AppFont.SANS_SERIF -> Typeface.SANS_SERIF
    AppFont.SERIF -> Typeface.SERIF
    AppFont.MONOSPACE -> Typeface.MONOSPACE
    AppFont.CUSTOM -> fontFile?.let { path ->
        customTypefaceCache.getOrPut(path) {
            runCatching { Typeface.createFromFile(File(path)) }.getOrDefault(Typeface.DEFAULT)
        }
    } ?: Typeface.DEFAULT
}

private val customTypefaceCache = mutableMapOf<String, Typeface>()

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

private val PurpleDark = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    background = Color.Transparent,
    surface = Color(0xFF141218),
    surfaceContainer = Color(0xFF1D1B20),
    surfaceContainerHigh = Color(0xFF28232A),
    surfaceContainerHighest = Color(0xFF322F35),
    onSurface = Color(0xFFE6E0E9),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
)

private val PurpleLight = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    background = Color.Transparent,
    surface = Color(0xFFFFFBFE),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
)

private val GreenDark = darkColorScheme(
    primary = Color(0xFFB1D18A),
    onPrimary = Color(0xFF1F3701),
    primaryContainer = Color(0xFF354E16),
    onPrimaryContainer = Color(0xFFCDEDA3),
    secondary = Color(0xFFBFCBAD),
    onSecondary = Color(0xFF263417),
    background = Color.Transparent,
    surface = Color(0xFF11140E),
    surfaceContainer = Color(0xFF1A1E18),
    surfaceContainerHigh = Color(0xFF242824),
    surfaceContainerHighest = Color(0xFF2F332D),
    onSurface = Color(0xFFE2E4D9),
    onSurfaceVariant = Color(0xFFC5C8BA),
    outline = Color(0xFF8F9285),
    outlineVariant = Color(0xFF44483D),
)

private val GreenLight = lightColorScheme(
    primary = Color(0xFF4C662B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEDA3),
    onPrimaryContainer = Color(0xFF102000),
    secondary = Color(0xFF5A6148),
    onSecondary = Color(0xFFFFFFFF),
    background = Color.Transparent,
    surface = Color(0xFFF9FAEF),
    surfaceContainer = Color(0xFFEDEFE4),
    surfaceContainerHigh = Color(0xFFE7E9DE),
    surfaceContainerHighest = Color(0xFFE2E4D9),
    onSurface = Color(0xFF1A1C16),
    onSurfaceVariant = Color(0xFF44483D),
    outline = Color(0xFF75796C),
    outlineVariant = Color(0xFFC5C8BA),
)

private val RedDark = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB7),
    onSecondary = Color(0xFF442926),
    background = Color.Transparent,
    surface = Color(0xFF1A1110),
    surfaceContainer = Color(0xFF221918),
    surfaceContainerHigh = Color(0xFF2D2322),
    surfaceContainerHighest = Color(0xFF382E2C),
    onSurface = Color(0xFFF1DFDB),
    onSurfaceVariant = Color(0xFFD7C2BD),
    outline = Color(0xFFA08C88),
    outlineVariant = Color(0xFF534340),
)

private val RedLight = lightColorScheme(
    primary = Color(0xFFBA1A1A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775651),
    onSecondary = Color(0xFFFFFFFF),
    background = Color.Transparent,
    surface = Color(0xFFFFFBFF),
    surfaceContainer = Color(0xFFFDF0EE),
    surfaceContainerHigh = Color(0xFFF7EAE7),
    surfaceContainerHighest = Color(0xFFF1DFDB),
    onSurface = Color(0xFF201A19),
    onSurfaceVariant = Color(0xFF534340),
    outline = Color(0xFF857370),
    outlineVariant = Color(0xFFD8C2BC),
)

private val OrangeDark = darkColorScheme(
    primary = Color(0xFFFFB787),
    onPrimary = Color(0xFF4D2600),
    primaryContainer = Color(0xFF6B3A05),
    onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFE2C4A4),
    onSecondary = Color(0xFF3E2D16),
    background = Color.Transparent,
    surface = Color(0xFF1A120B),
    surfaceContainer = Color(0xFF221A13),
    surfaceContainerHigh = Color(0xFF2D241C),
    surfaceContainerHighest = Color(0xFF383026),
    onSurface = Color(0xFFF0DFCE),
    onSurfaceVariant = Color(0xFFD3C4B2),
    outline = Color(0xFF9C8E7D),
    outlineVariant = Color(0xFF4F4539),
)

private val OrangeLight = lightColorScheme(
    primary = Color(0xFF8F4C00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2F1500),
    secondary = Color(0xFF6F5B40),
    onSecondary = Color(0xFFFFFFFF),
    background = Color.Transparent,
    surface = Color(0xFFFFF8F4),
    surfaceContainer = Color(0xFFFBEFE3),
    surfaceContainerHigh = Color(0xFFF5E9DD),
    surfaceContainerHighest = Color(0xFFF0DFCE),
    onSurface = Color(0xFF201A13),
    onSurfaceVariant = Color(0xFF4F4539),
    outline = Color(0xFF817567),
    outlineVariant = Color(0xFFD3C4B2),
)

private val YellowDark = darkColorScheme(
    primary = Color(0xFFC8CBA0),
    onPrimary = Color(0xFF303200),
    primaryContainer = Color(0xFF484A22),
    onPrimaryContainer = Color(0xFFE4E77A),
    secondary = Color(0xFFB1B189),
    onSecondary = Color(0xFF1D1F06),
    background = Color.Transparent,
    surface = Color(0xFF14130C),
    surfaceContainer = Color(0xFF1C1D16),
    surfaceContainerHigh = Color(0xFF26281F),
    surfaceContainerHighest = Color(0xFF313328),
    onSurface = Color(0xFFE5E3D5),
    onSurfaceVariant = Color(0xFFC9C7B4),
    outline = Color(0xFF92917E),
    outlineVariant = Color(0xFF494A3C),
)

private val YellowLight = lightColorScheme(
    primary = Color(0xFF5C5F00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E77A),
    onPrimaryContainer = Color(0xFF1B1C00),
    secondary = Color(0xFF5E5F44),
    onSecondary = Color(0xFFFFFFFF),
    background = Color.Transparent,
    surface = Color(0xFFFEFCF3),
    surfaceContainer = Color(0xFFF3F2E6),
    surfaceContainerHigh = Color(0xFFE7E9DE),
    surfaceContainerHighest = Color(0xFFE5E3D5),
    onSurface = Color(0xFF1B1C15),
    onSurfaceVariant = Color(0xFF494A3C),
    outline = Color(0xFF7A7B68),
    outlineVariant = Color(0xFFC9C7B4),
)

private val PinkDark = darkColorScheme(
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF5B1132),
    primaryContainer = Color(0xFF7A294A),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFE3BDC6),
    onSecondary = Color(0xFF422931),
    background = Color.Transparent,
    surface = Color(0xFF191114),
    surfaceContainer = Color(0xFF211A1D),
    surfaceContainerHigh = Color(0xFF2B2326),
    surfaceContainerHighest = Color(0xFF362E31),
    onSurface = Color(0xFFF0DEE2),
    onSurfaceVariant = Color(0xFFD5C2C7),
    outline = Color(0xFF9E8C90),
    outlineVariant = Color(0xFF514346),
)

private val PinkLight = lightColorScheme(
    primary = Color(0xFF9A405E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3F001D),
    secondary = Color(0xFF74565E),
    onSecondary = Color(0xFFFFFFFF),
    background = Color.Transparent,
    surface = Color(0xFFFFF8F9),
    surfaceContainer = Color(0xFFFBEDEF),
    surfaceContainerHigh = Color(0xFFF5E7EA),
    surfaceContainerHighest = Color(0xFFF0DEE2),
    onSurface = Color(0xFF201A1D),
    onSurfaceVariant = Color(0xFF514346),
    outline = Color(0xFF837377),
    outlineVariant = Color(0xFFD5C2C7),
)

private val TealDark = darkColorScheme(
    primary = Color(0xFF7ED9C9),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005049),
    onPrimaryContainer = Color(0xFF9EF1E1),
    secondary = Color(0xFFB0CCC5),
    onSecondary = Color(0xFF1C3530),
    background = Color.Transparent,
    surface = Color(0xFF0E1513),
    surfaceContainer = Color(0xFF161D1B),
    surfaceContainerHigh = Color(0xFF212825),
    surfaceContainerHighest = Color(0xFF2B3330),
    onSurface = Color(0xFFDEE5E0),
    onSurfaceVariant = Color(0xFFBEC9C2),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF3F4945),
)

private val TealLight = lightColorScheme(
    primary = Color(0xFF00776A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9EF1E1),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4C635D),
    onSecondary = Color(0xFFFFFFFF),
    background = Color.Transparent,
    surface = Color(0xFFF4FBF7),
    surfaceContainer = Color(0xFFEDF2EE),
    surfaceContainerHigh = Color(0xFFE7ECE9),
    surfaceContainerHighest = Color(0xFFDEE5E0),
    onSurface = Color(0xFF171D1B),
    onSurfaceVariant = Color(0xFF3F4945),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBEC9C2),
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
    scheme: ColorSchemeChoice = ColorSchemeChoice.DELUGE_BLUE,
    /** Non-null overrides the system: the night schedule decides, not the system's own. */
    forceDark: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val darkTheme = forceDark ?: isSystemInDarkTheme()
    val context = LocalContext.current
    // Deluge blue unless asked: it is this launcher's colour, and the wallpaper-derived
    // palette would put somebody else's in its place. Dynamic follows the wallpaper on
    // Android 12 and up, and falls back to blue below it where there is nothing to follow.
    val colors = when (scheme) {
        ColorSchemeChoice.DYNAMIC ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkColors else LightColors
            }
        ColorSchemeChoice.PURPLE -> if (darkTheme) PurpleDark else PurpleLight
        ColorSchemeChoice.GREEN -> if (darkTheme) GreenDark else GreenLight
        ColorSchemeChoice.RED -> if (darkTheme) RedDark else RedLight
        ColorSchemeChoice.ORANGE -> if (darkTheme) OrangeDark else OrangeLight
        ColorSchemeChoice.YELLOW -> if (darkTheme) YellowDark else YellowLight
        ColorSchemeChoice.PINK -> if (darkTheme) PinkDark else PinkLight
        ColorSchemeChoice.TEAL -> if (darkTheme) TealDark else TealLight
        ColorSchemeChoice.DELUGE_BLUE -> if (darkTheme) DarkColors else LightColors
    }.copy(background = Color.Transparent)
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
