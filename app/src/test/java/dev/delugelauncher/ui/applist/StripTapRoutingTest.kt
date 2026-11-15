package dev.delugelauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The strip opens the list on touch-down, so the second tap of a pair must not turn into a
 * lock unless the first one changed nothing. A lock is a state the user has to undo with the
 * device credential, which is why "open, then close" cannot mean it.
 */
class StripTapRoutingTest {

    @Test
    fun aLoneTapStartsTheWindow() {
        assertEquals(StripTap.RESTART, stripTapAction(withinWindow = false, previous = TapEffect.NONE))
        assertEquals(StripTap.RESTART, stripTapAction(withinWindow = false, previous = TapEffect.OPENED))
    }

    @Test
    fun openingTapFollowedByATapClosesTheList() {
        assertEquals(StripTap.CLOSE, stripTapAction(withinWindow = true, previous = TapEffect.OPENED))
    }

    @Test
    fun aTapOnTheOpenListCanStillLock() {
        assertEquals(StripTap.LOCK, stripTapAction(withinWindow = true, previous = TapEffect.NONE))
    }

    @Test
    fun aClosingTapStartsOverInsteadOfLocking() {
        assertEquals(StripTap.RESTART, stripTapAction(withinWindow = true, previous = TapEffect.DISMISSED))
    }
}
