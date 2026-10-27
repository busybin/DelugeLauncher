// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.delugelauncher.media.NotificationCountBus
import dev.delugelauncher.DelugeApp
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.EntryKind
import dev.delugelauncher.data.IconShape
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Per-app icon overrides are stored either as a gallery `content://` URI or as `pack:<pkg>:<drawable>`. */
const val ICON_PACK_OVERRIDE_PREFIX = "pack:"

fun encodePackOverride(packPackage: String, drawableName: String) =
    "$ICON_PACK_OVERRIDE_PREFIX$packPackage:$drawableName"

/**
 * Icons are rasterised once and cached. They used to be drawn by handing a Drawable to an
 * ImageView through AndroidView, which meant inflating a real Android View per row — far too
 * expensive for a list that rebuilds while scrubbing.
 *
 * The cache is bounded by *bytes*, not entry count. A fixed count is the wrong unit here: the
 * cache key includes the rasterised pixel size, so moving the icon-size slider adds a whole
 * new generation of bitmaps rather than replacing the old one, and at 96dp on a dense screen
 * a single icon is ~450 KB. Sizing by bytes against the heap keeps that bounded no matter how
 * many apps are installed or how large the icons are set.
 */
private object IconCache {
    private val cache = object : LruCache<String, ImageBitmap>(maxSizeKb()) {
        // Reported in KB so the running total cannot overflow an Int.
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            ((value.width.toLong() * value.height.toLong() * 4L) / 1024L).toInt().coerceAtLeast(1)
    }

    private fun maxSizeKb(): Int {
        val heapKb = Runtime.getRuntime().maxMemory() / 1024L
        return (heapKb / 8L).toInt().coerceIn(4 * 1024, 96 * 1024)
    }

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, value: ImageBitmap) = cache.put(key, value)

    /**
     * Marks a key most-recently-used without decoding anything: a [get] that drops its
     * answer. The warm pass ends with this for the icons on screen, so the long tail it
     * rasterised after them does not evict exactly those on a small heap.
     */
    fun touch(key: String) {
        cache.get(key)
    }

    /**
     * Dropped wholesale when a package is added, removed or changed: an app that ships a new
     * icon in an update keeps none of the key's components, so nothing else would invalidate
     * the stale bitmap.
     */
    fun clear() = cache.evictAll()

    /** Only one package's rows: a single update must not empty the whole cache. */
    fun clearPackage(packageName: String) {
        cache.snapshot().keys
            .filter { cacheKeyPackage(it) == packageName }
            .forEach { cache.remove(it) }
    }
}

/** The package a cache key belongs to: the app key is `package/class`, before the first `|`. */
private fun cacheKeyPackage(key: String): String {
    val head = key.substringBefore('|')
    if (head.startsWith("shortcut:")) return ""
    return head.substringBefore('/')
}

fun clearIconCache() = IconCache.clear()

/**
 * The icons on disk, so a dead process does not mean redrawing every app.
 *
 * The memory cache is per process: once the system reclaims it, the next cold start decodes
 * and draws a few hundred drawables before the first icon can show. These files survive
 * that — same key, PNG beside it — so the second cold start reads bytes instead of drawing
 * pictures.
 *
 * Bounded by last use: every hit touches its file, and the sweep drops the stalest until the
 * directory fits. Cleared wholesale beside the memory cache whenever packages change, for
 * the same reason — an app that ships a new icon in an update keeps none of the key's
 * components. That over-invalidates on any single install or uninstall; doing it per package
 * would mean threading the package name through every refresh, which is a bigger change than
 * the files it would save.
 */
private object IconDiskCache {
    private const val DIR = "icon_cache_v3"
    // Earlier naming or generation; dropped once rather than carried beside the new one.
    private val OLD_DIRS = listOf("icon_cache_v1", "icon_cache_v2")
    private const val MAX_BYTES = 48L * 1024L * 1024L

    // One mkdir per process: the directory does not move under us.
    private lateinit var dirFile: java.io.File

    private fun dir(context: Context): java.io.File {
        if (!::dirFile.isInitialized) {
            dirFile = java.io.File(context.filesDir, DIR).apply { mkdirs() }
            // v2 could hold the placeholder a hidden app was drawn with while it could not be
            // read; those keys are still the right ones, so the files have to go, not just age.
            OLD_DIRS.forEach { old ->
                runCatching { java.io.File(context.filesDir, old).deleteRecursively() }
            }
        }
        return dirFile
    }

    // The hex of a key, memoized: warming a few hundred icons used to hash every key twice
    // (once to read, once to write) plus a mkdir per lookup.
    private val hexCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private fun hex(key: String): String = hexCache.getOrPut(key) {
        if (hexCache.size > 4000) hexCache.clear()
        runCatching {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }.getOrNull() ?: key.hashCode().toString()
    }

    private fun packagePrefix(key: String): String =
        cacheKeyPackage(key).replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)

    private fun fileFor(context: Context, key: String): java.io.File {
        return java.io.File(dir(context), "${packagePrefix(key)}_${hex(key)}.png")
    }

    // Touching the mtime on every hit is a write syscall per icon per warm; one in ten keeps
    // the LRU honest for a tenth of the cost.
    private val touchCounter = java.util.concurrent.atomic.AtomicInteger(0)

    fun get(context: Context, key: String): ImageBitmap? {
        val file = fileFor(context, key)
        if (!file.exists()) return null
        return runCatching {
            if (touchCounter.incrementAndGet() % 10 == 0) {
                file.setLastModified(System.currentTimeMillis())
            }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }.getOrNull()
    }

    fun put(context: Context, key: String, bitmap: android.graphics.Bitmap) {
        runCatching {
            fileFor(context, key).outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    fun clear(context: Context) {
        runCatching { dir(context).deleteRecursively(); dir(context).mkdirs() }
    }

    /** Only one package's files: names carry their package prefix for exactly this. */
    fun clearPackage(context: Context, packageName: String) {
        val safe = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)
        runCatching {
            dir(context).listFiles { file -> file.name.startsWith(safe + "_") }
                ?.forEach { it.delete() }
        }
    }

    // Throttled: a readdir+stat of a few hundred files every warm is pure overhead while the
    // directory is nowhere near the cap. The cap is still enforced whenever it is checked.
    private var lastSweepMs = 0L

    /** Drops the stalest files until the directory fits. Runs rarely; keep it simple. */
    fun sweep(context: Context) {
        val now = System.currentTimeMillis()
        runCatching {
            val files = dir(context).listFiles() ?: return
            var total = files.sumOf { it.length() }
            if (total <= MAX_BYTES) {
                if (now - lastSweepMs < 12L * 60L * 60L * 1000L) return
            }
            lastSweepMs = now
            if (total <= MAX_BYTES) return
            files.sortedBy { it.lastModified() }.forEach { file ->
                if (total <= MAX_BYTES) return
                total -= file.length()
                file.delete()
            }
        }
    }
}

/**
 * Memory now and disk beside it: the memory cache must empty on this frame, so no row
 * draws a stale icon it already holds, while deleting a few hundred files can wait for
 * a background thread.
 */
fun clearIconDiskCache(context: Context) {
    IconCache.clear()
    val app = context.applicationContext
    if (app is DelugeApp) app.offMain { IconDiskCache.clear(app) }
    else IconDiskCache.clear(context)
}

/**
 * One package's icons, memory and disk, leaving the rest cached: an update or uninstall
 * touches one app, and the next warm must not redraw the other three hundred.
 */
fun clearIconCacheForPackage(context: Context, packageName: String) {
    IconCache.clearPackage(packageName)
    val app = context.applicationContext
    if (app is DelugeApp) app.offMain { IconDiskCache.clearPackage(app, packageName) }
    else IconDiskCache.clearPackage(context, packageName)
}

// The class name is in here for the rows whose key alone cannot tell two pictures apart: the
// private-space row keeps one key across locking and unlocking, and the padlock it draws is
// not the same picture either side of that. For an app it is what the key already says.
private fun iconCacheKey(
    app: AppInfo,
    iconPack: String?,
    override: String?,
    px: Int,
    style: IconStyle,
) = "${app.key}|${app.componentName.className}|$iconPack|$override|$px|${style.shape}|${style.themed}|${style.background}"

/** Everything about how an icon is drawn that is not the icon itself. */
@Immutable
data class IconStyle(
    val shape: IconShape,
    val themed: Boolean,
    val background: Int,
    val foreground: Int,
)

/**
 * Memory, then disk, then the drawable, with the last two off the main thread. A decode costs
 * an order of magnitude less than a rasterise, and the order is what makes the second cold
 * start fast rather than merely warm.
 *
 * The disk and rasterise half runs behind [iconDecodeGate], shared with the warm pass:
 * rasterising is CPU-bound, and a cold scrub used to fire one unbounded IO job per new row
 * — dozens of them at once, every core saturated exactly when the finger needed them.
 * The memory hit above stays outside the gate: it never blocks.
 */
private suspend fun loadIconBitmap(
    context: Context,
    delugeApp: DelugeApp,
    app: AppInfo,
    iconPack: String?,
    override: String?,
    px: Int,
    style: IconStyle,
    key: String,
): ImageBitmap? {
    IconCache.get(key)?.let { return it }
    return withContext(Dispatchers.IO) {
        iconDecodeGate.withPermit {
            IconDiskCache.get(context, key)?.let {
                IconCache.put(key, it)
                return@withPermit it
            }
            runCatching {
                val drawable = resolveDrawable(context, delugeApp, app, iconPack, override)
                val bitmap = renderIcon(drawable, px, style.shape, style.themed, style.background, style.foreground)
                IconDiskCache.put(context, key, bitmap)
                bitmap.asImageBitmap().also { IconCache.put(key, it) }
            }.getOrNull()
        }
    }
}

/**
 * How many icons rasterise at once, system-wide. Enough that a cold list fills in visibly;
 * few enough that a cold scrub still has cores left for the finger. The warm pass and the
 * rows' own loads share it, so their totals never stack.
 */
private val iconDecodeGate = Semaphore(3)
/**
 * Decode every app's icon ahead of time, off the main thread. Without this the first open of
 * the A-Z list rasterises a screenful of icons synchronously during composition, which is
 * what made it take a beat to appear.
 *
 * Work fans out across the dispatcher but rasterises behind a shared three-permit gate
 * (see [loadIconBitmap]): rasterising is CPU-bound, and one job per app used to saturate
 * every core while the user was already scrubbing — the first gesture after install
 * stuttered against the warm pass itself, then went smooth once the pass was done.
 * Favorites and folder members go first
 * so the home screen is covered before the long tail of everything else installed, and are
 * touched again at the end so that tail does not evict them from the LRU on a small heap.
 * The disk cache is swept at the end, once per enumeration rather than once per icon.
 */
suspend fun warmIconCache(
    context: Context,
    apps: List<AppInfo>,
    iconPack: String?,
    overrides: Map<String, String>,
    px: Int,
    style: IconStyle,
    priorityKeys: Set<String> = emptySet(),
) {
    if (px <= 0 || apps.isEmpty()) return
    val delugeApp = context.applicationContext as DelugeApp

    fun styledFor(app: AppInfo): Pair<String?, IconStyle> {
        val override = overrides[app.key]
        val styled = override == null && iconPack == null
        return override to if (styled) style else style.copy(shape = IconShape.SYSTEM, themed = false)
    }

    suspend fun warm(app: AppInfo) {
        val (override, own) = styledFor(app)
        val key = iconCacheKey(app, iconPack, override, px, own)
        loadIconBitmap(context, delugeApp, app, iconPack, override, px, own, key)
    }

    val (first, rest) = apps.partition { it.key in priorityKeys }
    // Explicitly off the caller's dispatcher: this is invoked from a LaunchedEffect, which
    // runs on Main, and coroutineScope/async would inherit it.
    withContext(Dispatchers.Default) {
        // The loads themselves take the shared gate inside loadIconBitmap; fanning out here
        // only queues them. Lets a frame's own work cut in between chunks rather than
        // after the whole pass.
        for (group in listOf(first, rest)) {
            if (group.isEmpty()) continue
            group.chunked(CHUNK_SIZE)
                .map { chunk -> async { chunk.forEach { warm(it) } } }
                .awaitAll()
            yield()
        }
        for (app in first) {
            val (override, own) = styledFor(app)
            IconCache.touch(iconCacheKey(app, iconPack, override, px, own))
        }
        IconDiskCache.sweep(context)
    }
}

/** Big enough that per-task overhead stays negligible against a drawable decode. */
private const val CHUNK_SIZE = 16

/**
 * Icon pack and per-app overrides, provided once for the whole tree. Collecting these flows
 * inside AppIcon meant every visible row started two DataStore collections of its own — with
 * a screenful of rows that is dozens of disk-backed collectors spun up the moment the list
 * appears, each resolving its icon twice (once for the initial value, once for the real one).
 */
@Immutable
data class IconConfig(
    val pack: String?,
    val overrides: Map<String, String>,
    /** False draws no icons at all, for people who want the list to be nothing but names. */
    val showIcons: Boolean,
    /** Draw the monochrome layer, tinted, instead of the app's own colors. */
    val themed: Boolean,
    /** Whether an app showing notifications is badged with how many. */
    val notificationBadges: Boolean,
    val shape: IconShape,
)

val LocalIconConfig = staticCompositionLocalOf {
    IconConfig(
        pack = null,
        overrides = emptyMap(),
        showIcons = true,
        themed = false,
        notificationBadges = false,
        shape = IconShape.SYSTEM,
    )
}

@Composable
fun AppIcon(app: AppInfo, sizeDp: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val delugeApp = context.applicationContext as DelugeApp
    val config = LocalIconConfig.current
    // Nothing drawn and no space taken, so rows close up rather than leaving a hole. Except
    // for a dot beside the name while the app has notifications, or a list with no icons
    // could never show the count at all.
    if (!config.showIcons) {
        if (notificationBadgeCount(app) > 0) {
            Box(
                modifier
                    .padding(horizontal = 6.dp)
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
        return
    }
    // Screenshot disguise: a plain initial tile in the fake name's color, and no badge — a
    // count would say which app this really is. Nothing is loaded, so there is nothing of
    // the real icon left to recognise.
    LocalScreenshotNames.current?.get(app.key)?.let { fake ->
        ScreenshotTile(name = fake, sizeDp = sizeDp, modifier = modifier)
        return
    }
    val iconPackPackage = config.pack
    val overrideValue = config.overrides[app.key]
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }.coerceAtLeast(1)

    // An override or an icon pack is a picture the user chose; neither is an adaptive icon
    // with layers to tint or a safe zone to cut into, so styling only applies to what the app
    // itself supplies.
    val styled = overrideValue == null && iconPackPackage == null
    val scheme = MaterialTheme.colorScheme
    val style = IconStyle(
        shape = if (styled) config.shape else IconShape.SYSTEM,
        themed = styled && config.themed,
        background = scheme.primaryContainer.toArgb(),
        foreground = scheme.onPrimaryContainer.toArgb(),
    )

    val cacheKey = iconCacheKey(app, iconPackPackage, overrideValue, px, style)
    // Memory first, synchronously: a hit draws this frame with no work at all.
    var bitmap by remember(cacheKey) { mutableStateOf(IconCache.get(cacheKey)) }
    // Anything else loads beside composition, never on it. Disk decodes and first-time
    // rasterises used to run here on the main thread, which is what made long lists
    // stutter into view while every visible icon drew itself at once.
    if (bitmap == null) {
        LaunchedEffect(cacheKey) {
            bitmap = loadIconBitmap(
                context, delugeApp, app, iconPackPackage, overrideValue, px, style, cacheKey,
            )
        }
    }

    // Badged rather than drawn into the bitmap: the count changes while the icon does not, and
    // baking it in would throw away the cached icon every time a notification arrived.
    val badge = notificationBadgeCount(app)
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            // Where this icon is, for the window animation the system draws from it. The
            // bounds are in window coordinates, which is what the launch APIs expect.
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInWindow()
                LaunchOrigin.note(
                    app,
                    Rect(
                        bounds.left.roundToInt(),
                        bounds.top.roundToInt(),
                        bounds.right.roundToInt(),
                        bounds.bottom.roundToInt(),
                    ),
                )
            },
    ) {
        val drawn = bitmap
        if (drawn != null) {
            Image(
                bitmap = drawn,
                contentDescription = app.label,
                modifier = Modifier.size(sizeDp.dp),
            )
        }
        if (badge > 0) NotificationBadge(badge, sizeDp, Modifier.align(Alignment.TopEnd))
    }
}

/**
 * The disguise tile: the fake name's initial on its color. Deliberately plain — anything
 * drawn from the real icon would still be recognisable, and anything borrowed from another
 * installed app would never match the name beside it.
 */
@Composable
private fun ScreenshotTile(name: String, sizeDp: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .background(
                color = Color(ScreenshotDisguise.tileColor(name)),
                shape = RoundedCornerShape(28),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "",
            color = Color.White.copy(alpha = 0.95f),
            fontSize = (sizeDp * 0.42f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * How many notifications to show on this app, or zero for none.
 *
 * Zero whenever the setting is off or the listener is not connected, so nothing is drawn from
 * a count that is only as current as the last time the service was alive.
 */
@Composable
private fun notificationBadgeCount(app: AppInfo): Int {
    if (!LocalIconConfig.current.notificationBadges) return 0
    // Per package, not the whole map: any app's count changing used to recompose every
    // visible row; this only emits when this package's own number moves.
    val count by NotificationCountBus.countFor(app.componentName.packageName)
        .collectAsState(initial = 0)
    return count
}

/**
 * The count, in a filled circle at the icon's top corner.
 *
 * Sized from the icon rather than fixed, so it stays in proportion at every icon size the
 * launcher offers. Anything past nine is drawn as 9+: the circle is the signal, and a
 * three-digit number in it is unreadable at any size an icon is drawn at.
 */
@Composable
private fun NotificationBadge(count: Int, iconSizeDp: Int, modifier: Modifier = Modifier) {
    val diameter = (iconSizeDp * 0.42f).dp.coerceIn(14.dp, 22.dp)
    Box(
        modifier = modifier
            .size(diameter)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 9) "9+" else count.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = (diameter.value * 0.55f).sp,
            lineHeight = (diameter.value * 0.55f).sp,
            maxLines = 1,
        )
    }
}

/**
 * Resolves an icon override — an icon-pack drawable or a picture from the gallery — for
 * either an app or a folder. Both use the same encoding on purpose, so a folder can take any
 * icon an app can.
 */
internal fun decodeIconOverride(
    context: Context,
    delugeApp: DelugeApp,
    override: String?,
): Drawable? = when {
    override == null -> null

    override.startsWith(ICON_PACK_OVERRIDE_PREFIX) -> {
        val body = override.removePrefix(ICON_PACK_OVERRIDE_PREFIX)
        val split = body.lastIndexOf(':')
        if (split <= 0) {
            null
        } else {
            delugeApp.iconPackRepository.loadPackDrawable(
                body.substring(0, split),
                body.substring(split + 1),
            )
        }
    }

    else -> runCatching {
        val uri = Uri.parse(override)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            Drawable.createFromStream(stream, uri.toString())
        }
    }.getOrNull()
}

private fun resolveDrawable(
    context: Context,
    delugeApp: DelugeApp,
    app: AppInfo,
    iconPackPackage: String?,
    overrideValue: String?,
): Drawable =
    decodeIconOverride(context, delugeApp, overrideValue)
        ?: if (app.kind != EntryKind.APP) {
            // An icon pack is matched by component, and a shortcut carries its publisher's:
            // every bookmark would come back wearing the browser's icon instead of its own,
            // which is the one thing that tells two of them apart.
            delugeApp.appRepository.loadIcon(app)
        } else {
            delugeApp.iconPackRepository.getIcon(iconPackPackage, app.componentName) {
                delugeApp.appRepository.loadIcon(app)
            }
        }