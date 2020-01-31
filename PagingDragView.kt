import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

private const val CLICK_THRESHOLD = 30

internal class PagingDragView constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private lateinit var pagingListeners: PagingListener
    private lateinit var centerClickListener: CenterClickListener
    private lateinit var brightnessChangeListener: BrightnessChangeListener
    private var currentClickAction: Runnable = Runnable {  }
    private var viewDragHelper: ViewDragHelper
    private var downXEvent = Pair(0f,0f)
    private var isScrollLocked = false
    private var currentLeft = 0
    private var currentTop = 0
    private var isHorizontal = true
    //values
    private var pagesCount = 10
    private var currentPageNumber = 0
    private var scrollStep = 0
    private var centerPos = 0
    private var leftFarPos = 0
    private var rightFarPos = 0
    private var leftScrollPos = 0
    private var rightScrollPos = 0

    init {
        viewDragHelper = ViewDragHelper.create(this, 1.0f, DragHelper())
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        initConstants()
        if (isHorizontal)
            layoutTo(centerPos, 0)
        else
            layoutTo(0, centerPos)
    }

    private fun initConstants() {
        scrollStep = if (isHorizontal)
            measuredWidth
            else
                measuredHeight
        centerPos = -scrollStep
        leftFarPos = -scrollStep*2
        rightFarPos = 0
        leftScrollPos = -scrollStep - dpToPx(context, 40)
        rightScrollPos = -scrollStep + dpToPx(context, 40)
    }

    fun setPagesCount(size: Int) {
        this.pagesCount = size
    }

    fun setCurrentPageNumber(position: Int) {
        this.currentPageNumber = position
    }

    fun layoutTo(x: Int, y: Int) {
        currentLeft = x
        currentTop = y
        val c = getChildAt(0)
        c.layout(x, y, x + c.width, y + c.height)
    }

    fun smoothNext(){
        if (currentPageNumber < pagesCount - 1) {
            currentPageNumber++
            pagingListeners.pagedNext()
            if (isHorizontal) {
                layoutTo(getChildAt(0).left + scrollStep, 0)
                smoothScrollTo(centerPos, 0)
            } else {
                layoutTo(0, getChildAt(0).top + scrollStep)
                smoothScrollTo(0, centerPos)
            }
        }
    }

    fun smoothPrev(){
        if (currentPageNumber > 0) {
            currentPageNumber--
            pagingListeners.pagedPrev()
            if (isHorizontal) {
                layoutTo(getChildAt(0).left - scrollStep, 0)
                smoothScrollTo(centerPos, 0)
            } else {
                layoutTo(0, getChildAt(0).top - scrollStep)
                smoothScrollTo(0, centerPos)
            }
        }
    }

    private inner class DragHelper : ViewDragHelper.Callback() {

        override fun tryCaptureView(child: View, pointerId: Int): Boolean = true

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            if (!isHorizontal)
                return 0
            pagingListeners.pagingInProcess()
            currentLeft = when {
                left > rightFarPos -> rightFarPos
                left > centerPos && currentPageNumber == 0 -> centerPos
                left < centerPos && currentPageNumber == pagesCount - 1 -> centerPos
                left < leftFarPos -> leftFarPos
                else -> left
            }
            return currentLeft
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            if (isHorizontal)
                return 0
            pagingListeners.pagingInProcess()
            currentTop = when {
                top > rightFarPos -> rightFarPos
                top > centerPos && currentPageNumber == 0 -> centerPos
                top < centerPos && currentPageNumber == pagesCount - 1 -> centerPos
                top < leftFarPos -> leftFarPos
                else -> top
            }
            return currentTop
        }

        override fun getViewHorizontalDragRange(child: View): Int = measuredWidth

        override fun getViewVerticalDragRange(child: View): Int = measuredHeight
    }

    private fun smoothScrollTo(x: Int, y: Int){
        if (viewDragHelper.smoothSlideViewTo(getChildAt(0), x, y))
            ViewCompat.postInvalidateOnAnimation(this)
    }

    override fun computeScroll() {
        if (viewDragHelper.continueSettling(true)) {
            currentLeft = getChildAt(0).left
            currentTop = getChildAt(0).top
            ViewCompat.postInvalidateOnAnimation(this)
            pagingListeners.pagingInProcess()
        } else {
            if (viewDragHelper.viewDragState == ViewDragHelper.STATE_IDLE) {
                pagingListeners.pagingSettled()
            }
        }
    }

    fun getCurrentPageNumber(): Int {
        return currentPageNumber
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN)
            downXEvent = Pair(ev.x, ev.y)
        checkClick(ev)
        checkBrightnessSwipe(ev)
        if (isScrollLocked)
            return false
        return if (super.onInterceptTouchEvent(ev))
            true
        else
            viewDragHelper.shouldInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        checkBrightnessSwipe(ev)
        if (isScrollLocked)
            return false
        if (ev.action == MotionEvent.ACTION_UP)
            settle()
        viewDragHelper.processTouchEvent(ev)
        return true
    }

    private fun checkClick(ev: MotionEvent){
        if (ev.action == MotionEvent.ACTION_DOWN)
            return
        val mod = sqrt((ev.x - downXEvent.first).toDouble().pow(2.0)
                + (ev.y - downXEvent.second).toDouble().pow(2.0))
        if (ev.action == MotionEvent.ACTION_UP && mod < CLICK_THRESHOLD) {
            if (ev.x  < width/4) {
                currentClickAction = Runnable { smoothPrev() }
                postDelayed(currentClickAction, 200)
            } else if (ev.x  > width *3/4) {
                currentClickAction = Runnable { smoothNext() }
                postDelayed(currentClickAction, 200)
            } else if (ev.x  > width*1/3 && x < width*2/3) {
                currentClickAction = Runnable { centerClickListener.centerClicked() }
                postDelayed(currentClickAction, 200)
            }
        }
    }

    fun removeClickAction(){
        removeCallbacks(currentClickAction)
    }

    private var brightnessThresholdPassed = false
    private var lastMoveY = -1
    private fun checkBrightnessSwipe(ev: MotionEvent) {
        if (!isHorizontal)
            return
        if (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_UP) {
            brightnessThresholdPassed = false
            lastMoveY = -1
        } else if (ev.action == MotionEvent.ACTION_MOVE){
            val divX = abs(downXEvent.first - ev.x)
            val divY = abs(downXEvent.second - ev.y)
            if (divY > dpToPx(context, 40))
                brightnessThresholdPassed = true
            if (divX > dpToPx(context, 40))
                brightnessThresholdPassed = false
            if (brightnessThresholdPassed){
                if (lastMoveY != -1)
                    brightnessChangeListener.brightnessChanged((lastMoveY - ev.y).toInt() / 10)
                lastMoveY = ev.y.toInt()
            }
        }
    }
    
    fun dpToPx(context: Context, dp: Int): Int{
        return dp // transform dp to px
    }

    private fun settle() {
        if (isHorizontal) {
            when {
                currentLeft < leftScrollPos -> smoothNext()
                currentLeft > rightScrollPos -> smoothPrev()
                else -> smoothScrollTo(centerPos, 0)
            }
        } else {
            when {
                currentTop < leftScrollPos -> smoothNext()
                currentTop > rightScrollPos -> smoothPrev()
                else -> smoothScrollTo(0, centerPos)
            }
        }
    }

    fun lockScroll(){
        isScrollLocked = true
    }

    fun freeScroll(){
        isScrollLocked = false
    }

    fun getPagesCount(): Int {
        return pagesCount
    }

    fun setPagingListener(pagingListener: PagingListener) {
        pagingListeners = pagingListener
    }

    fun setCenterClickListener(centerClickListener: CenterClickListener){
        this.centerClickListener = centerClickListener
    }

    internal interface CenterClickListener {
        fun centerClicked()
    }

    fun setBrightnessChangeListener(brightnessChangeListener: BrightnessChangeListener) {
        this.brightnessChangeListener = brightnessChangeListener
    }

    fun setIsHorizontal(isHorizontal: Boolean) {
        this.isHorizontal = isHorizontal
    }

    internal interface PagingListener {
        fun pagedPrev()
        fun pagedNext()
        fun pagingSettled()
        fun pagingInProcess()
    }

    internal interface BrightnessChangeListener {
        fun brightnessChanged(dy: Int)
    }
}
