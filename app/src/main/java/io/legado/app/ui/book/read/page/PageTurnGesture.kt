package io.legado.app.ui.book.read.page

import kotlin.math.abs

internal class PageTurnGesture(
    private val commitPercent: Int = 15,
    private val horizontalRatio: Float = 1.5f,
) {

    enum class Direction {
        NONE,
        PREV,
        NEXT,
    }

    private var downX = 0f
    private var downY = 0f
    private var viewWidth = 0
    private var invalid = true

    var direction = Direction.NONE
        private set

    fun start(x: Float, y: Float, width: Int) {
        downX = x
        downY = y
        viewWidth = width
        direction = Direction.NONE
        invalid = width <= 0
    }

    fun tryStart(x: Float, y: Float, activationDistance: Float): Direction {
        if (invalid || direction != Direction.NONE) return direction
        val deltaX = x - downX
        val absX = abs(deltaX)
        val absY = abs(y - downY)
        if (absX >= activationDistance && absX >= absY * horizontalRatio) {
            direction = if (deltaX > 0f) Direction.PREV else Direction.NEXT
        }
        return direction
    }

    fun shouldCommit(x: Float): Boolean {
        if (invalid || direction == Direction.NONE) return false
        val deltaX = x - downX
        val movesInDirection = when (direction) {
            Direction.PREV -> deltaX > 0f
            Direction.NEXT -> deltaX < 0f
            Direction.NONE -> false
        }
        return movesInDirection && abs(deltaX) * 100f >= viewWidth * commitPercent
    }

    fun isWithinHoldTolerance(x: Float, y: Float, tolerance: Float): Boolean {
        return !invalid &&
            abs(x - downX) <= tolerance &&
            abs(y - downY) <= tolerance
    }

    fun cancel() {
        invalid = true
        direction = Direction.NONE
    }
}
