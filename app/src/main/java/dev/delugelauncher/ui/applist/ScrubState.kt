// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.applist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.delugelauncher.data.AzStripSide
import dev.delugelauncher.data.EdgeSide
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Everything that changes while a finger is on the A-Z strip, in one stable holder.
 *
 * This exists for recomposition scope, not tidiness. The letter under the fingertip changes
 * up to 26 times per gesture; when the home destination read it directly, each change
 * invalidated that whole scope — and the home screen composable hangs off it. Passing this
 * holder down instead means only the composable that actually reads a field is invalidated,
 * so scrubbing no longer recomposes the home screen behind the overlay.
 *
 * [currentY] and [currentPull] are deliberately functions rather than properties: their
 * callers read them inside `graphicsLayer`, which puts the read in the draw phase and skips
 * recomposition altogether.
 */
@Stable
class ScrubState {
    /** The label under the fingertip, or null when no scrub is in progress. */
    var letter by mutableStateOf<String?>(null)
        private set

    /**
     * The letter a gesture asked the list to be parked at, with a count of the asking.
     *
     * Separate from [letter], which is what the fingertip is on right now and is dropped the
     * instant it lifts. A tap quick enough to land and lift inside one frame set a letter and
     * cleared it again before anything had composed, so the list opened at A having been told
     * about M and back. The request outlives the touch; the count is what tells a second ask
     * for the same letter from the first.
     */
    var placement by mutableStateOf<Placement?>(null)
        private set

    private var placementCount = 0L

    /**
     * [atEnd] asks for the very end of the list, past the last letter's apps: the finger was
     * pushed past Z, which is a place the alphabet itself has no row for.
     */
    data class Placement(val letter: String, val count: Long, val atEnd: Boolean = false)

    /** True while a finger is down on an edge zone. */
    var active by mutableStateOf(false)
        private set

    /**
     * True once the finger has actually traveled, as opposed to merely landing. The letter
     * under a fingertip is reported from the down event so that a tap still places the list,
     * but the drawn-out affordances — fading every other row away — are worth their cost only
     * while a finger is genuinely moving through the alphabet. Gating them on this is what
     * stops a plain tap on the edge spending a quarter of a second fading the list out and
     * straight back in again.
     */
    var scrubbing by mutableStateOf(false)
        private set

    /** Which edge is in play, so the strip only appears on the side actually used. */
    var side by mutableStateOf(EdgeSide.RIGHT)
        private set

    /**
     * True once a finger dragging off the letters is over the list itself, where letting go
     * opens whatever it is on rather than placing the list at a letter.
     */
    var onApps by mutableStateOf(false)
        private set

    /**
     * The app key under that finger, so the row it would open can say so. Null when it is over
     * nothing, which is how letting go there becomes a dismissal.
     */
    var hoverKey by mutableStateOf<String?>(null)
        private set

    /**
     * Which app is at a point in root pixels, set by the list while it is open. Null while the
     * list is not, so a drag over nothing finds nothing.
     */
    var appAt: ((x: Float, y: Float) -> String?)? = null

    fun updateOnApps(value: Boolean) {
        onApps = value
        if (!value) hoverKey = null
    }

    fun setHover(key: String?) {
        if (hoverKey != key) hoverKey = key
    }

    /** Where the finger over the list last was, in root pixels. */
    private var appsX = 0f
    private var appsY = 0f

    fun noteAppsFinger(x: Float, y: Float) {
        appsX = x
        appsY = y
    }

    /**
     * Asks the list again what is under the finger.
     *
     * The list keeps settling while the finger moves inward, so a row taken at the last move
     * can have slid away by the time the finger lifts — and that row would be the one opened.
     * Asked again every frame while the finger is over the list, and once more on release.
     */
    fun refreshHover() {
        val at = appAt ?: return
        setHover(at(appsX, appsY))
    }

    private var y by mutableFloatStateOf(0f)

    // Elastic pull: follows the finger inward, then eases back to the strip on release.
    private var pull by mutableFloatStateOf(0f)
    private val releasePull = Animatable(0f)
    private var releasing by mutableStateOf(false)

    // How far the strip has been pushed by a finger run off one of its ends, with "Strip
    // follows past the ends" on. Kept for the rest of the gesture — a thing that was pushed
    // stays where it was pushed to — and sprung home on release.
    private var shift by mutableFloatStateOf(0f)
    private val releaseShift = Animatable(0f)

    /**
     * When the strip was last tapped, so a second tap can be recognized. Deliberately not
     * snapshot state: nothing draws from it, and both edge zones share one holder so a
     * double tap works even with the strip enabled on both sides.
     */
    var lastTapUptimeMs: Long = 0L

    /** What that tap did, so the next one can undo it instead of locking the phone. */
    internal var lastTapEffect: TapEffect = TapEffect.NONE

    fun currentY(): Float? = if (active) y else null

    fun currentPull(): Float = if (releasing) releasePull.value else pull

    fun currentShift(): Float = if (releasing) releaseShift.value else shift

    /**
     * Parks the strip on the edge the settings name, so it renders on the right side before
     * the first scrub of a session ever happens. Ignored mid-gesture: the side in play then
     * is whichever edge the finger is actually on.
     *
     * "Off" rests on the right like anything that is not the left edge. The strip keeps being
     * drawn with the setting off — it is the touch area that goes, not the letters — so it
     * still has to have a side to be drawn on.
     */
    fun syncRestingSide(stripSide: AzStripSide) {
        if (!active) side = if (stripSide == AzStripSide.LEFT) EdgeSide.LEFT else EdgeSide.RIGHT
    }

    fun begin(side: EdgeSide) {
        this.side = side
        active = true
        // Every gesture starts on the letters; being over the list is something a finger
        // does, not something it inherits.
        updateOnApps(false)
        scrubbing = false
        releasing = false
        shift = 0f
        // Every gesture asks afresh, so picking the same letter twice places the list twice —
        // the second time is someone who scrolled away and wants to be back there.
        placement = null
    }

    /**
     * True while a finger has been resting on a letter long enough to mean "keep going".
     *
     * A letter places the list at its first app and stops there, which is no help when a letter
     * holds a hundred of them. Holding still asks for the rest of them to come past.
     */
    var holdScrolling by mutableStateOf(false)
        private set

    fun holdScroll(value: Boolean) {
        if (holdScrolling != value) holdScrolling = value
    }

    /** Called once the gesture passes touch slop, never for a tap. */
    fun markScrubbing() {
        scrubbing = true
    }

    /**
     * [shiftPx] is how far the strip has been pushed off its ends, and [atEnd] that it is
     * being pushed past its last letter, which places the list at its very end rather than at
     * any letter.
     */
    fun update(
        y: Float,
        inwardPx: Float,
        letter: String?,
        shiftPx: Float = 0f,
        atEnd: Boolean = false,
    ) {
        this.y = y
        this.pull = inwardPx
        this.shift = shiftPx
        if (atEnd && letter != null) {
            // No letter is being pointed at, so none is shown or singled out.
            this.letter = null
            if (placement?.atEnd != true) placement = Placement(letter, ++placementCount, atEnd = true)
            return
        }
        this.letter = letter
        if (letter != null && (letter != placement?.letter || placement?.atEnd == true)) {
            placement = Placement(letter, ++placementCount)
        }
    }

    /** Forgotten when the list closes, so the next opening starts where the list does. */
    fun clearPlacement() {
        placement = null
    }

    /** Releases the elastic pull back to the strip. Suspends until the spring settles. */
    suspend fun release() {
        holdScrolling = false
        active = false
        scrubbing = false
        letter = null
        onApps = false
        hoverKey = null
        releasing = true
        releasePull.snapTo(pull)
        releaseShift.snapTo(shift)
        val home = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )
        coroutineScope {
            launch { releasePull.animateTo(0f, home) }
            launch { releaseShift.animateTo(0f, home) }
        }
        releasing = false
        pull = 0f
        shift = 0f
    }

    /** Cancels a scrub without the spring, for dismissals that snap (the HOME key). */
    fun cancel() {
        holdScrolling = false
        active = false
        scrubbing = false
        letter = null
        onApps = false
        hoverKey = null
        placement = null
        releasing = false
        pull = 0f
        shift = 0f
    }
}

/**
 * Whether a finger dragged this far in is over the list rather than still on the letters.
 *
 * Two thresholds, not one: a fingertip resting exactly on the boundary would otherwise flip
 * between scrubbing and opening on every event, and the list would jump a letter each time.
 */
internal fun overApps(
    inwardPx: Float,
    alreadyOver: Boolean,
    enterPx: Float,
    exitPx: Float,
): Boolean = if (alreadyOver) inwardPx > exitPx else inwardPx > enterPx

/** What letting go does: open the app under the finger, take the list away, or nothing. */
internal enum class ReleaseEffect { LAUNCH, DISMISS, PARK }

/**
 * Letting go over an app opens it; over the list but on nothing takes the list away; on the
 * letters themselves it only parks the list, which is what the strip has always done.
 */
internal fun releaseEffect(onApps: Boolean, hoverKey: String?): ReleaseEffect = when {
    !onApps -> ReleaseEffect.PARK
    hoverKey != null -> ReleaseEffect.LAUNCH
    else -> ReleaseEffect.DISMISS
}

/** What a tap on the strip did, so the next one in the window knows what it means. */
internal enum class TapEffect { NONE, OPENED, DISMISSED }

/** What the tap that just ended should do. */
internal enum class StripTap { RESTART, CLOSE, LOCK }

/**
 * Resolves a tap against the one before it.
 *
 * The strip opens the list on touch-down, so the first tap of a pair always leaves the list
 * open. Calling the second one "lock" — the old rule — locked the phone on the double tap
 * people make to open and close the list again. Only a tap that changed nothing (a tap on a
 * letter, with the list already up) can be the first half of a lock; a tap that opened the
 * list hands the second tap back as "close"; one that closed it starts the window over.
 */
internal fun stripTapAction(withinWindow: Boolean, previous: TapEffect): StripTap = when {
    !withinWindow -> StripTap.RESTART
    previous == TapEffect.OPENED -> StripTap.CLOSE
    previous == TapEffect.NONE -> StripTap.LOCK
    else -> StripTap.RESTART
}
