package io.legado.app.ui.book.read.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTurnGestureTest {

    @Test
    fun releaseRequiresFifteenPercentNetDisplacement() {
        val gesture = PageTurnGesture()
        gesture.start(500f, 100f, 1000)
        assertEquals(
            PageTurnGesture.Direction.NEXT,
            gesture.tryStart(400f, 100f, 20f)
        )

        for (x in listOf(499f, 450f, 400f, 351f, 350.01f)) {
            assertFalse("x=$x", gesture.shouldCommit(x))
        }
        assertTrue(gesture.shouldCommit(350f))
        assertTrue(gesture.shouldCommit(349.99f))
    }

    @Test
    fun thresholdWorksInBothDirectionsAndAtDifferentWidths() {
        val previous = PageTurnGesture()
        previous.start(100f, 200f, 800)
        assertEquals(
            PageTurnGesture.Direction.PREV,
            previous.tryStart(140f, 200f, 20f)
        )
        assertFalse(previous.shouldCommit(219.99f))
        assertTrue(previous.shouldCommit(220f))

        val next = PageTurnGesture()
        next.start(900f, 200f, 400)
        assertEquals(
            PageTurnGesture.Direction.NEXT,
            next.tryStart(850f, 200f, 20f)
        )
        assertFalse(next.shouldCommit(840.01f))
        assertTrue(next.shouldCommit(840f))
    }

    @Test
    fun releaseUsesNetDistanceRatherThanMaximumExcursionOrVelocity() {
        val gesture = PageTurnGesture()
        gesture.start(500f, 100f, 1000)
        gesture.tryStart(300f, 100f, 20f)
        assertTrue(gesture.shouldCommit(300f))
        assertFalse(gesture.shouldCommit(400f))
        assertFalse(gesture.shouldCommit(510f))
    }

    @Test
    fun horizontalIntentRequiresDistanceAndDirectionRatio() {
        val gesture = PageTurnGesture()
        gesture.start(100f, 100f, 1000)

        assertEquals(
            PageTurnGesture.Direction.NONE,
            gesture.tryStart(119.99f, 100f, 20f)
        )
        assertEquals(
            PageTurnGesture.Direction.NONE,
            gesture.tryStart(130f, 121f, 20f)
        )
        assertEquals(
            PageTurnGesture.Direction.PREV,
            gesture.tryStart(131.5f, 121f, 20f)
        )
        assertEquals(
            PageTurnGesture.Direction.PREV,
            gesture.tryStart(20f, 100f, 20f)
        )
    }

    @Test
    fun holdToleranceUsesImmutableDownCoordinates() {
        val gesture = PageTurnGesture()
        gesture.start(200f, 300f, 1000)

        assertTrue(gesture.isWithinHoldTolerance(220f, 280f, 20f))
        assertFalse(gesture.isWithinHoldTolerance(220.01f, 300f, 20f))
        gesture.tryStart(250f, 300f, 20f)
        assertTrue(gesture.isWithinHoldTolerance(220f, 300f, 20f))
    }

    @Test
    fun cancellationAndInvalidGeometryNeverCommit() {
        val cancelled = PageTurnGesture()
        cancelled.start(500f, 100f, 1000)
        cancelled.tryStart(300f, 100f, 20f)
        cancelled.cancel()
        assertFalse(cancelled.shouldCommit(0f))
        assertEquals(
            PageTurnGesture.Direction.NONE,
            cancelled.tryStart(0f, 100f, 20f)
        )

        val invalid = PageTurnGesture()
        invalid.start(500f, 100f, 0)
        assertEquals(
            PageTurnGesture.Direction.NONE,
            invalid.tryStart(0f, 100f, 20f)
        )
        assertFalse(invalid.shouldCommit(0f))
    }

    @Test
    fun newTouchResetsCancelledGesture() {
        val gesture = PageTurnGesture()
        gesture.start(500f, 100f, 1000)
        gesture.cancel()
        gesture.start(500f, 100f, 1000)

        assertEquals(
            PageTurnGesture.Direction.NEXT,
            gesture.tryStart(450f, 100f, 20f)
        )
        assertTrue(gesture.shouldCommit(350f))
    }
}
