import android.content.Context;
import android.graphics.Color;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.*;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * drag layout which can be used in music app for example. has behaviour like player in google play music.
 */
 
public class DragLayout extends ViewGroup {
    ViewDragHelper viewDragHelper;
    private int currentPosition;
    private int bottomPosition;
    private int topPosition;
    private int sizeOfMiniPlayer;
    private boolean isMiniPlayer = true;
    private View miniPlayer;
    private View playerFront;
    private View playerChapters;
    private View parentFrameLayout;
    private float wrapRange;

    private PlayerScrollView scrollView;

    private int layoutHeight;

    public DragLayout(Context context) {
        this(context, null);
    }

    public DragLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DragLayout(Context context, AttributeSet attrs, int i) {
        super(context, attrs, i);
        viewDragHelper = ViewDragHelper.create(this, 1.0f, new ViewDragVerticalHelper());
        wrapRange = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 13, getContext().getResources().getDisplayMetrics());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        measureChildren(widthSpec, heightSpec);
        setMeasuredDimension(widthSpec, heightSpec);
        sizeOfMiniPlayer = getResources().getDimensionPixelSize(R.dimen.small_player_height);
        int navigationHeight = getResources().getDimensionPixelSize(R.dimen.navigation_height);
        if (layoutHeight != height){
            layoutHeight = height;
            currentPosition = bottomPosition = layoutHeight - sizeOfMiniPlayer - navigationHeight;
            topPosition = 0;
        }
        if (Utils.isIDE())//for convenient way to design layout in ide
            currentPosition = 0;
    }

    public int getRealHeight() {
        return layoutHeight;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        getChildAt(0).layout(0, currentPosition, r, currentPosition + getChildAt(0).getMeasuredHeight());
        updateVisibilityOfViews(currentPosition);
        if (getChildCount() > 1)
            throw new RuntimeException("playerDragLayout can host only 1 child - scrollView");
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        scrollView = findViewById(R.id.inner_scroll_view);
        miniPlayer = findViewById(R.id.activity_player_mini);
        playerFront = findViewById(R.id.player_front_container);
        playerChapters = findViewById(R.id.player_chapters_container);
    }

    public void setParentFrame(View parent) {
        parentFrameLayout = parent;
    }

    @Override
    public void computeScroll() {
        if (viewDragHelper.continueSettling(true)) {
            currentPosition = getChildAt(0).getTop();
            updateVisibilityOfViews(currentPosition);
            ViewCompat.postInvalidateOnAnimation(this);
        } else {
            if (viewDragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE) {
                isMiniPlayer = currentPosition != topPosition;
                scrollView.setScrollingEnabled(currentPosition == topPosition);
            }
        }
    }

    public void wrap() {
        scrollView.scrollTo(0, topPosition);
        if (viewDragHelper.smoothSlideViewTo(getChildAt(0), 0, bottomPosition))
            ViewCompat.postInvalidateOnAnimation(this);
    }

    public void unwrap() {
        if (viewDragHelper.smoothSlideViewTo(getChildAt(0), 0, topPosition))
            ViewCompat.postInvalidateOnAnimation(this);
    }

    public boolean isWraped() {
        return isMiniPlayer || getVisibility() == GONE;
    }

    public void close() {
        scrollView.scrollTo(0, topPosition);
        getChildAt(0).setTop(bottomPosition);
        isMiniPlayer = true;
        parentFrameLayout.setBackgroundColor(Color.argb(0, 0, 0, 0));
    }

    private class ViewDragVerticalHelper extends ViewDragHelper.Callback {

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            return true;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            if (!isMiniPlayer) {
                if (!canScrollViewScrollUp() && !(dy < 0 && top <= topPosition)) {
                    //this is not a top-end of scrolling of this PlayerDragLayout and scrollview cant scroll up, so update position
                    currentPosition = top;
                } else
                    scrollView.setScrollingEnabled(true);           //-- free scrollview
            } else {
                if (top >= (bottomPosition) || top <= topPosition)
                    return currentPosition;
                currentPosition = top;
            }
            updateVisibilityOfViews(currentPosition);
            return currentPosition;
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return getHeight();
        }
    }

    private void scrollToNeededDirection() {
        if ((isMiniPlayer && currentPosition < getHeight() - wrapRange)
                || (!isMiniPlayer && currentPosition < topPosition + wrapRange)) {
            if (viewDragHelper.smoothSlideViewTo(getChildAt(0), 0, topPosition))
                ViewCompat.postInvalidateOnAnimation(this);
        } else {
            if (viewDragHelper.smoothSlideViewTo(getChildAt(0), 0, bottomPosition))
                ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    private void updateVisibilityOfViews(int top) {
        if (bottomPosition > 0) {
            miniPlayer.setAlpha((float) top / bottomPosition);
            playerFront.setAlpha(1f - (float) top / bottomPosition);
            playerChapters.setAlpha(1f - (float) top / bottomPosition);
            if (parentFrameLayout != null)
                parentFrameLayout.setBackgroundColor(Color.argb(102 - 102 * top / bottomPosition, 0, 0, 0));
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isMiniPlayer)
            scrollView.setScrollingEnabled(false);
        else {
            if (canScrollViewScrollUp())
                return false;
        }
        if (super.onInterceptTouchEvent(ev))
            return true;
        return viewDragHelper.shouldInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_UP)
            scrollToNeededDirection();
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (isTouchAboveMiniPlayer(ev))
                return false;
        }
        viewDragHelper.processTouchEvent(ev);
        return true;
    }

    private boolean isTouchAboveMiniPlayer(MotionEvent ev){
        return isMiniPlayer && (ev.getY() < currentPosition || ev.getY() > bottomPosition + sizeOfMiniPlayer);
    }

    public boolean canScrollViewScrollUp() {
        return scrollView.canScrollVertically(-1);
    }
}
