package io.legado.app.ui.book.read.page.delegate

import android.view.MotionEvent
import io.legado.app.ui.book.read.page.PageTurnGesture
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import io.legado.app.utils.screenshot

abstract class HorizontalPageDelegate(readView: ReadView) : PageDelegate(readView) {

    protected var curRecorder = CanvasRecorderFactory.create()
    protected var prevRecorder = CanvasRecorderFactory.create()
    protected var nextRecorder = CanvasRecorderFactory.create()
    private val gesture = PageTurnGesture()
    private var acceptsTouch = false

    override fun setDirection(direction: PageDirection) {
        super.setDirection(direction)
        setBitmap()
    }

    open fun setBitmap() {
        when (mDirection) {
            PageDirection.PREV -> {
                prevPage.screenshot(prevRecorder)
                curPage.screenshot(curRecorder)
            }

            PageDirection.NEXT -> {
                nextPage.screenshot(nextRecorder)
                curPage.screenshot(curRecorder)
            }

            else -> Unit
        }
    }

    fun upRecorder() {
        curRecorder.recycle()
        prevRecorder.recycle()
        nextRecorder.recycle()
        curRecorder = CanvasRecorderFactory.create()
        prevRecorder = CanvasRecorderFactory.create()
        nextRecorder = CanvasRecorderFactory.create()
    }

    override fun onTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                abortAnim()
                gesture.start(event.x, event.y, viewWidth)
                acceptsTouch = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (acceptsTouch) {
                    onScroll(event)
                }
            }

            MotionEvent.ACTION_UP -> {
                finishTouch(event, cancelled = false)
            }

            MotionEvent.ACTION_CANCEL -> {
                finishTouch(event, cancelled = true)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                finishTouch(event, cancelled = true)
            }
        }
    }

    private fun onScroll(event: MotionEvent) {
        if (!isMoved) {
            when (gesture.tryStart(
                event.x,
                event.y,
                readView.horizontalPageTouchSlop.toFloat()
            )) {
                PageTurnGesture.Direction.PREV -> {
                    //如果上一页不存在
                    if (!hasPrev()) {
                        noNext = true
                        gesture.cancel()
                        return
                    }
                    setDirection(PageDirection.PREV)
                }

                PageTurnGesture.Direction.NEXT -> {
                    //如果不存在表示没有下一页了
                    if (!hasNext()) {
                        noNext = true
                        gesture.cancel()
                        return
                    }
                    setDirection(PageDirection.NEXT)
                }

                PageTurnGesture.Direction.NONE -> return
            }
            isMoved = true
            readView.setStartPoint(event.x, event.y, false)
        }
        if (isMoved) {
            isRunning = true
            //设置触摸点
            readView.setTouchPoint(event.x, event.y)
        }
    }

    private fun finishTouch(event: MotionEvent, cancelled: Boolean) {
        if (!acceptsTouch) return
        acceptsTouch = false
        if (!isMoved) {
            gesture.cancel()
            return
        }
        if (!cancelled) {
            readView.setTouchPoint(event.x, event.y)
        }
        isCancel = cancelled || !gesture.shouldCommit(event.x)
        gesture.cancel()
        onAnimStart(readView.defaultAnimationSpeed)
    }

    override fun abortAnim() {
        isStarted = false
        isMoved = false
        isRunning = false
        if (!scroller.isFinished) {
            readView.isAbortAnim = true
            scroller.abortAnimation()
            if (!isCancel) {
                readView.fillPage(mDirection)
                readView.invalidate()
            }
        } else {
            readView.isAbortAnim = false
        }
    }

    override fun nextPageByAnim(animationSpeed: Int) {
        abortAnim()
        if (!hasNext()) return
        isCancel = false
        setDirection(PageDirection.NEXT)
        val y = when {
            startY > viewHeight / 2 -> viewHeight.toFloat() * 0.9f
            else -> 1f
        }
        readView.setStartPoint(viewWidth.toFloat() * 0.9f, y, false)
        onAnimStart(animationSpeed)
    }

    override fun prevPageByAnim(animationSpeed: Int) {
        abortAnim()
        if (!hasPrev()) return
        isCancel = false
        setDirection(PageDirection.PREV)
        readView.setStartPoint(0f, viewHeight.toFloat(), false)
        onAnimStart(animationSpeed)
    }

    override fun onDestroy() {
        super.onDestroy()
        prevRecorder.recycle()
        curRecorder.recycle()
        nextRecorder.recycle()
    }

}