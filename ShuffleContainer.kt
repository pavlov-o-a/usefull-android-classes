import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

internal class ShuffleContainer(context: Context, attributeSet: AttributeSet) : FrameLayout(context, attributeSet) {
    private var isHorizontal = true
    //this view hosts 3 childs and shuffle them for simulation of scrolling through list of views(works with PagingDragView).
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (isHorizontal) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(width * 3, heightMeasureSpec)
            for (i in 0..2)
                measureChild(getChildAt(i), MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec)
        } else {
            val height = MeasureSpec.getSize(heightMeasureSpec)
            setMeasuredDimension(widthMeasureSpec, height * 3)
            for (i in 0..2)
                measureChild(getChildAt(i), widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        setTranslations(TRANSLATION.STAY)
    }

    fun pagedNext() {
        setTranslations(TRANSLATION.FORWARD)
    }

    fun pagedPrev() {
        setTranslations(TRANSLATION.BACKWARD)
    }

    private fun setTranslations(translation: TRANSLATION) {
        val views = getViews()
        val offsetSize = if (isHorizontal) views[0].width else views[0].height
        views.forEach { it.x = 0f; it.y = 0f }
        when (translation) {
            TRANSLATION.FORWARD -> {
                views[0].setOffset(offsetSize * 2)
                views[1].setOffset(offsetSize * 0)
                views[2].setOffset(offsetSize * 1)
            }
            TRANSLATION.BACKWARD -> {
                views[0].setOffset(offsetSize * 1)
                views[1].setOffset(offsetSize * 2)
                views[2].setOffset(offsetSize * 0)
            }
            TRANSLATION.STAY -> {
                views[0].setOffset(offsetSize * 0)
                views[1].setOffset(offsetSize * 1)
                views[2].setOffset(offsetSize * 2)
            }
        }
    }

    fun getCenterView(): View {
        return getViews()[1]
    }

    fun getLeftView(): View {
        return getViews()[0]
    }

    fun getRightView(): View {
        return getViews()[2]
    }

    fun getViews(): List<View> {
        var list = arrayListOf<View>()
        list.add(getChildAt(0))
        list.add(getChildAt(1))
        list.add(getChildAt(2))
        list = ArrayList(list.sortedWith(Comparator { l, r -> l.getOffset() - r.getOffset() }))
        return list
    }

    private fun View.getOffset(): Int {
        return if (isHorizontal) this.x.toInt() else this.y.toInt()
    }

    private fun View.setOffset(offset: Int) {
        if (isHorizontal)
            this.x = offset.toFloat()
        else this.y = offset.toFloat()
    }

    fun setIsHorizontal(isHorizontal: Boolean) {
        this.isHorizontal = isHorizontal
    }

    private enum class TRANSLATION {
        FORWARD,
        BACKWARD,
        STAY
    }
}
