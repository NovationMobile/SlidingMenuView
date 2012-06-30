
package com.novationmobile.view;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Scroller;

class SlidingView extends ViewGroup {

    @SuppressWarnings("unused")
    private static final String TAG= "SlidingView";

    /*
     * How long to animate between screens when programmatically setting with
     * setCurrentScreen using the animate parameter
     */
    private static final int ANIMATION_SCREEN_SET_DURATION_MILLIS= 500;

    // What fraction (1/x) of the screen the user must swipe to indicate a page
    // change
    private static final int FRACTION_OF_SCREEN_WIDTH_FOR_SWIPE= 8;

    /*
     * Velocity of a swipe (in density-independent pixels per second) to force a
     * swipe to the next/previous screen. Adjusted into
     * mDensityAdjustedSnapVelocity on init.
     */
    private static final int SNAP_VELOCITY_DIP_PER_SECOND= 600;
    // Argument to getVelocity for units to give pixels per second (1 = pixels
    // per millisecond).
    private static final int VELOCITY_UNIT_PIXELS_PER_SECOND= 1000;

    private static final int TOUCH_STATE_REST= 0;
    private static final int TOUCH_STATE_HORIZONTAL_SCROLLING= 1;
    private static final int TOUCH_STATE_VERTICAL_SCROLLING= -1;

    private static final float DEFAULT_SLIDER_WIDTH= .85f;

    private static final long MAX_CLICK_DELAY= 700;

    private int mDensityAdjustedSnapVelocity;
    private boolean mFirstLayout= true;
    private float mLastMotionX;
    private float mLastMotionY;
    private int mMaximumVelocity;
    private Scroller mScroller;
    private int mTouchSlop;
    private int mTouchState= TOUCH_STATE_REST;
    private VelocityTracker mVelocityTracker;
    private int mLastSeenLayoutWidth= -1;
    private View mChild;
    private float mSliderWidth= DEFAULT_SLIDER_WIDTH;
    private boolean mOpen;
    private OnStateChangedListener mListener;

    public SlidingView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public SlidingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SlidingView(Context context) {
        super(context);
        init();
    }

    private void init() {
        mScroller= new Scroller(getContext());

        // Calculate the density-dependent snap velocity in pixels
        DisplayMetrics displayMetrics= new DisplayMetrics();
        ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay()
                .getMetrics(displayMetrics);
        mDensityAdjustedSnapVelocity=
                (int) (displayMetrics.density * SNAP_VELOCITY_DIP_PER_SECOND);

        final ViewConfiguration configuration= ViewConfiguration.get(getContext());
        mTouchSlop= configuration.getScaledTouchSlop();
        mMaximumVelocity= configuration.getScaledMaximumFlingVelocity();
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int width= MeasureSpec.getSize(widthMeasureSpec);
        final int widthMode= MeasureSpec.getMode(widthMeasureSpec);
        if (widthMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("ViewSwitcher can only be used in EXACTLY mode.");
        }

        final int heightMode= MeasureSpec.getMode(heightMeasureSpec);
        if (heightMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("ViewSwitcher can only be used in EXACTLY mode.");
        }

        // The children are given the same width and height as the workspace
        final int count= getChildCount();
        for (int i= 0; i < count; i++) {
            getChildAt(i).measure(widthMeasureSpec, heightMeasureSpec);
        }

        if (mFirstLayout) {
            close();
            mFirstLayout= false;
        } else if (width != mLastSeenLayoutWidth) { // Width has changed
            /*
             * Recalculate the width and scroll to the right position to be sure
             * we're in the right place in the event that we had a rotation that
             * didn't result in an activity restart (code by aveyD). Without
             * this you can end up between two pages after a rotation.
             */

            final int newX= getActualSliderWidth();
            final int delta= newX - getScrollX();

            mScroller.startScroll(getScrollX(), 0, delta, 0, 0);
        }

        mLastSeenLayoutWidth= width;
    }

    @Override
    protected void onLayout(final boolean changed, final int l, final int t, final int r,
            final int b) {
        if (mChild != null && mChild.getVisibility() != View.GONE) {
            final int width= mChild.getMeasuredWidth();
            final int left= (int) (getWidth() * getSliderWidth());
            mChild.layout(left, 0, left + width, mChild.getMeasuredHeight());
        }
    }

    @Override
    public void addView(View child) {
        if (mChild == null) {
            mChild= child;
            super.addView(child);
        } else {
            throw new IllegalStateException("Cannot add another view to this sliding view.");
        }
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent ev) {
        /*
         * By Yoni Samlan: Modified onInterceptTouchEvent based on standard
         * ScrollView's onIntercept. The logic is designed to support a nested
         * vertically scrolling view inside this one; once a scroll registers
         * for X-wise scrolling, handle it in this view and don't let the
         * children, but once a scroll registers for y-wise scrolling, let the
         * children handle it exclusively.
         */
        final int action= ev.getAction();
        boolean intercept= false;

        switch (action) {
            case MotionEvent.ACTION_MOVE:
                /*
                 * If we're in a horizontal scroll event, take it (intercept
                 * further events). But if we're mid-vertical-scroll, don't even
                 * try; let the children deal with it. If we haven't found a
                 * scroll event yet, check for one.
                 */
                if (mTouchState == TOUCH_STATE_HORIZONTAL_SCROLLING) {
                    /*
                     * We've already started a horizontal scroll; set intercept
                     * to true so we can take the remainder of all touch events
                     * in onTouchEvent.
                     */
                    intercept= true;
                } else if (mTouchState == TOUCH_STATE_VERTICAL_SCROLLING) {
                    // Let children handle the events for the duration of the
                    // scroll event.
                    intercept= false;
                } else { // We haven't picked up a scroll event yet; check for
                         // one.

                    /*
                     * If we detected a horizontal scroll event, start stealing
                     * touch events (mark as scrolling). Otherwise, see if we
                     * had a vertical scroll event -- if so, let the children
                     * handle it and don't look to intercept again until the
                     * motion is done.
                     */
                    final float x= ev.getX();
                    final int xDiff= (int) Math.abs(x - mLastMotionX);
                    boolean xMoved= xDiff > mTouchSlop;

                    if (xMoved) {
                        // Scroll if the user moved far enough along the X axis
                        mTouchState= TOUCH_STATE_HORIZONTAL_SCROLLING;
                        mLastMotionX= x;
                        intercept= true;
                    }

                    final float y= ev.getY();
                    final int yDiff= (int) Math.abs(y - mLastMotionY);
                    boolean yMoved= yDiff > mTouchSlop;

                    if (yMoved) {
                        mTouchState= TOUCH_STATE_VERTICAL_SCROLLING;
                        mLastMotionY= y;
                    }
                }

                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // Release the drag.
                mTouchState= TOUCH_STATE_REST;
                break;
            case MotionEvent.ACTION_DOWN:
                /*
                 * No motion yet, but register the coordinates so we can check
                 * for intercept at the next MOVE event.
                 */
                if (mOpen) {
                    // If it is open, force intereception of all events.
                    intercept= true;
                }

                mLastMotionY= ev.getY();
                mLastMotionX= ev.getX();

                mTouchState= TOUCH_STATE_REST;
                break;
            default:
                break;
        }

        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker= null;
        }

        return intercept;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent ev) {

        if (mVelocityTracker == null) {
            mVelocityTracker= VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        final int action= ev.getAction();
        final float x= ev.getX();

        boolean result= true;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                /*
                 * If being flinged and user touches, stop the fling. isFinished
                 * will be false if being flinged.
                 */
                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                }

                // Remember where the motion event started
                mLastMotionX= x;

                if (mScroller.isFinished()) {
                    mTouchState= TOUCH_STATE_REST;
                } else {
                    mTouchState= TOUCH_STATE_HORIZONTAL_SCROLLING;
                }

                if (shouldIgnoreEvent(ev)) {
                    if (mVelocityTracker != null) {
                        mVelocityTracker.recycle();
                        mVelocityTracker= null;
                    }

                    mTouchState= TOUCH_STATE_REST;
                    result= false;
                }

                break;
            case MotionEvent.ACTION_MOVE:
                final int xDiff= (int) Math.abs(x - mLastMotionX);
                boolean xMoved= xDiff > mTouchSlop;

                if (xMoved) {
                    // Scroll if the user moved far enough along the X axis
                    mTouchState= TOUCH_STATE_HORIZONTAL_SCROLLING;
                }

                if (mTouchState == TOUCH_STATE_HORIZONTAL_SCROLLING) {
                    // Scroll to follow the motion event
                    final int deltaX= (int) (mLastMotionX - x);
                    mLastMotionX= x;
                    final int scrollX= getScrollX();

                    if (deltaX < 0) {
                        if (scrollX > 0) {
                            scrollBy(Math.max(-scrollX, deltaX), 0);
                        }
                    } else if (deltaX > 0) {
                        final int availableToScroll=
                                getChildAt(getChildCount() - 1).getRight() - scrollX - getWidth();

                        if (availableToScroll > 0) {
                            scrollBy(Math.min(availableToScroll, deltaX), 0);
                        }
                    }
                }

                break;

            case MotionEvent.ACTION_UP:
                if (mTouchState == TOUCH_STATE_HORIZONTAL_SCROLLING) {
                    final VelocityTracker velocityTracker= mVelocityTracker;
                    velocityTracker.computeCurrentVelocity(VELOCITY_UNIT_PIXELS_PER_SECOND,
                            mMaximumVelocity);
                    int velocityX= (int) velocityTracker.getXVelocity();

                    if (velocityX > mDensityAdjustedSnapVelocity) {
                        // Fling hard enough to move left
                        snapToState(true);
                    } else if (velocityX < -mDensityAdjustedSnapVelocity) {
                        // Fling hard enough to move right
                        snapToState(false);
                    } else {
                        snapToDestination();
                    }
                } else if (mTouchState == TOUCH_STATE_REST
                        && ev.getEventTime() - ev.getDownTime() < MAX_CLICK_DELAY) {
                    animateClose();
                }

                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker= null;
                }

                mTouchState= TOUCH_STATE_REST;
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker= null;
                }

                mTouchState= TOUCH_STATE_REST;
                break;
            default:
                break;
        }

        return result;
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            postInvalidate();
        }
    }

    public void open() {
        setCurrentState(true, false);
    }

    public void animateOpen() {
        setCurrentState(true, true);
    }

    public void close() {
        setCurrentState(false, false);
    }

    public void animateClose() {
        setCurrentState(false, true);
    }

    public void toggle() {
        setCurrentState(!mOpen, false);
    }

    public void animateToggle() {
        setCurrentState(!mOpen, true);
    }

    public float getSliderWidth() {
        return mSliderWidth;
    }

    public void setSliderWidth(float sliderWidth) {
        mSliderWidth= sliderWidth;
    }

    public OnStateChangedListener getOnStateChangedListener() {
        return mListener;
    }

    public void setOnStateChangedListener(OnStateChangedListener listener) {
        mListener= listener;
    }

    private void setCurrentState(final boolean open, final boolean animate) {
        if (animate) {
            snapToState(open, ANIMATION_SCREEN_SET_DURATION_MILLIS);
        } else {
            mOpen= open;
            notifyListener(mOpen);
            int loc= open ? 0 : getActualSliderWidth();
            scrollTo(loc, 0);
        }
        invalidate();
    }

    private int getActualSliderWidth() {
        return (int) (getMeasuredWidth() * mSliderWidth);
    }

    private void snapToDestination() {
        final int screenWidth= getWidth();
        boolean open= mOpen;
        int deltaX= getScrollX() - (!mOpen ? getActualSliderWidth() : 0);

        if (mOpen && ((screenWidth / FRACTION_OF_SCREEN_WIDTH_FOR_SWIPE) < deltaX)) {
            open= false;
        } else if (!mOpen && ((screenWidth / FRACTION_OF_SCREEN_WIDTH_FOR_SWIPE) < -deltaX)) {
            open= true;
        }

        snapToState(open);
    }

    private void snapToState(final boolean open) {
        snapToState(open, -1);
    }

    private void snapToState(final boolean open, final int duration) {
        mOpen= open;
        notifyListener(open);

        final int newX= open ? 0 : getActualSliderWidth();
        final int delta= newX - getScrollX();

        if (duration < 0) {
            // E.g. if they've scrolled 80% of the way, only animation for 20%
            // of the duration
            mScroller.startScroll(getScrollX(), 0, delta, 0, (int) (Math.abs(delta)
                    / (float) getWidth() * ANIMATION_SCREEN_SET_DURATION_MILLIS));
        } else {
            mScroller.startScroll(getScrollX(), 0, delta, 0, duration);
        }
        invalidate();
    }

    private void notifyListener(boolean open) {
        if (mListener != null) {
            mListener.onStateChanged(open);
        }
    }

    boolean shouldIgnoreEvent(MotionEvent ev) {

        float x= ev.getX();
        float y= ev.getY();

        int left= mChild.getLeft() - getScrollX();
        int right= mChild.getRight() - getScrollX();
        int top= mChild.getTop();
        int bottom= mChild.getBottom();

        return x < left || x > right
                || y < top || y > bottom;
    }

    public interface OnStateChangedListener {
        public void onStateChanged(boolean open);
    }
}
