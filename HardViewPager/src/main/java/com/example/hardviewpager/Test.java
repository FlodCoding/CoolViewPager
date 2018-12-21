package com.example.hardviewpager;

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.support.annotation.CallSuper;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.AbsSavedState;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.WindowInsetsCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.FocusFinder;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.Interpolator;
import android.widget.EdgeEffect;
import android.widget.Scroller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * Layout manager that allows the user to flip left and right
 * through pages of data.  You supply an implementation of a
 * {@link PagerAdapter} to generate the pages that the view shows.
 * <p>
 * <p>ViewPager is most often used in conjunction with {@link android.app.Fragment},
 * which is a convenient way to supply and manage the lifecycle of each page.
 * There are standard adapters implemented for using fragments with the ViewPager,
 * which cover the most common use cases.  These are
 * {@link android.support.v4.app.FragmentPagerAdapter} and
 * {@link android.support.v4.app.FragmentStatePagerAdapter}; each of these
 * classes have simple code showing how to build a full user interface
 * with them.
 * <p>
 * <p>Views which are annotated with the {@link DecorView} annotation are treated as
 * part of the view pagers 'decor'. Each decor view's position can be controlled via
 * its {@code android:layout_gravity} attribute. For example:
 * <p>
 * <pre>
 * &lt;android.support.v4.view.ViewPager
 *     android:layout_width=&quot;match_parent&quot;
 *     android:layout_height=&quot;match_parent&quot;&gt;
 *
 *     &lt;android.support.v4.view.PagerTitleStrip
 *         android:layout_width=&quot;match_parent&quot;
 *         android:layout_height=&quot;wrap_content&quot;
 *         android:layout_gravity=&quot;top&quot; /&gt;
 *
 * &lt;/android.support.v4.view.ViewPager&gt;
 * </pre>
 * <p>
 * <p>For more information about how to use ViewPager, read <a
 * href="{@docRoot}training/implementing-navigation/lateral.html">Creating Swipe Views with
 * Tabs</a>.</p>
 * <p>
 * <p>You can find examples of using ViewPager in the API 4+ Support Demos and API 13+ Support Demos
 * sample code.
 */
public class Test extends ViewGroup {
    private static final String TAG = "ViewPager";
    private static final boolean DEBUG = true;

    private static final boolean USE_CACHE = false;

    private static final int DEFAULT_OFFSCREEN_PAGES = 1;
    private static final int MAX_SETTLE_DURATION = 600; // ms
    private static final int MIN_DISTANCE_FOR_FLING = 25; // dips

    private static final int DEFAULT_GUTTER_SIZE = 16; // dips

    private static final int MIN_FLING_VELOCITY = 400; // dips

    static final int[] LAYOUT_ATTRS = new int[]{
            android.R.attr.layout_gravity
    };

    /**
     * Used to track what the expected number of items in the adapter should be.
     * If the app changes this when we don't expect it, we'll throw a big obnoxious exception.
     */
    private int mExpectedAdapterCount;

    static class ItemInfo {
        //object为PagerAdapter的instantiateItem函数返回的对象
        Object object;
        //position为页面的序号，即第几个页面
        int position;
        //是否正在滚动
        boolean scrolling;
        //页面宽度，取值为0到1，表示为页面宽度与ViewPager显示区域宽度比例，默认为1
        float sizeFactor;
        //偏移量，页面移动的偏移量，默认是0,用来乘页面面的宽度，可以算出实际的偏移量
        float offset;
    }

    //页面排序，倒序
    private static final Comparator<ItemInfo> COMPARATOR = new Comparator<ItemInfo>() {
        @Override
        public int compare(ItemInfo lhs, ItemInfo rhs) {
            return lhs.position - rhs.position;
        }
    };

    //插值器，用来根据不同的时间来控制滑动速度
    private static final Interpolator sInterpolator = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    //已经缓存的页面，这个数量由 mOffscreenPageLimit来决定的
    private final ArrayList<ItemInfo> mItems = new ArrayList<ItemInfo>();
    private final ItemInfo mTempItem = new ItemInfo();

    private final Rect mTempRect = new Rect();

    PagerAdapter mAdapter;
    int mCurItem;   // Index of currently displayed page.
    private int mRestoredCurItem = -1;
    private Parcelable mRestoredAdapterState = null;
    private ClassLoader mRestoredClassLoader = null;

    private Scroller mScroller;  //负值是右下，正值是左上,原点在布局的左上角
    private boolean mIsScrollStarted;

    private PagerObserver mObserver;

    private int mPageMargin;
    private Drawable mMarginDrawable;

    private int mTopPageBounds;
    private int mBottomPageBounds;
    private int mLeftPageBounds;
    private int mRightPageBounds;

    // Offsets of the first and last items, if known.
    // Set during population, used to determine if we are at the beginning
    // or end of the pager data set during touch scrolling.
    //第一个和最后一个滑动的偏移量,默认是最大值
    private float mFirstOffset = -Float.MAX_VALUE;
    private float mLastOffset = Float.MAX_VALUE;

    private int mChildWidthMeasureSpec;
    private int mChildHeightMeasureSpec;
    private boolean mInLayout;

    private boolean mScrollingCacheEnabled;

    private boolean mPopulatePending;
    private int mOffscreenPageLimit = DEFAULT_OFFSCREEN_PAGES;

    private boolean mIsBeingDragged;
    private boolean mIsUnableToDrag; //判定当前的手指的Move动作是否有效
    private int mDefaultGutterSize;
    private int mGutterSize;
    private int mTouchSlop;//系统所能识别的最小滑动距离
    /**
     * Position of the last motion event.
     */
    private float mLastMotionX;
    private float mLastMotionY;
    private float mInitialMotionX;
    private float mInitialMotionY;
    /**
     * ID of the active pointer. This is used to retain consistency during
     * drags/flings if multiple pointers are used.
     */
    private int mActivePointerId = INVALID_POINTER;
    /**
     * Sentinel value for no current active pointer.
     * Used by {@link #mActivePointerId}.
     */
    private static final int INVALID_POINTER = -1;

    /**
     * Determines speed during touch scrolling
     */
    private VelocityTracker mVelocityTracker;
    private int mMinimumVelocity;//最小滑动速度 px
    private int mMaximumVelocity;//最大滑动速度 px （在FLING状态下）
    private int mFlingDistance;
    private int mCloseEnough;

    // If the pager is at least this close to its final position, complete the scroll
    // on touch down and let the user interact with the content inside instead of
    // "catching" the flinging pager.
    private static final int CLOSE_ENOUGH = 2; // dp

    private boolean mFakeDragging;//正在进行假拖动
    private long mFakeDragBeginTime;

    //边缘效果
    private EdgeEffect mLeftEdge;
    private EdgeEffect mRightEdge;

    private boolean mFirstLayout = true;
    private boolean mNeedCalculatePageOffsets = false;
    private boolean mCalledSuper;
    private int mDecorChildCount; //Decor的数量

    private List<OnPageChangeListener> mOnPageChangeListeners;
    private OnPageChangeListener mOnPageChangeListener;
    private OnPageChangeListener mInternalPageChangeListener;
    private List<OnAdapterChangeListener> mAdapterChangeListeners;
    private PageTransformer mPageTransformer;
    private int mPageTransformerLayerType;

    private static final int DRAW_ORDER_DEFAULT = 0;
    private static final int DRAW_ORDER_FORWARD = 1;
    private static final int DRAW_ORDER_REVERSE = 2;
    private int mDrawingOrder;
    private ArrayList<View> mDrawingOrderedChildren;
    private static final ViewPositionComparator sPositionComparator = new ViewPositionComparator();

    /**
     * Indicates that the pager is in an idle, settled state. The current page
     * is fully in view and no animation is in progress.
     */
    public static final int SCROLL_STATE_IDLE = 0;       //空闲状态

    /**
     * Indicates that the pager is currently being dragged by the user.
     */
    public static final int SCROLL_STATE_DRAGGING = 1;  //滑动中（手指还没释放）

    /**
     * Indicates that the pager is in the process of settling to a final position.
     */
    public static final int SCROLL_STATE_SETTLING = 2; //正在滑动到最终位置（手指释放了）

    private final Runnable mEndScrollRunnable = new Runnable() {
        @Override
        public void run() {
            setScrollState(SCROLL_STATE_IDLE);
            populate();
        }
    };

    private int mScrollState = SCROLL_STATE_IDLE;

    private Orientation mOrientation = Orientation.VERTICAL;

    //方向
    public enum Orientation {
        HORIZONTAL, VERTICAL
    }

    /**
     * Callback interface for responding to changing state of the selected page.
     */
    public interface OnPageChangeListener {

        /**
         * This method will be invoked when the current page is scrolled, either as part
         * of a programmatically initiated smooth scroll or a user initiated touch scroll.
         *
         * @param position             Position index of the first page currently being displayed.
         *                             Page position+1 will be visible if positionOffset is nonzero.
         * @param positionOffset       Value from [0, 1) indicating the offset from the page at position.
         * @param positionOffsetPixels Value in pixels indicating the offset from position.
         */
        void onPageScrolled(int position, float positionOffset, int positionOffsetPixels);

        /**
         * This method will be invoked when a new page becomes selected. Animation is not
         * necessarily complete.
         *
         * @param position Position index of the new selected page.
         */
        void onPageSelected(int position);

        /**
         * Called when the scroll state changes. Useful for discovering when the user
         * begins dragging, when the pager is automatically settling to the current page,
         * or when it is fully stopped/idle.
         *
         * @param state The new scroll state.
         * @see Test#SCROLL_STATE_IDLE
         * @see Test#SCROLL_STATE_DRAGGING
         * @see Test#SCROLL_STATE_SETTLING
         */
        void onPageScrollStateChanged(int state);
    }

    /**
     * Simple implementation of the {@link OnPageChangeListener} interface with stub
     * implementations of each method. Extend this if you do not intend to override
     * every method of {@link OnPageChangeListener}.
     */
    public static class SimpleOnPageChangeListener implements OnPageChangeListener {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            // This space for rent
        }

        @Override
        public void onPageSelected(int position) {
            // This space for rent
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            // This space for rent
        }
    }

    /**
     * A PageTransformer is invoked whenever a visible/attached page is scrolled.
     * This offers an opportunity for the application to apply a custom transformation
     * to the page views using animation properties.
     * <p>
     * <p>As property animation is only supported as of Android 3.0 and forward,
     * setting a PageTransformer on a ViewPager on earlier platform versions will
     * be ignored.</p>
     */
    public interface PageTransformer {
        /**
         * Apply a property transformation to the given page.
         *
         * @param page     Apply the transformation to this page
         * @param position Position of page relative to the current front-and-center
         *                 position of the pager. 0 is front and center. 1 is one full
         *                 page position to the right, and -1 is one page position to the left.
         */
        void transformPage(@NonNull View page, float position);
    }

    /**
     * Callback interface for responding to adapter changes.
     */
    public interface OnAdapterChangeListener {
        /**
         * Called when the adapter for the given view pager has changed.
         *
         * @param viewPager  ViewPager where the adapter change has happened
         * @param oldAdapter the previously set adapter
         * @param newAdapter the newly set adapter
         */
        void onAdapterChanged(@NonNull Test viewPager,
                              @Nullable PagerAdapter oldAdapter, @Nullable PagerAdapter newAdapter);
    }

    /**
     * Annotation which allows marking of views to be decoration views when added to a view
     * pager.
     * <p>
     * <p>Views marked with this annotation can be added to the view pager with a layout resource.
     * .</p>
     * <p>
     * <p>You can also control whether a view is a decor view but setting
     * {@link LayoutParams#isDecor} on the child's layout params.</p>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    public @interface DecorView {
    }

    public Test(@NonNull Context context) {
        super(context);
        initViewPager();
    }

    public Test(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initViewPager();
    }

    void initViewPager() {
        //就是会调用 Draw()
        setWillNotDraw(false);
        //先分发给Child View进行处理，如果所有的Child View都没有处理，则自己再处理
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setFocusable(true);
        final Context context = getContext();
        mScroller = new Scroller(context, sInterpolator);
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        final float density = context.getResources().getDisplayMetrics().density;

        //系统所能识别的最小滑动距离
        mTouchSlop = configuration.getScaledPagingTouchSlop();
        mMinimumVelocity = (int) (MIN_FLING_VELOCITY * density);
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

        //左右边的边缘效果
        mLeftEdge = new EdgeEffect(context);
        mRightEdge = new EdgeEffect(context);

        mFlingDistance = (int) (MIN_DISTANCE_FOR_FLING * density);
        mCloseEnough = (int) (CLOSE_ENOUGH * density);
        mDefaultGutterSize = (int) (DEFAULT_GUTTER_SIZE * density);

        ViewCompat.setAccessibilityDelegate(this, new MyAccessibilityDelegate());

        if (ViewCompat.getImportantForAccessibility(this)
                == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            ViewCompat.setImportantForAccessibility(this,
                    ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        ViewCompat.setOnApplyWindowInsetsListener(this,
                new android.support.v4.view.OnApplyWindowInsetsListener() {
                    private final Rect mTempRect = new Rect();

                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(final View v,
                                                                  final WindowInsetsCompat originalInsets) {
                        // First let the ViewPager itself try and consume them...
                        final WindowInsetsCompat applied =
                                ViewCompat.onApplyWindowInsets(v, originalInsets);
                        if (applied.isConsumed()) {
                            // If the ViewPager consumed all insets, return now
                            return applied;
                        }

                        // Now we'll manually dispatch the insets to our children. Since ViewPager
                        // children are always full-height, we do not want to use the standard
                        // ViewGroup dispatchApplyWindowInsets since if child 0 consumes them,
                        // the rest of the children will not receive any insets. To workaround this
                        // we manually dispatch the applied insets, not allowing children to
                        // consume them from each other. We do however keep track of any insets
                        // which are consumed, returning the union of our children's consumption
                        final Rect res = mTempRect;
                        res.left = applied.getSystemWindowInsetLeft();
                        res.top = applied.getSystemWindowInsetTop();
                        res.right = applied.getSystemWindowInsetRight();
                        res.bottom = applied.getSystemWindowInsetBottom();

                        for (int i = 0, count = getChildCount(); i < count; i++) {
                            final WindowInsetsCompat childInsets = ViewCompat
                                    .dispatchApplyWindowInsets(getChildAt(i), applied);
                            // Now keep track of any consumed by tracking each dimension's min
                            // value
                            res.left = Math.min(childInsets.getSystemWindowInsetLeft(),
                                    res.left);
                            res.top = Math.min(childInsets.getSystemWindowInsetTop(),
                                    res.top);
                            res.right = Math.min(childInsets.getSystemWindowInsetRight(),
                                    res.right);
                            res.bottom = Math.min(childInsets.getSystemWindowInsetBottom(),
                                    res.bottom);
                        }

                        // Now return a new WindowInsets, using the consumed window insets
                        return applied.replaceSystemWindowInsets(
                                res.left, res.top, res.right, res.bottom);
                    }
                });
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mEndScrollRunnable);
        // To be on the safe side, abort the scroller
        if ((mScroller != null) && !mScroller.isFinished()) {
            mScroller.abortAnimation();
        }
        super.onDetachedFromWindow();
    }

    void setScrollState(int newState) {
        if (mScrollState == newState) {
            return;
        }

        mScrollState = newState;
        if (mPageTransformer != null) {
            // PageTransformers can do complex things that benefit from hardware layers.
            enableLayers(newState != SCROLL_STATE_IDLE);
        }
        dispatchOnScrollStateChanged(newState);
    }

    /**
     * Set a PagerAdapter that will supply views for this pager as needed.
     *
     * @param adapter Adapter to use
     */
    public void setAdapter(@Nullable PagerAdapter adapter) {
        if (mAdapter != null) {
            mAdapter.setViewPagerObserver(null);
            mAdapter.startUpdate(this);
            for (int i = 0; i < mItems.size(); i++) {
                final ItemInfo ii = mItems.get(i);
                mAdapter.destroyItem(this, ii.position, ii.object);
            }
            mAdapter.finishUpdate(this);
            mItems.clear();
            removeNonDecorViews();
            mCurItem = 0;
            scrollTo(0, 0);
        }

        final PagerAdapter oldAdapter = mAdapter;
        mAdapter = adapter;
        mExpectedAdapterCount = 0;

        if (mAdapter != null) {
            if (mObserver == null) {
                mObserver = new PagerObserver();
            }
            mAdapter.setViewPagerObserver(mObserver);
            mPopulatePending = false;
            final boolean wasFirstLayout = mFirstLayout;
            mFirstLayout = true;
            mExpectedAdapterCount = mAdapter.getCount();
            if (mRestoredCurItem >= 0) {
                mAdapter.restoreState(mRestoredAdapterState, mRestoredClassLoader);
                setCurrentItemInternal(mRestoredCurItem, false, true);
                mRestoredCurItem = -1;
                mRestoredAdapterState = null;
                mRestoredClassLoader = null;
            } else if (!wasFirstLayout) {
                populate();
            } else {
                requestLayout();
            }
        }

        // Dispatch the change to any listeners
        if (mAdapterChangeListeners != null && !mAdapterChangeListeners.isEmpty()) {
            for (int i = 0, count = mAdapterChangeListeners.size(); i < count; i++) {
                mAdapterChangeListeners.get(i).onAdapterChanged(this, oldAdapter, adapter);
            }
        }
    }

    private void removeNonDecorViews() {
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (!lp.isDecor) {
                removeViewAt(i);
                i--;
            }
        }
    }

    /**
     * Retrieve the current adapter supplying pages.
     *
     * @return The currently registered PagerAdapter
     */
    @Nullable
    public PagerAdapter getAdapter() {
        return mAdapter;
    }

    /**
     * Add a listener that will be invoked whenever the adapter for this ViewPager changes.
     *
     * @param listener listener to add
     */
    public void addOnAdapterChangeListener(@NonNull OnAdapterChangeListener listener) {
        if (mAdapterChangeListeners == null) {
            mAdapterChangeListeners = new ArrayList<>();
        }
        mAdapterChangeListeners.add(listener);
    }

    /**
     * Remove a listener that was previously added via
     * {@link #addOnAdapterChangeListener(OnAdapterChangeListener)}.
     *
     * @param listener listener to remove
     */
    public void removeOnAdapterChangeListener(@NonNull OnAdapterChangeListener listener) {
        if (mAdapterChangeListeners != null) {
            mAdapterChangeListeners.remove(listener);
        }
    }

    private int getClientWidth() {
        return getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
    }

    private int getClientHeight() {
        return getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
    }

    /**
     * Set the currently selected page. If the ViewPager has already been through its first
     * layout with its current adapter there will be a smooth animated transition between
     * the current item and the specified item.
     *
     * @param item Item index to select
     */
    public void setCurrentItem(int item) {
        mPopulatePending = false;
        setCurrentItemInternal(item, !mFirstLayout, false);
    }

    /**
     * Set the currently selected page.
     *
     * @param item         Item index to select
     * @param smoothScroll True to smoothly scroll to the new item, false to transition immediately
     */
    public void setCurrentItem(int item, boolean smoothScroll) {
        mPopulatePending = false;
        setCurrentItemInternal(item, smoothScroll, false);
    }

    public int getCurrentItem() {
        return mCurItem;
    }

    void setCurrentItemInternal(int item, boolean smoothScroll, boolean always) {
        setCurrentItemInternal(item, smoothScroll, always, 0);
    }

    void setCurrentItemInternal(int item, boolean smoothScroll, boolean always, int velocity) {
        if (mAdapter == null || mAdapter.getCount() <= 0) {
            setScrollingCacheEnabled(false);
            return;
        }
        if (!always && mCurItem == item && mItems.size() != 0) {
            setScrollingCacheEnabled(false);
            return;
        }

        if (item < 0) {
            item = 0;
        } else if (item >= mAdapter.getCount()) {
            item = mAdapter.getCount() - 1;
        }
        final int pageLimit = mOffscreenPageLimit;
        if (item > (mCurItem + pageLimit) || item < (mCurItem - pageLimit)) {
            // We are doing a jump by more than one page.  To avoid
            // glitches, we want to keep all current pages in the view
            // until the scroll ends.
            for (int i = 0; i < mItems.size(); i++) {
                mItems.get(i).scrolling = true;
            }
        }
        final boolean dispatchSelected = mCurItem != item;

        if (mFirstLayout) {
            // We don't have any idea how big we are yet and shouldn't have any pages either.
            // Just set things up and let the pending layout handle things.
            mCurItem = item;
            if (dispatchSelected) {
                dispatchOnPageSelected(item);
            }
            requestLayout();
        } else {
            populate(item);
            scrollToItem(item, smoothScroll, velocity, dispatchSelected);
        }
    }

    private void scrollToItem(int item, boolean smoothScroll, int velocity,
                              boolean dispatchSelected) {

        /*************方向是水平********************/
        if (mOrientation == Orientation.HORIZONTAL) {
            final ItemInfo curInfo = infoForPosition(item);
            int destX = 0;
            if (curInfo != null) {
                final int width = getClientWidth();
                destX = (int) (width * Math.max(mFirstOffset,
                        Math.min(curInfo.offset, mLastOffset)));
            }
            if (smoothScroll) {
                smoothScrollTo(destX, 0, velocity);
                if (dispatchSelected) {
                    dispatchOnPageSelected(item);
                }
            } else {
                if (dispatchSelected) {
                    dispatchOnPageSelected(item);
                }
                completeScroll(false);
                scrollTo(destX, 0);
                pageScrolled(destX);
            }
        } else {
            /*************方向是垂直********************/
            final ItemInfo curInfo = infoForPosition(item);
            int destY = 0;
            if (curInfo != null) {
                final int height = getClientHeight();
                destY = (int) (height * Math.max(mFirstOffset,
                        Math.min(curInfo.offset, mLastOffset)));
            }
            if (smoothScroll) {
                smoothScrollTo(0, destY, velocity);
                if (dispatchSelected) {
                    dispatchOnPageSelected(item);
                }
            } else {
                if (dispatchSelected) {
                    dispatchOnPageSelected(item);
                }
                completeScroll(false);
                scrollTo(0, destY);
                pageScrolled(destY);
            }
        }


    }

    /**
     * Set a listener that will be invoked whenever the page changes or is incrementally
     * scrolled. See {@link OnPageChangeListener}.
     *
     * @param listener Listener to set
     * @deprecated Use {@link #addOnPageChangeListener(OnPageChangeListener)}
     * and {@link #removeOnPageChangeListener(OnPageChangeListener)} instead.
     */
    @Deprecated
    public void setOnPageChangeListener(OnPageChangeListener listener) {
        mOnPageChangeListener = listener;
    }

    /**
     * Add a listener that will be invoked whenever the page changes or is incrementally
     * scrolled. See {@link OnPageChangeListener}.
     * <p>
     * <p>Components that add a listener should take care to remove it when finished.
     * Other components that take ownership of a view may call {@link #clearOnPageChangeListeners()}
     * to remove all attached listeners.</p>
     *
     * @param listener listener to add
     */
    public void addOnPageChangeListener(@NonNull OnPageChangeListener listener) {
        if (mOnPageChangeListeners == null) {
            mOnPageChangeListeners = new ArrayList<>();
        }
        mOnPageChangeListeners.add(listener);
    }

    /**
     * Remove a listener that was previously added via
     * {@link #addOnPageChangeListener(OnPageChangeListener)}.
     *
     * @param listener listener to remove
     */
    public void removeOnPageChangeListener(@NonNull OnPageChangeListener listener) {
        if (mOnPageChangeListeners != null) {
            mOnPageChangeListeners.remove(listener);
        }
    }

    /**
     * Remove all listeners that are notified of any changes in scroll state or position.
     */
    public void clearOnPageChangeListeners() {
        if (mOnPageChangeListeners != null) {
            mOnPageChangeListeners.clear();
        }
    }

    /**
     * Sets a {@link PageTransformer} that will be called for each attached page whenever
     * the scroll position is changed. This allows the application to apply custom property
     * transformations to each page, overriding the default sliding behavior.
     * <p>
     * <p><em>Note:</em> By default, calling this method will cause contained pages to use
     * {@link View#LAYER_TYPE_HARDWARE}. This layer type allows custom alpha transformations,
     * but it will cause issues if any of your pages contain a {@link android.view.SurfaceView}
     * and you have not called {@link android.view.SurfaceView#setZOrderOnTop(boolean)} to put that
     * {@link android.view.SurfaceView} above your app content. To disable this behavior, call
     * {@link #setPageTransformer(boolean, PageTransformer, int)} and pass
     * {@link View#LAYER_TYPE_NONE} for {@code pageLayerType}.</p>
     *
     * @param reverseDrawingOrder true if the supplied PageTransformer requires page views
     *                            to be drawn from last to first instead of first to last.
     * @param transformer         PageTransformer that will modify each page's animation properties
     */
    public void setPageTransformer(boolean reverseDrawingOrder,
                                   @Nullable PageTransformer transformer) {
        setPageTransformer(reverseDrawingOrder, transformer, View.LAYER_TYPE_HARDWARE);
    }

    /**
     * Sets a {@link PageTransformer} that will be called for each attached page whenever
     * the scroll position is changed. This allows the application to apply custom property
     * transformations to each page, overriding the default sliding behavior.
     *
     * @param reverseDrawingOrder true if the supplied PageTransformer requires page views
     *                            to be drawn from last to first instead of first to last.
     * @param transformer         PageTransformer that will modify each page's animation properties
     * @param pageLayerType       View layer type that should be used for ViewPager pages. It should be
     *                            either {@link View#LAYER_TYPE_HARDWARE},
     *                            {@link View#LAYER_TYPE_SOFTWARE}, or
     *                            {@link View#LAYER_TYPE_NONE}.
     */
    public void setPageTransformer(boolean reverseDrawingOrder,
                                   @Nullable PageTransformer transformer, int pageLayerType) {
        final boolean hasTransformer = transformer != null;
        final boolean needsPopulate = hasTransformer != (mPageTransformer != null);
        mPageTransformer = transformer;
        setChildrenDrawingOrderEnabled(hasTransformer);
        if (hasTransformer) {
            mDrawingOrder = reverseDrawingOrder ? DRAW_ORDER_REVERSE : DRAW_ORDER_FORWARD;
            mPageTransformerLayerType = pageLayerType;
        } else {
            mDrawingOrder = DRAW_ORDER_DEFAULT;
        }
        if (needsPopulate) populate();
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        final int index = mDrawingOrder == DRAW_ORDER_REVERSE ? childCount - 1 - i : i;
        final int result =
                ((LayoutParams) mDrawingOrderedChildren.get(index).getLayoutParams()).childIndex;
        return result;
    }

    /**
     * Set a separate OnPageChangeListener for internal use by the support library.
     *
     * @param listener Listener to set
     * @return The old listener that was set, if any.
     */
    OnPageChangeListener setInternalPageChangeListener(OnPageChangeListener listener) {
        OnPageChangeListener oldListener = mInternalPageChangeListener;
        mInternalPageChangeListener = listener;
        return oldListener;
    }

    /**
     * Returns the number of pages that will be retained to either side of the
     * current page in the view hierarchy in an idle state. Defaults to 1.
     *
     * @return How many pages will be kept offscreen on either side
     * @see #setOffscreenPageLimit(int)
     */
    public int getOffscreenPageLimit() {
        return mOffscreenPageLimit;
    }

    /**
     * Set the number of pages that should be retained to either side of the
     * current page in the view hierarchy in an idle state. Pages beyond this
     * limit will be recreated from the adapter when needed.
     * <p>
     * <p>This is offered as an optimization. If you know in advance the number
     * of pages you will need to support or have lazy-loading mechanisms in place
     * on your pages, tweaking this setting can have benefits in perceived smoothness
     * of paging animations and interaction. If you have a small number of pages (3-4)
     * that you can keep active all at once, less time will be spent in layout for
     * newly created view subtrees as the user pages back and forth.</p>
     * <p>
     * <p>You should keep this limit low, especially if your pages have complex layouts.
     * This setting defaults to 1.</p>
     *
     * @param limit How many pages will be kept offscreen in an idle state.
     */
    public void setOffscreenPageLimit(int limit) {
        if (limit < DEFAULT_OFFSCREEN_PAGES) {
            Log.w(TAG, "Requested offscreen page limit " + limit + " too small; defaulting to "
                    + DEFAULT_OFFSCREEN_PAGES);
            limit = DEFAULT_OFFSCREEN_PAGES;
        }
        if (limit != mOffscreenPageLimit) {
            mOffscreenPageLimit = limit;
            populate();
        }
    }

    /**
     * Set the margin between pages.
     *
     * @param marginPixels Distance between adjacent pages in pixels
     * @see #getPageMargin()
     * @see #setPageMarginDrawable(Drawable)
     * @see #setPageMarginDrawable(int)
     */
    public void setPageMargin(int marginPixels) {
        final int oldMargin = mPageMargin;
        mPageMargin = marginPixels;

        final int width = getWidth();
        recomputeScrollPosition(width, width, marginPixels, oldMargin);

        requestLayout();
    }

    /**
     * Return the margin between pages.
     *
     * @return The size of the margin in pixels
     */
    public int getPageMargin() {
        return mPageMargin;
    }

    /**
     * Set a drawable that will be used to fill the margin between pages.
     *
     * @param d Drawable to display between pages
     */
    public void setPageMarginDrawable(@Nullable Drawable d) {
        mMarginDrawable = d;
        if (d != null) refreshDrawableState();
        setWillNotDraw(d == null);
        invalidate();
    }

    /**
     * Set a drawable that will be used to fill the margin between pages.
     *
     * @param resId Resource ID of a drawable to display between pages
     */
    public void setPageMarginDrawable(@DrawableRes int resId) {
        setPageMarginDrawable(ContextCompat.getDrawable(getContext(), resId));
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == mMarginDrawable;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        final Drawable d = mMarginDrawable;
        if (d != null && d.isStateful()) {
            d.setState(getDrawableState());
        }
    }

    // We want the duration of the page snap animation to be influenced by the distance that
    // the screen has to travel, however, we don't want this duration to be effected in a
    // purely linear fashion. Instead, we use this method to moderate the effect that the distance
    // of travel has on the overall snap duration.
    float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * (float) Math.PI / 2.0f;
        return (float) Math.sin(f);
    }

    /**
     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
     *
     * @param x the number of pixels to scroll by on the X axis
     * @param y the number of pixels to scroll by on the Y axis
     */
    void smoothScrollTo(int x, int y) {
        smoothScrollTo(x, y, 0);
    }

    /**
     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
     *
     * @param x        the number of pixels to scroll by on the X axis
     * @param y        the number of pixels to scroll by on the Y axis
     * @param velocity the velocity associated with a fling, if applicable. (0 otherwise)
     */
    void smoothScrollTo(int x, int y, int velocity) {
        if (getChildCount() == 0) {
            // Nothing to do.
            setScrollingCacheEnabled(false);
            return;
        }

        int sxy;
        boolean wasScrolling = (mScroller != null) && !mScroller.isFinished();

        /*************方向是水平********************/
        if (mOrientation == Orientation.HORIZONTAL) {
            if (wasScrolling) {
                // We're in the middle of a previously initiated scrolling. Check to see
                // whether that scrolling has actually started (if we always call getStartX
                // we can get a stale value from the scroller if it hadn't yet had its first
                // computeScrollOffset call) to decide what is the current scrolling position.
                sxy = mIsScrollStarted ? mScroller.getCurrX() : mScroller.getStartX();
                // And abort the current scrolling.
                mScroller.abortAnimation();
                setScrollingCacheEnabled(false);
            } else {
                sxy = getScrollX();
            }
            int sy = getScrollY();
            int dx = x - sxy;
            int dy = y - sy;
            if (dx == 0 && dy == 0) {
                completeScroll(false);
                populate();
                setScrollState(SCROLL_STATE_IDLE);
                return;
            }

            setScrollingCacheEnabled(true);
            setScrollState(SCROLL_STATE_SETTLING);

            final int width = getClientWidth();
            final int halfWidth = width / 2;

            //滑动距离占宽度的比例
            final float distanceRatio = Math.min(1f, 1.0f * Math.abs(dx) / width);
            //进行变速
            final float distance = halfWidth + halfWidth
                    * distanceInfluenceForSnapDuration(distanceRatio);

            int duration;
            velocity = Math.abs(velocity);
            if (velocity > 0) {
                //推算出需要滑动的时间：4倍的手指滑动时间
                duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
            } else {
                //如果手指滑动速度0，就自己推算滑动时间
                final float pageWidth = width * mAdapter.getPageWidth(mCurItem);
                final float pageDelta = (float) Math.abs(dx) / (pageWidth + mPageMargin);
                duration = (int) ((pageDelta + 1) * 100);
            }
            //未设置过,则采用原始逻辑获取duration
            duration = Math.min(duration, MAX_SETTLE_DURATION);

            // Reset the "scroll started" flag. It will be flipped to true in all places
            // where we call computeScrollOffset().
            mIsScrollStarted = false;
            mScroller.startScroll(sxy, sy, dx, dy, duration);
        } else {
            /*************方向是垂直********************/
            if (wasScrolling) {
                // We're in the middle of a previously initiated scrolling. Check to see
                // whether that scrolling has actually started (if we always call getStartX
                // we can get a stale value from the scroller if it hadn't yet had its first
                // computeScrollOffset call) to decide what is the current scrolling position.
                sxy = mIsScrollStarted ? mScroller.getCurrY() : mScroller.getStartY();
                // And abort the current scrolling.
                mScroller.abortAnimation();
                setScrollingCacheEnabled(false);
            } else {
                sxy = getScrollY();
            }
            int sx = getScrollX();
            int dx = x - sx;
            int dy = y - sxy;
            if (dx == 0 && dy == 0) {
                completeScroll(false);
                populate();
                setScrollState(SCROLL_STATE_IDLE);
                return;
            }

            setScrollingCacheEnabled(true);
            setScrollState(SCROLL_STATE_SETTLING);

            final int height = getClientHeight();
            final int halfHeight = height / 2;

            //滑动距离占宽度的比例
            final float distanceRatio = Math.min(1f, 1.0f * Math.abs(dy) / height);
            //进行变速
            final float distance = halfHeight + halfHeight
                    * distanceInfluenceForSnapDuration(distanceRatio);

            int duration;
            velocity = Math.abs(velocity);
            if (velocity > 0) {
                //推算出需要滑动的时间：4倍的手指滑动时间
                duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
            } else {
                //如果手指滑动速度0，就自己推算滑动时间
                final float pageHeight = height * mAdapter.getPageWidth(mCurItem);
                final float pageDelta = (float) Math.abs(dx) / (pageHeight + mPageMargin);
                duration = (int) ((pageDelta + 1) * 100);
            }
            //未设置过,则采用原始逻辑获取duration
            duration = Math.min(duration, MAX_SETTLE_DURATION);

            // Reset the "scroll started" flag. It will be flipped to true in all places
            // where we call computeScrollOffset().
            mIsScrollStarted = false;
            mScroller.startScroll(sx, sxy, dx, dy, duration);

        }

        ViewCompat.postInvalidateOnAnimation(this);
    }

    //添加Item到Items里面，会调用到instantiateItem来生成新的Item
    ItemInfo addNewItem(int position, int index) {
        ItemInfo ii = new ItemInfo();
        ii.position = position;
        ii.object = mAdapter.instantiateItem(this, position);
        ii.sizeFactor = mAdapter.getPageWidth(position);
        if (index < 0 || index >= mItems.size()) {
            mItems.add(ii);
        } else {
            mItems.add(index, ii);
        }
        return ii;
    }

    void dataSetChanged() {
        // This method only gets called if our observer is attached, so mAdapter is non-null.

        final int adapterCount = mAdapter.getCount();
        mExpectedAdapterCount = adapterCount;
        boolean needPopulate = mItems.size() < mOffscreenPageLimit * 2 + 1
                && mItems.size() < adapterCount;
        int newCurrItem = mCurItem;

        boolean isUpdating = false;
        for (int i = 0; i < mItems.size(); i++) {
            final ItemInfo ii = mItems.get(i);
            final int newPos = mAdapter.getItemPosition(ii.object);

            if (newPos == PagerAdapter.POSITION_UNCHANGED) {
                continue;
            }

            if (newPos == PagerAdapter.POSITION_NONE) {
                mItems.remove(i);
                i--;

                if (!isUpdating) {
                    mAdapter.startUpdate(this);
                    isUpdating = true;
                }

                mAdapter.destroyItem(this, ii.position, ii.object);
                needPopulate = true;

                if (mCurItem == ii.position) {
                    // Keep the current item in the valid range
                    newCurrItem = Math.max(0, Math.min(mCurItem, adapterCount - 1));
                    needPopulate = true;
                }
                continue;
            }

            if (ii.position != newPos) {
                if (ii.position == mCurItem) {
                    // Our current item changed position. Follow it.
                    newCurrItem = newPos;
                }

                ii.position = newPos;
                needPopulate = true;
            }
        }

        if (isUpdating) {
            mAdapter.finishUpdate(this);
        }

        Collections.sort(mItems, COMPARATOR);

        if (needPopulate) {
            // Reset our known page widths; populate will recompute them.
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (!lp.isDecor) {
                    lp.widthFactor = 0.f;
                }
            }

            setCurrentItemInternal(newCurrItem, false, true);
            requestLayout();
        }
    }

    void populate() {
        populate(mCurItem);
    }

    //mItems类似于一排窗口这些窗口的长度是mOffscreenPageLimit*2+1，这个排窗口是不断滑动的
    //mItems的第一个ItemInfo的position有可能从3开始，但也是按照顺序排列下去的
    void populate(int newCurrentItem) {
        ItemInfo oldCurInfo = null;
        if (mCurItem != newCurrentItem) {
            oldCurInfo = infoForPosition(mCurItem);
            mCurItem = newCurrentItem;
        }

        if (mAdapter == null) {
            //对页卡的顺序进行排序
            sortChildDrawingOrder();
            return;
        }

        // Bail now if we are waiting to populate.  This is to hold off
        // on creating views from the time the user releases their finger to
        // fling to a new position until we have finished the scroll to
        // that position, avoiding glitches from happening at that point.
        if (mPopulatePending) {
            if (DEBUG) Log.i(TAG, "populate is pending, skipping for now...");
            sortChildDrawingOrder();
            return;
        }

        // Also, don't populate until we are attached to a window.  This is to
        // avoid trying to populate before we have restored our view hierarchy
        // state and conflicting with what is restored.
        if (getWindowToken() == null) {
            return;
        }

        //告诉 PagerAdapter要开始更新了，startUpdate
        mAdapter.startUpdate(this);

        //一般情况下mItems的长度是 2*pageLimit + 1
        final int pageLimit = mOffscreenPageLimit;
        //起始位置：当前的页面减去缓存页面数量，并确保开始的位置是大于等于0。
        final int startPos = Math.max(0, mCurItem - pageLimit);
        final int N = mAdapter.getCount();
        //结束位置：当前页面加上缓存页面的数量，并确保是小于数据源的个数
        final int endPos = Math.min(N - 1, mCurItem + pageLimit);

        //如果从Adapter拿到的大小和自认为的大小不一样，那说明Adapter数据源的个数发生变化了，抛出异常
        if (N != mExpectedAdapterCount) {
            String resName;
            try {
                resName = getResources().getResourceName(getId());
            } catch (Resources.NotFoundException e) {
                resName = Integer.toHexString(getId());
            }
            throw new IllegalStateException("The application's PagerAdapter changed the adapter's"
                    + " contents without calling PagerAdapter#notifyDataSetChanged!"
                    + " Expected adapter item count: " + mExpectedAdapterCount + ", found: " + N
                    + " Pager id: " + resName
                    + " Pager class: " + getClass()
                    + " Problematic adapter: " + mAdapter.getClass());
        }

        // Locate the currently focused item or add it if needed.
        // ***从mItems拿到当前焦点的ItemInfo
        int curIndex = -1;
        ItemInfo curItem = null;
        for (curIndex = 0; curIndex < mItems.size(); curIndex++) {
            //从mItems列表中找到这个mCurItem的Pos的ItemInfo
            final ItemInfo ii = mItems.get(curIndex);
            //>= 是因为Item的position总是会大于等于在列表的序号
            if (ii.position >= mCurItem) {
                if (ii.position == mCurItem) curItem = ii;
                break;
            }
        }

        //在mItems还未保存这个ItemInfo，则创建这个ItemInfo
        if (curItem == null && N > 0) {
            curItem = addNewItem(mCurItem, curIndex);
        }

        // Fill 3x the available width or up to the number of offscreen
        // pages requested to either side, whichever is larger.
        // If we have no current item we have no work to do.

        /*************方向是水平********************/
        if (mOrientation == Orientation.HORIZONTAL) {
            //**管理mItems中的其余对象
            //由于mItems的长度是有限的，当页面总数大于mItems的长度时需要不断添加和移除ItemInfo进来
            if (curItem != null) {
                //**1、开始调整curItem左边的对象
                //左边整体的的宽度（下面会进行累加计算，用一个抽象的数字代替）
                float extraWidthLeft = 0.f;
                //curIndex是mItems的当前索引，itemIndex是curIndex左边的索引
                int itemIndex = curIndex - 1;
                //获取左边的ItemInfo对象
                ItemInfo ii = itemIndex >= 0 ? mItems.get(itemIndex) : null;
                //显示区域的宽度
                final int clientWidth = getClientWidth();
                //左边需要的宽度:实际宽度和可视区域的比例，默认情况下是1.0f
                final float leftWidthNeeded = clientWidth <= 0 ? 0 :
                        2.f - curItem.sizeFactor + (float) getPaddingLeft() / (float) clientWidth;
                //遍历左半部分
                for (int pos = mCurItem - 1; pos >= 0; pos--) {
                    //pos < startPos说明已经遍历完了（所需要的宽度并没有期望的大）
                    if (extraWidthLeft >= leftWidthNeeded && pos < startPos) {

                        //如果是空的话就退出循环，说明已经遍历完了
                        if (ii == null) {
                            break;
                        }

                        //从下面拿到的ItemInfo，如果不为空就走下面的销毁和移除的流程
                        if (pos == ii.position && !ii.scrolling) {
                            mItems.remove(itemIndex);
                            mAdapter.destroyItem(this, pos, ii.object);
                            if (DEBUG) {
                                Log.i(TAG, "populate() - destroyItem() with pos: " + pos
                                        + " view: " + ((View) ii.object));
                            }
                            itemIndex--;//删除后左边的的索引也要减1
                            curIndex--; //当前的索引也要减1
                            ii = itemIndex >= 0 ? mItems.get(itemIndex) : null;//然后会再次进入，并走上面的break
                        }
                    } else if (ii != null && pos == ii.position) {
                        extraWidthLeft += ii.sizeFactor;//累加curItem左边需要的宽度
                        itemIndex--;                     //在再向左移动一个位置
                        ii = itemIndex >= 0 ? mItems.get(itemIndex) : null; //拿到这个左左边的对象，如果是最后一个会准备走上面的移除流程
                    } else {
                        ii = addNewItem(pos, itemIndex + 1);  //新建一个ItemInfo添加到itemIndex的右边，也是curIndex的左边
                        extraWidthLeft += ii.sizeFactor;           //累加左边的宽度
                        curIndex++;                                 //因为左边插入了一个对象，所以当前curIndex要加1
                        ii = itemIndex >= 0 ? mItems.get(itemIndex) : null; //拿到这个左左边的对象，如果是最后一个会准备走上面的移除流程
                    }
                }

                //**2、开始调整右半边的对象
                float extraWidthRight = curItem.sizeFactor; //右边整体宽度
                itemIndex = curIndex + 1;                    //右边对象的索引
                if (extraWidthRight < 2.f) {
                    //判断方式与上面大致相同
                    ii = itemIndex < mItems.size() ? mItems.get(itemIndex) : null;
                    final float rightWidthNeeded = clientWidth <= 0 ? 0 :
                            (float) getPaddingRight() / (float) clientWidth + 2.f;
                    for (int pos = mCurItem + 1; pos < N; pos++) {
                        if (extraWidthRight >= rightWidthNeeded && pos > endPos) {
                            if (ii == null) {
                                break;
                            }
                            if (pos == ii.position && !ii.scrolling) {
                                mItems.remove(itemIndex);
                                mAdapter.destroyItem(this, pos, ii.object);
                                if (DEBUG) {
                                    Log.i(TAG, "populate() - destroyItem() with pos: " + pos
                                            + " view: " + ((View) ii.object));
                                }
                                ii = itemIndex < mItems.size() ? mItems.get(itemIndex) : null;
                            }
                        } else if (ii != null && pos == ii.position) {
                            extraWidthRight += ii.sizeFactor;
                            itemIndex++;
                            ii = itemIndex < mItems.size() ? mItems.get(itemIndex) : null;
                        } else {
                            ii = addNewItem(pos, itemIndex);
                            itemIndex++;
                            extraWidthRight += ii.sizeFactor;
                            ii = itemIndex < mItems.size() ? mItems.get(itemIndex) : null;
                        }
                    }
                }
            }
        } else {
            /*************方向是垂直********************/
            //**管理mItems中的其余对象
            //由于mItems的长度是有限的，当页面总数大于mItems的长度时需要不断添加和移除ItemInfo进来
            if (curItem != null) {
                //**1、开始调整curItem左边的对象
                //左边整体的的宽度（下面会进行累加计算，用一个抽象的数字代替）
                float extraHeightTop = 0.f;
                //curIndex是mItems的当前索引，itemIndex是curIndex左边的索引
                int itemIndex = curIndex - 1;
                //获取左边的ItemInfo对象
                ItemInfo ii = itemIndex >= 0 ? mItems.get(itemIndex) : null;
                //显示区域的宽度
                final int clientHeight = getClientHeight();
                //左边需要的宽度:实际宽度和可视区域的比例，默认情况下是1.0f
                final float topHeightNeeded = clientHeight <= 0 ? 0 :
                        2.f - curItem.sizeFactor + (float) getPaddingTop() / (float) clientHeight;
                //遍历左半部分
                for (int pos = mCurItem - 1; pos >= 0; pos--) {
                    //pos < startPos说明已经遍历完了（所需要的宽度并没有期望的大）
                    if (extraHeightTop >= topHeightNeeded && pos < startPos) {

                        //如果是空的话就退出循环，说明已经遍历完了
                        if (ii == null) {
                            break;
                        }

                        //从下面拿到的ItemInfo，如果不为空就走下面的销毁和移除的流程
                        if (pos == ii.position && !ii.scrolling) {
                            mItems.remove(itemIndex);
                            mAdapter.destroyItem(this, pos, ii.object);
                            if (DEBUG) {
                                Log.i(TAG, "populate() - destroyItem() with pos: " + pos
                                        + " view: " + ((View) ii.object));
                            }
                            itemIndex--;//删除后左边的的索引也要减1
                            curIndex--; //当前的索引也要减1
                            ii = itemIndex >= 0 ? mItems.get(itemIndex) : null;//然后会再次进入，并走上面的break
                        }
                    } else if (ii != null && pos == ii.position) {
                        extraHeightTop += ii.sizeFactor;//累加curItem左边需要的宽度
                        itemIndex--;                     //在再向左移动一个位置
                        ii = itemIndex >= 0 ? mItems.get(itemIndex) : null; //拿到这个左左边的对象，如果是最后一个会准备走上面的移除流程
                    } else {
                        ii = addNewItem(pos, itemIndex + 1);  //新建一个ItemInfo添加到itemIndex的右边，也是curIndex的左边
                        extraHeightTop += ii.sizeFactor;           //累加左边的宽度
                        curIndex++;                                 //因为左边插入了一个对象，所以当前curIndex要加1
                        ii = itemIndex >= 0 ? mItems.get(itemIndex) : null; //拿到这个左左边的对象，如果是最后一个会准备走上面的移除流程
                    }
                }

                //**2、开始调整右半边的对象
                float extraHeightBottom = curItem.sizeFactor; //右边整体宽度
                itemIndex = curIndex + 1;                    //右边对象的索引
                if (extraHeightBottom < 2.f) {
                    //判断方式与上面大致相同
                    ii = itemIndex < mItems.size() ? mItems.get(itemIndex) : null;
                    final float BottomHeightNeeded = clientHeight <= 0 ? 0 :
                            (float) getPaddingBottom() / (float) clientHeight + 2.f;
                    for (int pos = mCurItem + 1; pos < N; pos++) {
                        if (extraHeightBottom >= BottomHeightNeeded && pos > endPos) {
                            if (ii == null) {
                                break;
                            }
                            if (pos == ii.position && !ii.scrolling) {
                                mItems.remove(itemIndex);
                                mAdapter.destroyItem(this, pos, ii.object);
                                if (DEBUG) {
                                    Log.i(TAG, "populate() - destroyItem() with pos: " + pos
                                            + " view: " + ((View) ii.object));
                                }
                                ii = itemIndex < mItems.size() ? mItems.get(itemIndex) : null;
                            }
                        } else if (ii != null && pos == ii.position) {
                            extraHeightBottom += ii.sizeFactor;
                            itemIndex++;
                            ii = itemIndex < mItems.size() ? mItems.get(itemIndex) : null;
                        } else {
                            ii = addNewItem(pos, itemIndex);
                            itemIndex++;
                            extraHeightBottom += ii.sizeFactor;
                            ii = itemIndex < mItems.size() ? mItems.get(itemIndex) : null;
                        }
                    }
                }

            }
        }


        //计算Items的偏移量
        calculatePageOffsets(curItem, curIndex, oldCurInfo);

        //回调PagerAdapter的setPrimaryItem，告诉PagerAdapter当前显示的页面
        mAdapter.setPrimaryItem(this, mCurItem, curItem.object);


        if (DEBUG)

        {
            Log.i(TAG, "Current page list:");
            for (int i = 0; i < mItems.size(); i++) {
                Log.i(TAG, "#" + i + ": page " + mItems.get(i).position);
            }
        }

        //回调PagerAdapter的finishUpdate，告诉PagerAdapter页面更新结束
        mAdapter.finishUpdate(this);

        // Check width measurement of current pages and drawing sort order.
        // Update LayoutParams as needed.
        final int childCount = getChildCount();
        for (
                int i = 0;
                i < childCount; i++)

        {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            lp.childIndex = i;
            if (!lp.isDecor && lp.widthFactor == 0.f) {
                // 0 means requery the adapter for this, it doesn't have a valid width.
                final ItemInfo ii = infoForChild(child);
                if (ii != null) {
                    lp.widthFactor = ii.sizeFactor;
                    lp.position = ii.position;
                }
            }
        }

        sortChildDrawingOrder();

        if (

                hasFocus())

        {
            View currentFocused = findFocus();
            ItemInfo ii = currentFocused != null ? infoForAnyChild(currentFocused) : null;
            if (ii == null || ii.position != mCurItem) {
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    ii = infoForChild(child);
                    if (ii != null && ii.position == mCurItem) {
                        if (child.requestFocus(View.FOCUS_FORWARD)) {
                            break;
                        }
                    }
                }
            }
        }
    }

    //用来更新页集的排序，
    private void sortChildDrawingOrder() {
        if (mDrawingOrder != DRAW_ORDER_DEFAULT) {
            if (mDrawingOrderedChildren == null) {
                mDrawingOrderedChildren = new ArrayList<View>();
            } else {
                mDrawingOrderedChildren.clear();
            }
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                mDrawingOrderedChildren.add(child);
            }
            Collections.sort(mDrawingOrderedChildren, sPositionComparator);
        }
    }

    private void calculatePageOffsets(ItemInfo curItem, int curIndex, ItemInfo oldCurInfo) {
        final int N = mAdapter.getCount();
        final int width = getClientWidth();
        final float marginOffset = width > 0 ? (float) mPageMargin / width : 0;
        // Fix up offsets for later layout.
        if (oldCurInfo != null) {
            final int oldCurPosition = oldCurInfo.position;
            // Base offsets off of oldCurInfo.
            //旧的在左边
            if (oldCurPosition < curItem.position) {
                int itemIndex = 0;
                ItemInfo ii = null;
                float offset = oldCurInfo.offset + oldCurInfo.sizeFactor + marginOffset;
                for (int pos = oldCurPosition + 1;
                     pos <= curItem.position && itemIndex < mItems.size(); pos++) {
                    ii = mItems.get(itemIndex);
                    while (pos > ii.position && itemIndex < mItems.size() - 1) {
                        itemIndex++;
                        ii = mItems.get(itemIndex);
                    }
                    while (pos < ii.position) {
                        // We don't have an item populated for this,
                        // ask the adapter for an offset.
                        offset += mAdapter.getPageWidth(pos) + marginOffset;
                        pos++;
                    }
                    ii.offset = offset;
                    offset += ii.sizeFactor + marginOffset;
                }

                //旧的在右边
            } else if (oldCurPosition > curItem.position) {
                int itemIndex = mItems.size() - 1;
                ItemInfo ii = null;
                float offset = oldCurInfo.offset;
                for (int pos = oldCurPosition - 1;
                     pos >= curItem.position && itemIndex >= 0; pos--) {
                    ii = mItems.get(itemIndex);
                    while (pos < ii.position && itemIndex > 0) {
                        itemIndex--;
                        ii = mItems.get(itemIndex);
                    }
                    while (pos > ii.position) {
                        // We don't have an item populated for this,
                        // ask the adapter for an offset.
                        offset -= mAdapter.getPageWidth(pos) + marginOffset;
                        pos--;
                    }
                    offset -= ii.sizeFactor + marginOffset;
                    ii.offset = offset;
                }
            }
        }

        // Base all offsets off of curItem.
        final int itemCount = mItems.size();
        float offset = curItem.offset;
        int pos = curItem.position - 1;
        mFirstOffset = curItem.position == 0 ? curItem.offset : -Float.MAX_VALUE;
        mLastOffset = curItem.position == N - 1
                ? curItem.offset + curItem.sizeFactor - 1 : Float.MAX_VALUE;
        // Previous pages
        for (int i = curIndex - 1; i >= 0; i--, pos--) {
            final ItemInfo ii = mItems.get(i);
            while (pos > ii.position) {
                offset -= mAdapter.getPageWidth(pos--) + marginOffset;
            }
            offset -= ii.sizeFactor + marginOffset;
            ii.offset = offset;
            if (ii.position == 0) mFirstOffset = offset;
        }
        offset = curItem.offset + curItem.sizeFactor + marginOffset;
        pos = curItem.position + 1;
        // Next pages
        for (int i = curIndex + 1; i < itemCount; i++, pos++) {
            final ItemInfo ii = mItems.get(i);
            while (pos < ii.position) {
                offset += mAdapter.getPageWidth(pos++) + marginOffset;
            }
            if (ii.position == N - 1) {
                mLastOffset = offset + ii.sizeFactor - 1;
            }
            ii.offset = offset;
            offset += ii.sizeFactor + marginOffset;
        }

        mNeedCalculatePageOffsets = false;
    }

    /**
     * This is the persistent state that is saved by ViewPager.  Only needed
     * if you are creating a sublass of ViewPager that must save its own
     * state, in which case it should implement a subclass of this which
     * contains that state.
     */
    public static class SavedState extends AbsSavedState {
        int position;
        Parcelable adapterState;
        ClassLoader loader;

        public SavedState(@NonNull Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(position);
            out.writeParcelable(adapterState, flags);
        }

        @Override
        public String toString() {
            return "FragmentPager.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " position=" + position + "}";
        }

        public static final Creator<SavedState> CREATOR = new ClassLoaderCreator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in, loader);
            }

            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in, null);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };

        SavedState(Parcel in, ClassLoader loader) {
            super(in, loader);
            if (loader == null) {
                loader = getClass().getClassLoader();
            }
            position = in.readInt();
            adapterState = in.readParcelable(loader);
            this.loader = loader;
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.position = mCurItem;
        if (mAdapter != null) {
            ss.adapterState = mAdapter.saveState();
        }
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        if (mAdapter != null) {
            mAdapter.restoreState(ss.adapterState, ss.loader);
            setCurrentItemInternal(ss.position, false, true);
        } else {
            mRestoredCurItem = ss.position;
            mRestoredAdapterState = ss.adapterState;
            mRestoredClassLoader = ss.loader;
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (!checkLayoutParams(params)) {
            params = generateLayoutParams(params);
        }
        final LayoutParams lp = (LayoutParams) params;
        // Any views added via inflation should be classed as part of the decor
        lp.isDecor |= isDecorView(child);
        if (mInLayout) {
            if (lp != null && lp.isDecor) {
                throw new IllegalStateException("Cannot add pager decor view during layout");
            }
            lp.needsMeasure = true;
            addViewInLayout(child, index, params);
        } else {
            super.addView(child, index, params);
        }

        if (USE_CACHE) {
            if (child.getVisibility() != GONE) {
                child.setDrawingCacheEnabled(mScrollingCacheEnabled);
            } else {
                child.setDrawingCacheEnabled(false);
            }
        }
    }

    private static boolean isDecorView(@NonNull View view) {
        Class<?> clazz = view.getClass();
        return clazz.getAnnotation(DecorView.class) != null;
    }

    @Override
    public void removeView(View view) {
        if (mInLayout) {
            removeViewInLayout(view);
        } else {
            super.removeView(view);
        }
    }

    ItemInfo infoForChild(View child) {
        for (int i = 0; i < mItems.size(); i++) {
            ItemInfo ii = mItems.get(i);
            if (mAdapter.isViewFromObject(child, ii.object)) {
                return ii;
            }
        }
        return null;
    }

    ItemInfo infoForAnyChild(View child) {
        ViewParent parent;
        while ((parent = child.getParent()) != this) {
            if (parent == null || !(parent instanceof View)) {
                return null;
            }
            child = (View) parent;
        }
        return infoForChild(child);
    }

    ItemInfo infoForPosition(int position) {
        for (int i = 0; i < mItems.size(); i++) {
            ItemInfo ii = mItems.get(i);
            if (ii.position == position) {
                return ii;
            }
        }
        return null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mFirstLayout = true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // For simple implementation, our internal size is always 0.
        // We depend on the container to specify the layout size of
        // our view.  We can't really know what it is since we will be
        // adding and removing different arbitrary views and do not
        // want the layout to change as this happens.

        //根据布局文件设置尺寸，默认是0
        setMeasuredDimension(getDefaultSize(0, widthMeasureSpec),
                getDefaultSize(0, heightMeasureSpec));

        /*************方向是水平********************/
        if (mOrientation == Orientation.HORIZONTAL) {
            final int measuredWidth = getMeasuredWidth();
            final int maxGutterSize = measuredWidth / 10;
            mGutterSize = Math.min(maxGutterSize, mDefaultGutterSize);
        } else {
            /*************方向是垂直********************/
            final int measuredHeight = getMeasuredHeight();
            final int maxGutterSize = measuredHeight / 10;
            mGutterSize = Math.min(maxGutterSize, mDefaultGutterSize);
        }


        // Children are just made to fill our space.
        // 意思是只能在一个显示区域内显示一个Children
        // ViewPager的宽高去除掉内边距就是children 的宽高
        int childWidthSize = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        int childHeightSize = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();

        /*
         * Make sure all children have been properly measured. Decor views first.
         * Right now we cheat and make this less complicated by assuming decor
         * views won't intersect. We will pin to edges based on gravity.
         */

        //===测量DecorView
        int size = getChildCount();
        for (int i = 0; i < size; ++i) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (lp != null && lp.isDecor) {
                    //获取DecorView的在水平方向和竖直方向上的Gravity
                    final int hgrav = lp.gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int vgrav = lp.gravity & Gravity.VERTICAL_GRAVITY_MASK;
                    //默认DecorView模式对应的宽高是wrap_content
                    int widthMode = MeasureSpec.AT_MOST;
                    int heightMode = MeasureSpec.AT_MOST;
                    //true 水平或垂直方向上是 match_parent，否则是wrap
                    boolean consumeVertical = vgrav == Gravity.TOP || vgrav == Gravity.BOTTOM;
                    boolean consumeHorizontal = hgrav == Gravity.LEFT || hgrav == Gravity.RIGHT;

                    // 得到测量的模式
                    if (consumeVertical) {
                        widthMode = MeasureSpec.EXACTLY;
                    } else if (consumeHorizontal) {
                        heightMode = MeasureSpec.EXACTLY;
                    }

                    int widthSize = childWidthSize;
                    int heightSize = childHeightSize;
                    if (lp.width != LayoutParams.WRAP_CONTENT) {
                        widthMode = MeasureSpec.EXACTLY;
                        if (lp.width != LayoutParams.MATCH_PARENT) {
                            widthSize = lp.width;
                        }
                    }
                    if (lp.height != LayoutParams.WRAP_CONTENT) {
                        heightMode = MeasureSpec.EXACTLY;
                        if (lp.height != LayoutParams.MATCH_PARENT) {
                            heightSize = lp.height;
                        }
                    }
                    final int widthSpec = MeasureSpec.makeMeasureSpec(widthSize, widthMode);
                    final int heightSpec = MeasureSpec.makeMeasureSpec(heightSize, heightMode);
                    child.measure(widthSpec, heightSpec);

                    if (consumeVertical) {
                        childHeightSize -= child.getMeasuredHeight();
                    } else if (consumeHorizontal) {
                        childWidthSize -= child.getMeasuredWidth();
                    }
                }
            }
        }

        mChildWidthMeasureSpec = MeasureSpec.makeMeasureSpec(childWidthSize, MeasureSpec.EXACTLY);
        mChildHeightMeasureSpec = MeasureSpec.makeMeasureSpec(childHeightSize, MeasureSpec.EXACTLY);

        // Make sure we have created all fragments that we need to have shown.
        // 从Adapter中更新childView
        mInLayout = true;
        populate();
        mInLayout = false;

        // Page views next.
        //===测量ChildView
        size = getChildCount();
        for (int i = 0; i < size; ++i) {
            final View child = getChildAt(i);
            //对GONE的页卡就不测量了
            if (child.getVisibility() != GONE) {
                if (DEBUG) {
                    Log.v(TAG, "Measuring #" + i + " " + child + ": " + mChildWidthMeasureSpec);
                }

                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (lp == null || !lp.isDecor) {
                    final int widthSpec = MeasureSpec.makeMeasureSpec(
                            (int) (childWidthSize * lp.widthFactor), MeasureSpec.EXACTLY);
                    child.measure(widthSpec, mChildHeightMeasureSpec);
                }
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Make sure scroll position is set correctly.
        if (w != oldw) {
            recomputeScrollPosition(w, oldw, mPageMargin, mPageMargin);
        }
    }

    //TODO recompute height
    private void recomputeScrollPosition(int width, int oldWidth, int margin, int oldMargin) {
        if (oldWidth > 0 && !mItems.isEmpty()) {
            if (!mScroller.isFinished()) {
                mScroller.setFinalX(getCurrentItem() * getClientWidth());
            } else {
                final int widthWithMargin = width - getPaddingLeft() - getPaddingRight() + margin;
                final int oldWidthWithMargin = oldWidth - getPaddingLeft() - getPaddingRight()
                        + oldMargin;
                final int xpos = getScrollX();
                final float pageOffset = (float) xpos / oldWidthWithMargin;
                final int newOffsetPixels = (int) (pageOffset * widthWithMargin);

                scrollTo(newOffsetPixels, getScrollY());
            }
        } else {
            final ItemInfo ii = infoForPosition(mCurItem);
            final float scrollOffset = ii != null ? Math.min(ii.offset, mLastOffset) : 0;
            final int scrollPos =
                    (int) (scrollOffset * (width - getPaddingLeft() - getPaddingRight()));
            if (scrollPos != getScrollX()) {
                completeScroll(false);
                scrollTo(scrollPos, getScrollY());
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int count = getChildCount();
        int width = r - l;
        int height = b - t;
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int paddingRight = getPaddingRight();
        int paddingBottom = getPaddingBottom();
        final int scrollX = getScrollX();

        //DecorView 数量
        int decorCount = 0;

        // First pass - decor views. We need to do this in two passes so that
        // we have the proper offsets for non-decor views later.
        // 先对DecorView进行Layout,之所以先对DecorView布局，是为了让普通的页卡能有合适的偏移
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            //布局的视图必须不是Gone的
            if (child.getVisibility() != GONE) {
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                int childLeft = 0;
                int childTop = 0;
                if (lp.isDecor) {
                    final int hgrav = lp.gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int vgrav = lp.gravity & Gravity.VERTICAL_GRAVITY_MASK;
                    switch (hgrav) {
                        default:
                            childLeft = paddingLeft;
                            break;
                        case Gravity.LEFT://DecorView往最左边靠
                            childLeft = paddingLeft;
                            paddingLeft += child.getMeasuredWidth();
                            break;
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = Math.max((width - child.getMeasuredWidth()) / 2,
                                    paddingLeft);
                            break;
                        case Gravity.RIGHT:
                            childLeft = width - paddingRight - child.getMeasuredWidth();
                            paddingRight += child.getMeasuredWidth();
                            break;
                    }
                    switch (vgrav) {
                        default:
                            childTop = paddingTop;
                            break;
                        case Gravity.TOP:
                            childTop = paddingTop;
                            paddingTop += child.getMeasuredHeight();
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = Math.max((height - child.getMeasuredHeight()) / 2,
                                    paddingTop);
                            break;
                        case Gravity.BOTTOM:
                            childTop = height - paddingBottom - child.getMeasuredHeight();
                            paddingBottom += child.getMeasuredHeight();
                            break;
                    }
                    childLeft += scrollX;
                    child.layout(childLeft, childTop,
                            childLeft + child.getMeasuredWidth(),
                            childTop + child.getMeasuredHeight());
                    decorCount++;
                }
            }
        }

        /*滑动方向是水平*/
        if (mOrientation == Orientation.HORIZONTAL) {
            final int childWidth = width - paddingLeft - paddingRight;
            // Page views. Do this once we have the right padding offsets from above.
            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() != GONE) {
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    ItemInfo ii;
                    //infoForChild 会调用Adapter的isViewFromObject
                    if (!lp.isDecor && (ii = infoForChild(child)) != null) {
                        //子页卡的左偏移量，第一个是loff是0，第二个是一个页面向左偏移一个页面的宽度，以此类推
                        int loff = (int) (childWidth * ii.offset);
                        int childLeft = paddingLeft + loff;
                        int childTop = paddingTop;
                        if (lp.needsMeasure) {
                            // This was added during layout and needs measurement.
                            // Do it now that we know what we're working with.
                            lp.needsMeasure = false;
                            final int widthSpec = MeasureSpec.makeMeasureSpec(
                                    (int) (childWidth * lp.widthFactor),
                                    MeasureSpec.EXACTLY);
                            final int heightSpec = MeasureSpec.makeMeasureSpec(
                                    (int) (height - paddingTop - paddingBottom),
                                    MeasureSpec.EXACTLY);
                            child.measure(widthSpec, heightSpec);
                        }
                        if (DEBUG) {
                            Log.v(TAG, "Positioning #" + i + " " + child + " f=" + ii.object
                                    + ":" + childLeft + "," + childTop + " " + child.getMeasuredWidth()
                                    + "x" + child.getMeasuredHeight());
                        }
                        child.layout(childLeft, childTop,
                                childLeft + child.getMeasuredWidth(),
                                childTop + child.getMeasuredHeight());
                    }
                }
            }

            //滑动方向是垂直
        } else {
            final int childHeight = height - paddingTop - paddingBottom;
            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() != GONE) {
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    ItemInfo ii;
                    //infoForChild 会调用Adapter的isViewFromObject
                    if (!lp.isDecor && (ii = infoForChild(child)) != null) {
                        //子页面的上偏移量，第一个是toff是0，第二个是一个页面向左偏移一个页面的宽度，以此类推
                        int toff = (int) (childHeight * ii.offset);
                        int childTop = paddingTop + toff;
                        int childLeft = paddingLeft;
                        if (lp.needsMeasure) {
                            // This was added during layout and needs measurement.
                            // Do it now that we know what we're working with.
                            lp.needsMeasure = false;

                            final int widthSpec = MeasureSpec.makeMeasureSpec(
                                    (int) (width - paddingLeft - paddingRight),
                                    MeasureSpec.EXACTLY);

                            final int heightSpec = MeasureSpec.makeMeasureSpec(
                                    (int) (childHeight * lp.widthFactor),
                                    MeasureSpec.EXACTLY);
                            child.measure(widthSpec, heightSpec);
                        }
                        if (DEBUG) {
                            Log.v(TAG, "Positioning #" + i + " " + child + " f=" + ii.object
                                    + ":" + childLeft + "," + childTop + " " + child.getMeasuredWidth()
                                    + "x" + child.getMeasuredHeight());
                        }
                        child.layout(childLeft, childTop,
                                childLeft + child.getMeasuredWidth(),
                                childTop + child.getMeasuredHeight());
                    }
                }
            }

        }

        mTopPageBounds = paddingTop;
        mBottomPageBounds = height - paddingBottom;
        mLeftPageBounds = paddingLeft;
        mRightPageBounds = width - paddingRight;

        mDecorChildCount = decorCount;

        //如果是第一次layout 先翻到第一页
        if (mFirstLayout) {
            scrollToItem(mCurItem, false, 0, false);
        }
        mFirstLayout = false;
    }

    @Override
    public void computeScroll() {
        //标记当前正在移动
        mIsScrollStarted = true;
        if (!mScroller.isFinished() && mScroller.computeScrollOffset()) {
            int oldX = getScrollX();
            int oldY = getScrollY();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();

            if (oldX != x || oldY != y) {
                scrollTo(x, y);
                if (mOrientation == Orientation.HORIZONTAL) {
                    //确保mScroller还没结束滑动，并开始计算滑动位置
                    if (!pageScrolled(x)) {
                        //如果没有子页面中断动画并且滑动到x=0位置，但是y保持滑动
                        mScroller.abortAnimation();
                        scrollTo(0, y);
                    }
                } else {
                    if (!pageScrolled(y)) {
                        mScroller.abortAnimation();
                        scrollTo(x, 0);
                    }
                }

            }

            // Keep on drawing until the animation has finished.
            //保持绘制，并且是在上一帧动画完成才重绘
            ViewCompat.postInvalidateOnAnimation(this);
            return;
        }

        // Done with scroll, clean up state.
        completeScroll(true);
    }

    private boolean pageScrolled(int xypos) {
        if (mItems.size() == 0) {
            if (mFirstLayout) {
                // If we haven't been laid out yet, we probably just haven't been populated yet.
                // Let's skip this call since it doesn't make sense in this state
                return false;
            }
            mCalledSuper = false;
            onPageScrolled(0, 0, 0);
            if (!mCalledSuper) {
                throw new IllegalStateException(
                        "onPageScrolled did not call superclass implementation");
            }
            return false;
        }
        final ItemInfo ii = infoForCurrentScrollPosition();
        /*************方向是水平********************/
        if (mOrientation == Orientation.HORIZONTAL) {
            // 获取显示区域的宽度
            final int width = getClientWidth();
            //加上外边距后的宽度
            final int widthWithMargin = width + mPageMargin;
            final float marginOffset = (float) mPageMargin / width;
            //当前是第几个页面
            final int currentPage = ii.position;
            //计算当前页面的偏移量 [0,1) 如果pageOffset不等于0，则下个页面可见
            final float pageOffset = (((float) xypos / width) - ii.offset)
                    / (ii.sizeFactor + marginOffset);
            final int offsetPixels = (int) (pageOffset * widthWithMargin);
            mCalledSuper = false;
            onPageScrolled(currentPage, pageOffset, offsetPixels);

        } else {
            /*************方向是垂直********************/
            // 获取显示区域的宽度
            final int height = getClientHeight();
            //加上外边距后的宽度
            final int heightWithMargin = height + mPageMargin;
            final float marginOffset = (float) mPageMargin / height;
            //当前是第几个页面
            final int currentPage = ii.position;
            //计算当前页面的偏移量 [0,1) 如果pageOffset不等于0，则下个页面可见
            final float pageOffset = (((float) xypos / height) - ii.offset)
                    / (ii.sizeFactor + marginOffset);
            final int offsetPixels = (int) (pageOffset * heightWithMargin);
            mCalledSuper = false;
            onPageScrolled(currentPage, pageOffset, offsetPixels);
        }


        if (!mCalledSuper) {
            throw new IllegalStateException(
                    "onPageScrolled did not call superclass implementation");
        }
        return true;
    }

    /**
     * This method will be invoked when the current page is scrolled, either as part
     * of a programmatically initiated smooth scroll or a user initiated touch scroll.
     * If you override this method you must call through to the superclass implementation
     * (e.g. super.onPageScrolled(position, offset, offsetPixels)) before onPageScrolled
     * returns.
     *
     * @param position     Position index of the first page currently being displayed.
     *                     Page position+1 will be visible if positionOffset is nonzero.
     * @param offset       Value from [0, 1) indicating the offset from the page at position.
     * @param offsetPixels Value in pixels indicating the offset from position.
     */
    @CallSuper
    protected void onPageScrolled(int position, float offset, int offsetPixels) {
        // Offset any decor views if needed - keep them on-screen at all times.

        /*************方向是水平********************/
        if (mOrientation == Orientation.HORIZONTAL) {
            //TODO 处理DecorView 原理不明白
            if (mDecorChildCount > 0) {
                final int scrollX = getScrollX();
                int paddingLeft = getPaddingLeft();
                int paddingRight = getPaddingRight();
                final int width = getWidth();
                final int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    final View child = getChildAt(i);
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    if (!lp.isDecor) continue;

                    final int hgrav = lp.gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    int childLeft = 0;
                    switch (hgrav) {
                        default:
                            childLeft = paddingLeft;
                            break;
                        case Gravity.LEFT:
                            childLeft = paddingLeft;
                            paddingLeft += child.getWidth();
                            break;
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = Math.max((width - child.getMeasuredWidth()) / 2,
                                    paddingLeft);
                            break;
                        case Gravity.RIGHT:
                            childLeft = width - paddingRight - child.getMeasuredWidth();
                            paddingRight += child.getMeasuredWidth();
                            break;
                    }
                    childLeft += scrollX;

                    final int childOffset = childLeft - child.getLeft();
                    if (childOffset != 0) {
                        child.offsetLeftAndRight(childOffset);
                    }
                }
            }

            dispatchOnPageScrolled(position, offset, offsetPixels);

            if (mPageTransformer != null) {
                final int scrollX = getScrollX();
                final int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    final View child = getChildAt(i);
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    if (lp.isDecor) continue;
                    final float transformPos = (float) (child.getLeft() - scrollX) / getClientWidth();
                    mPageTransformer.transformPage(child, transformPos);
                }
            }

        } else {
            /*************方向是垂直********************/
            if (mDecorChildCount > 0) {
                final int scrollY = getScrollY();
                int paddingTop = getPaddingTop();
                int paddingBottom = getPaddingBottom();
                final int height = getHeight();
                final int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    final View child = getChildAt(i);
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    if (!lp.isDecor) continue;

                    final int hgrav = lp.gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    int childTop = 0;
                    switch (hgrav) {
                        default:
                            childTop = paddingTop;
                            break;
                        case Gravity.LEFT:
                            childTop = paddingTop;
                            paddingTop += child.getHeight();
                            break;
                        case Gravity.CENTER_HORIZONTAL:
                            childTop = Math.max((height - child.getMeasuredWidth()) / 2,
                                    paddingTop);
                            break;
                        case Gravity.RIGHT:
                            childTop = height - paddingBottom - child.getMeasuredWidth();
                            paddingBottom += child.getMeasuredWidth();
                            break;
                    }
                    childTop += scrollY;

                    final int childOffset = childTop - child.getTop();
                    if (childOffset != 0) {
                        child.offsetLeftAndRight(childOffset);
                    }
                }
            }

            dispatchOnPageScrolled(position, offset, offsetPixels);

            if (mPageTransformer != null) {
                final int scrollY = getScrollY();
                final int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    final View child = getChildAt(i);
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    if (lp.isDecor) continue;
                    final float transformPos = (float) (child.getTop() - scrollY) / getClientHeight();
                    mPageTransformer.transformPage(child, transformPos);
                }
            }
        }


        mCalledSuper = true;
    }

    private void dispatchOnPageScrolled(int position, float offset, int offsetPixels) {
        if (mOnPageChangeListener != null) {
            mOnPageChangeListener.onPageScrolled(position, offset, offsetPixels);
        }
        if (mOnPageChangeListeners != null) {
            for (int i = 0, z = mOnPageChangeListeners.size(); i < z; i++) {
                OnPageChangeListener listener = mOnPageChangeListeners.get(i);
                if (listener != null) {
                    listener.onPageScrolled(position, offset, offsetPixels);
                }
            }
        }
        if (mInternalPageChangeListener != null) {
            mInternalPageChangeListener.onPageScrolled(position, offset, offsetPixels);
        }
    }

    private void dispatchOnPageSelected(int position) {
        if (mOnPageChangeListener != null) {
            mOnPageChangeListener.onPageSelected(position);
        }
        if (mOnPageChangeListeners != null) {
            for (int i = 0, z = mOnPageChangeListeners.size(); i < z; i++) {
                OnPageChangeListener listener = mOnPageChangeListeners.get(i);
                if (listener != null) {
                    listener.onPageSelected(position);
                }
            }
        }
        if (mInternalPageChangeListener != null) {
            mInternalPageChangeListener.onPageSelected(position);
        }
    }

    private void dispatchOnScrollStateChanged(int state) {
        if (mOnPageChangeListener != null) {
            mOnPageChangeListener.onPageScrollStateChanged(state);
        }
        if (mOnPageChangeListeners != null) {
            for (int i = 0, z = mOnPageChangeListeners.size(); i < z; i++) {
                OnPageChangeListener listener = mOnPageChangeListeners.get(i);
                if (listener != null) {
                    listener.onPageScrollStateChanged(state);
                }
            }
        }
        if (mInternalPageChangeListener != null) {
            mInternalPageChangeListener.onPageScrollStateChanged(state);
        }
    }

    private void completeScroll(boolean postEvents) {
        boolean needPopulate = mScrollState == SCROLL_STATE_SETTLING;
        if (needPopulate) {
            // Done with scroll, no longer want to cache view drawing.
            setScrollingCacheEnabled(false);
            boolean wasScrolling = !mScroller.isFinished();
            if (wasScrolling) {
                mScroller.abortAnimation();
                int oldX = getScrollX();
                int oldY = getScrollY();
                int x = mScroller.getCurrX();
                int y = mScroller.getCurrY();
                if (oldX != x || oldY != y) {
                    scrollTo(x, y);
                    /*************方向是水平********************/
                    if (mOrientation == Orientation.HORIZONTAL) {
                        if (x != oldX) {
                            pageScrolled(x);
                        }
                    } else {
                        /*************方向是垂直********************/
                        if (y != oldY) {
                            pageScrolled(y);
                        }

                    }

                }
            }
        }
        mPopulatePending = false;
        for (int i = 0; i < mItems.size(); i++) {
            ItemInfo ii = mItems.get(i);
            if (ii.scrolling) {
                needPopulate = true;
                ii.scrolling = false;
            }
        }
        if (needPopulate) {
            if (postEvents) {
                ViewCompat.postOnAnimation(this, mEndScrollRunnable);
            } else {
                mEndScrollRunnable.run();
            }
        }
    }

    //手指是否在页面的缝隙滑动(dx<0向左，dx>0向右)
    private boolean isGutterDrag(float xy, float dxy) {
        /*************方向是水平********************/
        if (mOrientation == Orientation.HORIZONTAL) {
            return (xy < mGutterSize && dxy > 0) || (xy > getWidth() - mGutterSize && dxy < 0);
        } else {
            /*************方向是垂直********************/
            return (xy < mGutterSize && dxy > 0) || (xy > getHeight() - mGutterSize && dxy < 0);

        }

    }

    private void enableLayers(boolean enable) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final int layerType = enable
                    ? mPageTransformerLayerType : View.LAYER_TYPE_NONE;
            getChildAt(i).setLayerType(layerType, null);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {

        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onMotionEvent will be called and we do the actual
         * scrolling there.
         */

        final int action = ev.getAction() & MotionEvent.ACTION_MASK;

        // Always take care of the touch gesture being complete.
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            // Release the drag.
            if (DEBUG) Log.v(TAG, "Intercept done!");
            resetTouch();
            return false;
        }

        // Nothing more to do here if we have decided whether or not we
        // are dragging.
        //不是按下
        if (action != MotionEvent.ACTION_DOWN) {
            //如果在拖拽页面就拦截
            if (mIsBeingDragged) {
                if (DEBUG) Log.v(TAG, "Intercept returning true!");
                return true;
            }
            //如果不允许拖拽页面就  放过一切触摸事件
            if (mIsUnableToDrag) {
                if (DEBUG) Log.v(TAG, "Intercept returning false!");
                return false;
            }
        }


        /*************方向是水平********************/
        if (mOrientation == Orientation.HORIZONTAL) {

            //如果是手指移动，准备要开始拖拽页面了,TODO 调试的时候发现并没有进入到这里。。。
            switch (action) {
                case MotionEvent.ACTION_MOVE: {
                    /*
                     * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                     * whether the user has moved far enough from his original down touch.
                     */

                    /*
                     * Locally do absolute value. mLastMotionY is set to the y value
                     * of the down event.
                     */
                    final int activePointerId = mActivePointerId; //拿到触摸点Id，在ACTION_DOWN生成，这个跟多点触摸有关系
                    if (activePointerId == INVALID_POINTER) {
                        // If we don't have a valid id, the touch down wasn't on content.
                        break;
                    }

                    //根据触摸点的id来区分不同的手指，仅仅需要关注一个手指
                    final int pointerIndex = ev.findPointerIndex(activePointerId);
                    final float x = ev.getX(pointerIndex);
                    final float dx = x - mLastMotionX;
                    final float xDiff = Math.abs(dx);

                    final float y = ev.getY(pointerIndex);
                    final float yDiff = Math.abs(y - mInitialMotionY);
                    if (DEBUG)
                        Log.v(TAG, "Moved x to " + x + "," + y + " diff=" + xDiff + "," + yDiff);

                    //手指不在页面之间的边缘且页面可以滑动
                    if (dx != 0 && !isGutterDrag(mLastMotionX, dx)
                            && canScroll(this, false, (int) dx, (int) x, (int) y)) {
                        // Nested view has scrollable area under this point. Let it be handled there.
                        //更新x,y的移动坐标
                        mLastMotionX = x;
                        mLastMotionY = y;
                        //标记现在不可以再拖拽页面了，防止另一个手指按下（ACTION_DOWN）而被影响到
                        mIsUnableToDrag = true;
                        return false;
                    }
                    //x移动的距离大于最小距离，且斜率小于0.5，则认为是水平方向上的移动
                    if (xDiff > mTouchSlop && xDiff * 0.5f > yDiff) {
                        if (DEBUG) Log.v(TAG, "Starting drag!");
                        mIsBeingDragged = true;
                        requestParentDisallowInterceptTouchEvent(true);
                        setScrollState(SCROLL_STATE_DRAGGING);
                        //TODO 为什么是这个值不知
                        mLastMotionX = dx > 0
                                ? mInitialMotionX + mTouchSlop : mInitialMotionX - mTouchSlop;
                        mLastMotionY = y;
                        setScrollingCacheEnabled(true);
                    } else if (yDiff > mTouchSlop) {
                        // The finger has moved enough in the vertical
                        // direction to be counted as a drag...  abort
                        // any attempt to drag horizontally, to work correctly
                        // with children that have scrolling containers.
                        if (DEBUG) Log.v(TAG, "Starting unable to drag!");
                        mIsUnableToDrag = true;
                    }
                    if (mIsBeingDragged) {
                        // Scroll to follow the motion event
                        if (performDrag(x)) {
                            ViewCompat.postInvalidateOnAnimation(this);
                        }
                    }
                    break;
                }

                case MotionEvent.ACTION_DOWN: {
                    /*
                     * Remember location of down touch.
                     * ACTION_DOWN always refers to pointer index 0.
                     */
                    //记录按下的点位置
                    mLastMotionX = mInitialMotionX = ev.getX();
                    mLastMotionY = mInitialMotionY = ev.getY();
                    //记录按下的手指id,索引到的总是第一个触碰到的手指也就是0
                    mActivePointerId = ev.getPointerId(0);
                    //重置可以拖拽切换页面
                    mIsUnableToDrag = false;
                    //标记开始滚动
                    mIsScrollStarted = true;
                    //手动调用计算滑动的偏移量,如果目前滑动已经结束了，会直接返回，不会计算
                    mScroller.computeScrollOffset();

                    //如果此时按下，且页面正在放到最终位置
                    //且当前位置距离最终位置足够远
                    if (mScrollState == SCROLL_STATE_SETTLING
                            && Math.abs(mScroller.getFinalX() - mScroller.getCurrX()) > mCloseEnough) {
                        // Let the user 'catch' the pager as it animates.

                        // 需要停止当前的滑动动画，然后暂停滑动
                        mScroller.abortAnimation();
                        mPopulatePending = false;
                        populate();
                        //状态改成正在拖拽
                        mIsBeingDragged = true;
                        //屏蔽父View的触摸拦截，总是会把事件下发到这里
                        requestParentDisallowInterceptTouchEvent(true);
                        //设置滑动状态为滑动中
                        setScrollState(SCROLL_STATE_DRAGGING);
                    } else {
                        //当前按下无论如何需要先停止滑动
                        //结束滚动
                        completeScroll(false);
                        mIsBeingDragged = false;
                    }

                    if (DEBUG) {
                        Log.v(TAG, "Down at " + mLastMotionX + "," + mLastMotionY
                                + " mIsBeingDragged=" + mIsBeingDragged
                                + "mIsUnableToDrag=" + mIsUnableToDrag);
                    }
                    break;
                }

                //TODO 调试的时候发现并没有进入到这里。。。
                case MotionEvent.ACTION_POINTER_UP:
                    onSecondaryPointerUp(ev);
                    break;
            }

        } else {
            /*************方向是垂直********************/
            switch (action) {

                //如果是手指移动，准备要开始拖拽页面了,TODO 调试的时候发现并没有进入到这里。。。
                case MotionEvent.ACTION_MOVE: {
                    /*
                     * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                     * whether the user has moved far enough from his original down touch.
                     */

                    /*
                     * Locally do absolute value. mLastMotionY is set to the y value
                     * of the down event.
                     */
                    final int activePointerId = mActivePointerId; //拿到触摸点Id，在ACTION_DOWN生成，这个跟多点触摸有关系
                    if (activePointerId == INVALID_POINTER) {
                        // If we don't have a valid id, the touch down wasn't on content.
                        break;
                    }

                    //根据触摸点的id来区分不同的手指，仅仅需要关注一个手指
                    final int pointerIndex = ev.findPointerIndex(activePointerId);
                    final float x = ev.getX(pointerIndex);
                    final float dx = x - mInitialMotionX;
                    final float xDiff = Math.abs(dx);

                    final float y = ev.getY(pointerIndex);
                    final float dy = y - mLastMotionY;
                    final float yDiff = Math.abs(y - mLastMotionY);
                    if (DEBUG)
                        Log.v(TAG, "Moved x to " + x + "," + y + " diff=" + xDiff + "," + yDiff);

                    //手指不在页面之间的边缘且页面可以滑动
                    if (dy != 0 && !isGutterDrag(mLastMotionY, dy)
                            && canScroll(this, false, (int) dy, (int) x, (int) y)) {
                        // Nested view has scrollable area under this point. Let it be handled there.
                        //更新x,y的移动坐标
                        mLastMotionX = x;
                        mLastMotionY = y;
                        //标记现在不可以再拖拽页面了，防止另一个手指按下（ACTION_DOWN）而被影响到
                        mIsUnableToDrag = true;
                        return false;
                    }
                    //y移动的距离大于最小距离，且斜率大于0.5，则认为是垂直方向上的移动
                    if (yDiff > mTouchSlop && xDiff * 0.5f < yDiff) {
                        if (DEBUG) Log.v(TAG, "Starting drag!");
                        mIsBeingDragged = true;
                        requestParentDisallowInterceptTouchEvent(true);
                        setScrollState(SCROLL_STATE_DRAGGING);
                        //保存当前的移动位置
                        mLastMotionX = x;
                        mLastMotionY = dy > 0 ? mInitialMotionY + mTouchSlop : mInitialMotionY - mTouchSlop;
                        setScrollingCacheEnabled(true);
                    } else if (xDiff > mTouchSlop) {
                        // The finger has moved enough in the vertical
                        // direction to be counted as a drag...  abort
                        // any attempt to drag horizontally, to work correctly
                        // with children that have scrolling containers.
                        if (DEBUG) Log.v(TAG, "Starting unable to drag!");
                        //提前结束Move 动作，后面的move动作都屏蔽
                        mIsUnableToDrag = true;
                    }
                    if (mIsBeingDragged) {
                        // Scroll to follow the motion event
                        if (performDrag(y)) {
                            ViewCompat.postInvalidateOnAnimation(this);
                        }
                    }
                    break;
                }

                case MotionEvent.ACTION_DOWN: {
                    /*
                     * Remember location of down touch.
                     * ACTION_DOWN always refers to pointer index 0.
                     */
                    //记录按下的点位置
                    mLastMotionX = mInitialMotionX = ev.getX();
                    mLastMotionY = mInitialMotionY = ev.getY();
                    //记录按下的手指id,索引到的总是第一个触碰到的手指也就是0
                    mActivePointerId = ev.getPointerId(0);
                    //重置可以拖拽切换页面
                    mIsUnableToDrag = false;
                    //标记开始滚动
                    mIsScrollStarted = true;
                    //手动调用计算滑动的偏移量,如果目前滑动已经结束了，会直接返回，不会计算
                    mScroller.computeScrollOffset();

                    //如果此时按下，且页面正在放到最终位置
                    //且当前位置距离最终位置足够远
                    if (mScrollState == SCROLL_STATE_SETTLING
                            && Math.abs(mScroller.getFinalY() - mScroller.getCurrY()) > mCloseEnough) {
                        // Let the user 'catch' the pager as it animates.

                        // 需要停止当前的滑动动画，然后暂停滑动
                        mScroller.abortAnimation();
                        mPopulatePending = false;
                        populate();
                        //状态改成正在拖拽
                        mIsBeingDragged = true;
                        //屏蔽父View的触摸拦截，总是会把事件下发到这里
                        requestParentDisallowInterceptTouchEvent(true);
                        //设置滑动状态为滑动中
                        setScrollState(SCROLL_STATE_DRAGGING);
                    } else {
                        //当前按下无论如何需要先停止滑动
                        //结束滚动
                        completeScroll(false);
                        mIsBeingDragged = false;
                    }

                    if (DEBUG) {
                        Log.v(TAG, "Down at " + mLastMotionX + "," + mLastMotionY
                                + " mIsBeingDragged=" + mIsBeingDragged
                                + "mIsUnableToDrag=" + mIsUnableToDrag);
                    }
                    break;
                }

                //TODO 调试的时候发现并没有进入到这里。。。
                case MotionEvent.ACTION_POINTER_UP:
                    onSecondaryPointerUp(ev);
                    break;
            }

        }


        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        /*
         * The only time we want to intercept motion events is if we are in the
         * drag mode.
         */
        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        //如果此时正在模拟一个假的拖动，触摸事件就消费掉
        if (mFakeDragging) {
            // A fake drag is in progress already, ignore this real one
            // but still eat the touch events.
            // (It is likely that the user is multi-touching the screen.)
            return true;
        }

        //如果按钮并且是正在触摸屏幕的边缘，就放掉这个触摸事件
        if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getEdgeFlags() != 0) {
            // ev.getEdgeFlags() 返回的如果是非零，则代表上下左右的flag（int）值，如果返回0就代表还没有触摸到屏幕边缘
            // 测试时这个返回值总是0，似乎这段判断意义不大
            // Don't handle edge touches immediately -- they may actually belong to one of our
            // descendants.
            return false;
        }

        //没上适配器或者里面没东西就放过这个触摸事件
        if (mAdapter == null || mAdapter.getCount() == 0) {
            // Nothing to present or scroll; nothing to touch.
            return false;
        }

        //滑动速度追踪器是空的就创建一个
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction();
        //是否需要重绘
        boolean needsInvalidate = false;

        switch (action & MotionEvent.ACTION_MASK) {
            //按下
            case MotionEvent.ACTION_DOWN: {
                //停止滑动动画
                mScroller.abortAnimation();
                //重新开始populate()
                mPopulatePending = false;
                populate();

                // Remember where the motion event started
                //重新定位点击的位置，和活动的手指坐标Id
                mLastMotionX = mInitialMotionX = ev.getX();
                mLastMotionY = mInitialMotionY = ev.getY();
                mActivePointerId = ev.getPointerId(0);
                break;
            }
            //已经按下并滑动
            case MotionEvent.ACTION_MOVE:

                /*************方向是水平********************/
                if (mOrientation == Orientation.HORIZONTAL) {
                    //当前没有正在拖动
                    if (!mIsBeingDragged) {
                        final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                        //找不到这个手指索引（有其他的子视图可能已经消费了这个事件）
                        if (pointerIndex == -1) {
                            // A child has consumed some touch events and put us into an inconsistent
                            // state.
                            //重置一些与滑动相关的参数，如果是滑动到边缘就释放边界动画，并且需要再绘制
                            needsInvalidate = resetTouch();
                            break;
                        }
                        final float x = ev.getX(pointerIndex);
                        final float xDiff = Math.abs(x - mLastMotionX);
                        final float y = ev.getY(pointerIndex);
                        final float yDiff = Math.abs(y - mLastMotionY);
                        if (DEBUG) {
                            Log.v(TAG, "Moved x to " + x + "," + y + " diff=" + xDiff + "," + yDiff);
                        }

                        //如果滑动的斜率小于1
                        if (xDiff > mTouchSlop && xDiff > yDiff) {
                            if (DEBUG) Log.v(TAG, "Starting drag!");
                            mIsBeingDragged = true;
                            //开始滑动了
                            requestParentDisallowInterceptTouchEvent(true);
                            //最新x坐标设置为初始值加减mTouchSlop这个裕量，保证达到滑动的条件
                            mLastMotionX = x - mInitialMotionX > 0 ? mInitialMotionX + mTouchSlop :
                                    mInitialMotionX - mTouchSlop;
                            mLastMotionY = y;
                            //设置滑动的状态为：正在拖拽
                            setScrollState(SCROLL_STATE_DRAGGING);
                            //打开draw的缓存，这个一直是关闭的
                            setScrollingCacheEnabled(true);

                            // Disallow Parent Intercept, just in case
                            //关闭父布局的触摸拦截
                            ViewParent parent = getParent();
                            if (parent != null) {
                                parent.requestDisallowInterceptTouchEvent(true);
                            }
                        }
                    }
                    // Not else! Note that mIsBeingDragged can be set above.
                    // 当前正在滑动，执行滑动
                    if (mIsBeingDragged) {
                        // Scroll to follow the motion event
                        final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                        final float x = ev.getX(activePointerIndex);
                        needsInvalidate |= performDrag(x);
                    }
                } else {
                    /*************方向是垂直********************/

                    //当前没有正在拖动
                    if (!mIsBeingDragged) {
                        final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                        //找不到这个手指索引（有其他的子视图可能已经消费了这个事件）
                        if (pointerIndex == -1) {
                            // A child has consumed some touch events and put us into an inconsistent
                            // state.
                            //重置一些与滑动相关的参数，如果是滑动到边缘就释放边界动画，并且需要再绘制
                            needsInvalidate = resetTouch();
                            break;
                        }
                        final float x = ev.getX(pointerIndex);
                        final float xDiff = Math.abs(x - mLastMotionX);
                        final float y = ev.getY(pointerIndex);
                        final float yDiff = Math.abs(y - mLastMotionY);
                        if (DEBUG) {
                            Log.v(TAG, "Moved x to " + x + "," + y + " diff=" + xDiff + "," + yDiff);
                        }

                        //如果滑动的斜率小于1
                        if (yDiff > mTouchSlop && xDiff < yDiff) {
                            if (DEBUG) Log.v(TAG, "Starting drag!");
                            mIsBeingDragged = true;
                            //开始滑动了
                            requestParentDisallowInterceptTouchEvent(true);
                            //最新x坐标设置为初始值加减mTouchSlop这个裕量，保证达到滑动的条件
                            mLastMotionX = x;
                            mLastMotionY = y - mInitialMotionY > 0 ? mInitialMotionY + mTouchSlop :
                                    mInitialMotionY - mTouchSlop;
                            //设置滑动的状态为：正在拖拽
                            setScrollState(SCROLL_STATE_DRAGGING);
                            //打开draw的缓存，这个一直是关闭的
                            setScrollingCacheEnabled(true);

                            // Disallow Parent Intercept, just in case
                            //关闭父布局的触摸拦截
                            ViewParent parent = getParent();
                            if (parent != null) {
                                parent.requestDisallowInterceptTouchEvent(true);
                            }
                        }
                    }
                    // Not else! Note that mIsBeingDragged can be set above.
                    // 当前正在滑动，执行滑动
                    if (mIsBeingDragged) {
                        // Scroll to follow the motion event
                        final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                        final float y = ev.getY(activePointerIndex);
                        needsInvalidate |= performDrag(y);
                    }

                }


                break;

            //手指抬起
            case MotionEvent.ACTION_UP:
                //必须是正在拖动的状态
                if (mIsBeingDragged) {
                    //手指速度
                    final VelocityTracker velocityTracker = mVelocityTracker;
                    //计算手指的速度，这样可以拿到速度的x或y值
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    //拿到x的速度
                    int initialVelocity = (int) velocityTracker.getXVelocity(mActivePointerId);
                    mPopulatePending = true;

                    /*************方向是水平********************/
                    if (mOrientation == Orientation.HORIZONTAL) {
                        // 获得当前视图的实际宽度和滑动到x的终点值
                        final int width = getClientWidth();
                        final int scrollX = getScrollX();
                        //拿到当前的ItemInfo
                        final ItemInfo ii = infoForCurrentScrollPosition();
                        final float marginOffset = (float) mPageMargin / width;
                        final int currentPage = ii.position;
                        final float pageOffset = (((float) scrollX / width) - ii.offset)
                                / (ii.sizeFactor + marginOffset);
                        final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                        final float x = ev.getX(activePointerIndex);
                        final int totalDelta = (int) (x - mInitialMotionX);
                        //算出下一个页面应该是哪个？
                        int nextPage = determineTargetPage(currentPage, pageOffset, initialVelocity,
                                totalDelta);
                        //设置到当前页面
                        setCurrentItemInternal(nextPage, true, true, initialVelocity);
                    } else {
                        /*************方向是垂直********************/

                        // 获得当前视图的实际宽度和滑动到y的终点值
                        final int height = getClientHeight();
                        final int scrollY = getScrollY();
                        //拿到当前的ItemInfo
                        final ItemInfo ii = infoForCurrentScrollPosition();
                        final float marginOffset = (float) mPageMargin / height;
                        final int currentPage = ii.position;
                        final float pageOffset = (((float) scrollY / height) - ii.offset)
                                / (ii.sizeFactor + marginOffset);
                        final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                        final float y = ev.getY(activePointerIndex);
                        final int totalDelta = (int) (y - mInitialMotionY);
                        //算出下一个页面应该是哪个？
                        int nextPage = determineTargetPage(currentPage, pageOffset, initialVelocity,
                                totalDelta);
                        //设置到当前页面
                        setCurrentItemInternal(nextPage, true, true, initialVelocity);
                    }


                    needsInvalidate = resetTouch();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsBeingDragged) {
                    scrollToItem(mCurItem, true, 0, false);
                    needsInvalidate = resetTouch();
                }
                break;
            //手指按下，记录坐标和当前活动的手指Id
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = ev.getActionIndex();
                /*************方向是水平********************/
                if (mOrientation == Orientation.HORIZONTAL) {
                    final float x = ev.getX(index);
                    mLastMotionX = x;
                    mActivePointerId = ev.getPointerId(index);
                } else {
                    /*************方向是垂直********************/
                    final float y = ev.getY(index);
                    mLastMotionY = y;
                    mActivePointerId = ev.getPointerId(index);
                }

                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);

                /*************方向是水平********************/
                if (mOrientation == Orientation.HORIZONTAL) {
                    mLastMotionX = ev.getX(ev.findPointerIndex(mActivePointerId));
                } else {
                    /*************方向是垂直********************/
                    mLastMotionY = ev.getY(ev.findPointerIndex(mActivePointerId));
                }
                break;
        }
        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
        return true;
    }

    private boolean resetTouch() {
        boolean needsInvalidate;
        mActivePointerId = INVALID_POINTER;
        endDrag();
        mLeftEdge.onRelease();
        mRightEdge.onRelease();
        needsInvalidate = mLeftEdge.isFinished() || mRightEdge.isFinished();
        return needsInvalidate;
    }

    private void requestParentDisallowInterceptTouchEvent(boolean disallowIntercept) {
        final ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    private boolean performDrag(float xy) {
        boolean needsInvalidate = false;

        /*************方向是水平********************/
        if (mOrientation == Orientation.HORIZONTAL) {
            final float deltaX = mLastMotionX - xy;  //拿到拖动的偏移量
            mLastMotionX = xy;                       //更新最新的x坐标

            //将旧的滑动目标X加上偏移，就是新的滑动目标
            float oldScrollX = getScrollX();
            float scrollX = oldScrollX + deltaX;
            final int width = getClientWidth();

            float leftBound = width * mFirstOffset; //页面卷左测距离原点的距离
            float rightBound = width * mLastOffset; //页面卷右侧距离原点的距离
            boolean leftAbsolute = true;            //是否到达左侧边界
            boolean rightAbsolute = true;           //是否到达右侧边界

            //拿到页卷的首个和最后一个
            final ItemInfo firstItem = mItems.get(0);
            final ItemInfo lastItem = mItems.get(mItems.size() - 1);
            //如果页卷还没有到达左边界，那需要算出页卷的左侧距离原点的距离；
            if (firstItem.position != 0) {
                leftAbsolute = false;
                leftBound = firstItem.offset * width;
            }
            //页卷右侧同理
            if (lastItem.position != mAdapter.getCount() - 1) {
                rightAbsolute = false;
                rightBound = lastItem.offset * width;
            }

            //如果滑动到达边界，那需要执行边界禁止滑动效果
            if (scrollX < leftBound) {
                if (leftAbsolute) {
                    float over = leftBound - scrollX;
                    mLeftEdge.onPull(Math.abs(over) / width);
                    needsInvalidate = true;
                }
                scrollX = leftBound;
            } else if (scrollX > rightBound) {
                if (rightAbsolute) {
                    float over = scrollX - rightBound;
                    mRightEdge.onPull(Math.abs(over) / width);
                    needsInvalidate = true;
                }
                scrollX = rightBound;
            }
            // Don't lose the rounded component
            //把scrollX小数加到mLastMotionX 不清楚这么做的意义
            mLastMotionX += scrollX - (int) scrollX;
            //执行滑动
            scrollTo((int) scrollX, getScrollY());
            pageScrolled((int) scrollX);
        } else {
            /*************方向是垂直********************/
            final float deltaY = mLastMotionY - xy;  //拿到拖动的偏移量
            mLastMotionY = xy;                       //更新最新的x坐标

            //将旧的滑动目标Y加上偏移，就是新的滑动目标
            float oldScrollY = getScrollY();
            float scrollY = oldScrollY + deltaY;
            final int height = getClientHeight();

            float topBound = height * mFirstOffset; //页面卷上测距离原点的距离
            float bottomBound = height * mLastOffset; //页面卷下侧距离原点的距离
            boolean topAbsolute = true;            //是否到达上侧边界
            boolean bottomAbsolute = true;           //是否到达下侧边界

            //拿到页卷的首个和最后一个
            final ItemInfo firstItem = mItems.get(0);
            final ItemInfo lastItem = mItems.get(mItems.size() - 1);
            //如果页卷还没有到达左边界，那需要算出页卷的左侧距离原点的距离；
            if (firstItem.position != 0) {
                topAbsolute = false;
                topBound = firstItem.offset * height;
            }
            //页卷右侧同理
            if (lastItem.position != mAdapter.getCount() - 1) {
                bottomAbsolute = false;
                bottomBound = lastItem.offset * height;
            }

            //如果滑动到达边界，那需要执行边界禁止滑动效果
            if (scrollY < topBound) {
                if (topAbsolute) {
                    float over = topBound - scrollY;
                    mLeftEdge.onPull(Math.abs(over) / height);
                    needsInvalidate = true;
                }
                scrollY = topBound;
            } else if (scrollY > bottomBound) {
                if (bottomAbsolute) {
                    float over = scrollY - bottomBound;
                    mRightEdge.onPull(Math.abs(over) / height);
                    needsInvalidate = true;
                }
                scrollY = bottomBound;
            }
            // Don't lose the rounded component
            //把scrollX小数加到mLastMotionX 不清楚这么做的意义
            mLastMotionY += scrollY - (int) scrollY;
            //执行滑动
            scrollTo(getScrollX(), (int) scrollY);
            pageScrolled((int) scrollY);
        }

        return needsInvalidate;
    }

    /**
     * @return Info about the page at the current scroll position.
     * This can be synthetic for a missing middle page; the 'object' field can be null.
     */
    private ItemInfo infoForCurrentScrollPosition() {

        /*************方向是水平********************/
        if (mOrientation == Orientation.HORIZONTAL) {
            final int width = getClientWidth();
            final float scrollOffset = width > 0 ? (float) getScrollX() / width : 0;
            final float marginOffset = width > 0 ? (float) mPageMargin / width : 0;
            int lastPos = -1;
            float lastOffset = 0.f;
            float lastWidth = 0.f;
            boolean first = true;

            ItemInfo lastItem = null;
            for (int i = 0; i < mItems.size(); i++) {
                ItemInfo ii = mItems.get(i);
                float offset;
                if (!first && ii.position != lastPos + 1) {
                    // Create a synthetic item for a missing page.
                    ii = mTempItem;
                    ii.offset = lastOffset + lastWidth + marginOffset;
                    ii.position = lastPos + 1;
                    ii.sizeFactor = mAdapter.getPageWidth(ii.position);
                    i--;
                }
                offset = ii.offset;

                final float leftBound = offset;
                final float rightBound = offset + ii.sizeFactor + marginOffset;
                if (first || scrollOffset >= leftBound) {
                    if (scrollOffset < rightBound || i == mItems.size() - 1) {
                        return ii;
                    }
                } else {
                    return lastItem;
                }
                first = false;
                lastPos = ii.position;
                lastOffset = offset;
                lastWidth = ii.sizeFactor;
                lastItem = ii;
            }

            return lastItem;

        } else {
            /*************方向是垂直********************/

            final int height = getClientHeight();
            final float scrollOffset = height > 0 ? (float) getScrollY() / height : 0;
            final float marginOffset = height > 0 ? (float) mPageMargin / height : 0;
            int lastPos = -1;
            float lastOffset = 0.f;
            float lastHeight = 0.f;
            boolean first = true;

            ItemInfo lastItem = null;
            for (int i = 0; i < mItems.size(); i++) {
                ItemInfo ii = mItems.get(i);
                float offset;
                if (!first && ii.position != lastPos + 1) {
                    // Create a synthetic item for a missing page.
                    ii = mTempItem;
                    ii.offset = lastOffset + lastHeight + marginOffset;
                    ii.position = lastPos + 1;
                    ii.sizeFactor = mAdapter.getPageWidth(ii.position);
                    i--;
                }
                offset = ii.offset;

                final float topBound = offset;
                final float bottomBound = offset + ii.sizeFactor + marginOffset;
                if (first || scrollOffset >= topBound) {
                    if (scrollOffset < bottomBound || i == mItems.size() - 1) {
                        return ii;
                    }
                } else {
                    return lastItem;
                }
                first = false;
                lastPos = ii.position;
                lastOffset = offset;
                lastHeight = ii.sizeFactor;
                lastItem = ii;
            }

            return lastItem;

        }


    }

    //推算滑动后的页面是哪一个
    private int determineTargetPage(int currentPage, float pageOffset, int velocity,
                                    int deltaXY) {
        int targetPage;
        if (Math.abs(deltaXY) > mFlingDistance && Math.abs(velocity) > mMinimumVelocity) {
            targetPage = velocity > 0 ? currentPage : currentPage + 1;
        } else {
            final float truncator = currentPage >= mCurItem ? 0.4f : 0.6f;
            targetPage = currentPage + (int) (pageOffset + truncator);
        }

        if (mItems.size() > 0) {
            final ItemInfo firstItem = mItems.get(0);
            final ItemInfo lastItem = mItems.get(mItems.size() - 1);

            // Only let the user target pages we have items for
            targetPage = Math.max(firstItem.position, Math.min(targetPage, lastItem.position));
        }

        return targetPage;
    }

    //主要来处理边界效果
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        boolean needsInvalidate = false;

        final int overScrollMode = getOverScrollMode();
        if (overScrollMode == View.OVER_SCROLL_ALWAYS
                || (overScrollMode == View.OVER_SCROLL_IF_CONTENT_SCROLLS
                && mAdapter != null && mAdapter.getCount() > 1)) {
            if (!mLeftEdge.isFinished()) {
                final int restoreCount = canvas.save();
                final int height = getHeight() - getPaddingTop() - getPaddingBottom();
                final int width = getWidth();

                canvas.rotate(270);
                canvas.translate(-height + getPaddingTop(), mFirstOffset * width);
                mLeftEdge.setSize(height, width);
                needsInvalidate |= mLeftEdge.draw(canvas);
                canvas.restoreToCount(restoreCount);
            }
            if (!mRightEdge.isFinished()) {
                final int restoreCount = canvas.save();
                final int width = getWidth();
                final int height = getHeight() - getPaddingTop() - getPaddingBottom();

                canvas.rotate(90);
                canvas.translate(-getPaddingTop(), -(mLastOffset + 1) * width);
                mRightEdge.setSize(height, width);
                needsInvalidate |= mRightEdge.draw(canvas);
                canvas.restoreToCount(restoreCount);
            }
        } else {
            mLeftEdge.finish();
            mRightEdge.finish();
        }

        if (needsInvalidate) {
            // Keep animating
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        /*************方向是水平********************/
        if (mOrientation == Orientation.HORIZONTAL) {

            // Draw the margin drawable between pages if needed.
            if (mPageMargin > 0 && mMarginDrawable != null && mItems.size() > 0 && mAdapter != null) {
                final int scrollX = getScrollX();
                final int width = getWidth();

                final float marginOffset = (float) mPageMargin / width;
                int itemIndex = 0;
                ItemInfo ii = mItems.get(0);
                float offset = ii.offset;
                final int itemCount = mItems.size();
                final int firstPos = ii.position;
                final int lastPos = mItems.get(itemCount - 1).position;
                for (int pos = firstPos; pos < lastPos; pos++) {
                    while (pos > ii.position && itemIndex < itemCount) {
                        ii = mItems.get(++itemIndex);
                    }

                    float drawAt;
                    if (pos == ii.position) {
                        drawAt = (ii.offset + ii.sizeFactor) * width;
                        offset = ii.offset + ii.sizeFactor + marginOffset;
                    } else {
                        float widthFactor = mAdapter.getPageWidth(pos);
                        drawAt = (offset + widthFactor) * width;
                        offset += widthFactor + marginOffset;
                    }

                    if (drawAt + mPageMargin > scrollX) {
                        mMarginDrawable.setBounds(Math.round(drawAt), mTopPageBounds,
                                Math.round(drawAt + mPageMargin), mBottomPageBounds);
                        mMarginDrawable.draw(canvas);
                    }

                    if (drawAt > scrollX + width) {
                        break; // No more visible, no sense in continuing
                    }
                }
            }
        } else {
            /*************方向是垂直********************/
            // Draw the margin drawable between pages if needed.
            if (mPageMargin > 0 && mMarginDrawable != null && mItems.size() > 0 && mAdapter != null) {
                final int scrollY = getScrollY();
                final int height = getHeight();

                final float marginOffset = (float) mPageMargin / height;
                int itemIndex = 0;
                ItemInfo ii = mItems.get(0);
                float offset = ii.offset;
                final int itemCount = mItems.size();
                final int firstPos = ii.position;
                final int lastPos = mItems.get(itemCount - 1).position;
                for (int pos = firstPos; pos < lastPos; pos++) {
                    while (pos > ii.position && itemIndex < itemCount) {
                        ii = mItems.get(++itemIndex);
                    }

                    float drawAt;
                    if (pos == ii.position) {
                        drawAt = (ii.offset + ii.sizeFactor) * height;
                        offset = ii.offset + ii.sizeFactor + marginOffset;
                    } else {
                        float widthFactor = mAdapter.getPageWidth(pos);
                        drawAt = (offset + widthFactor) * height;
                        offset += widthFactor + marginOffset;
                    }

                    if (drawAt + mPageMargin > scrollY) {
                        mMarginDrawable.setBounds(Math.round(drawAt), mTopPageBounds,
                                Math.round(drawAt + mPageMargin), mBottomPageBounds);
                        mMarginDrawable.setBounds(mLeftPageBounds, Math.round(drawAt),
                                mRightPageBounds, Math.round(drawAt + mPageMargin));
                        mMarginDrawable.draw(canvas);
                    }

                    if (drawAt > scrollY + height) {
                        break; // No more visible, no sense in continuing
                    }
                }
            }

        }
    }


    /**
     * Start a fake drag of the pager.
     * <p>
     * <p>A fake drag can be useful if you want to synchronize the motion of the ViewPager
     * with the touch scrolling of another view, while still letting the ViewPager
     * control the snapping motion and fling behavior. (e.g. parallax-scrolling tabs.)
     * Call {@link #fakeDragBy(float)} to simulate the actual drag motion. Call
     * {@link #endFakeDrag()} to complete the fake drag and fling as necessary.
     * <p>
     * <p>During a fake drag the ViewPager will ignore all touch events. If a real drag
     * is already in progress, this method will return false.
     *
     * @return true if the fake drag began successfully, false if it could not be started.
     * @see #fakeDragBy(float)
     * @see #endFakeDrag()
     */
    public boolean beginFakeDrag() {
        if (mIsBeingDragged) {
            return false;
        }
        mFakeDragging = true;
        setScrollState(SCROLL_STATE_DRAGGING);
        mInitialMotionX = mLastMotionX = 0;
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
        final long time = SystemClock.uptimeMillis();
        final MotionEvent ev = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0, 0, 0);
        mVelocityTracker.addMovement(ev);
        ev.recycle();
        mFakeDragBeginTime = time;
        return true;
    }

    /**
     * End a fake drag of the pager.
     *
     * @see #beginFakeDrag()
     * @see #fakeDragBy(float)
     */
    public void endFakeDrag() {
        if (!mFakeDragging) {
            throw new IllegalStateException("No fake drag in progress. Call beginFakeDrag first.");
        }

        if (mAdapter != null) {
            final VelocityTracker velocityTracker = mVelocityTracker;
            velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
            int initialVelocity = (int) velocityTracker.getXVelocity(mActivePointerId);
            mPopulatePending = true;
            final int width = getClientWidth();
            final int scrollX = getScrollX();
            final ItemInfo ii = infoForCurrentScrollPosition();
            final int currentPage = ii.position;
            final float pageOffset = (((float) scrollX / width) - ii.offset) / ii.sizeFactor;
            final int totalDelta = (int) (mLastMotionX - mInitialMotionX);
            int nextPage = determineTargetPage(currentPage, pageOffset, initialVelocity,
                    totalDelta);
            setCurrentItemInternal(nextPage, true, true, initialVelocity);
        }
        endDrag();

        mFakeDragging = false;
    }

    /**
     * Fake drag by an offset in pixels. You must have called {@link #beginFakeDrag()} first.
     *
     * @param xOffset Offset in pixels to drag by.
     * @see #beginFakeDrag()
     * @see #endFakeDrag()
     */
    public void fakeDragBy(float xOffset) {
        if (!mFakeDragging) {
            throw new IllegalStateException("No fake drag in progress. Call beginFakeDrag first.");
        }

        if (mAdapter == null) {
            return;
        }

        mLastMotionX += xOffset;

        float oldScrollX = getScrollX();
        float scrollX = oldScrollX - xOffset;
        final int width = getClientWidth();

        float leftBound = width * mFirstOffset;
        float rightBound = width * mLastOffset;

        final ItemInfo firstItem = mItems.get(0);
        final ItemInfo lastItem = mItems.get(mItems.size() - 1);
        if (firstItem.position != 0) {
            leftBound = firstItem.offset * width;
        }
        if (lastItem.position != mAdapter.getCount() - 1) {
            rightBound = lastItem.offset * width;
        }

        if (scrollX < leftBound) {
            scrollX = leftBound;
        } else if (scrollX > rightBound) {
            scrollX = rightBound;
        }
        // Don't lose the rounded component
        mLastMotionX += scrollX - (int) scrollX;
        scrollTo((int) scrollX, getScrollY());
        pageScrolled((int) scrollX);

        // Synthesize an event for the VelocityTracker.
        final long time = SystemClock.uptimeMillis();
        final MotionEvent ev = MotionEvent.obtain(mFakeDragBeginTime, time, MotionEvent.ACTION_MOVE,
                mLastMotionX, 0, 0);
        mVelocityTracker.addMovement(ev);
        ev.recycle();
    }

    /**
     * Returns true if a fake drag is in progress.
     *
     * @return true if currently in a fake drag, false otherwise.
     * @see #beginFakeDrag()
     * @see #fakeDragBy(float)
     * @see #endFakeDrag()
     */
    public boolean isFakeDragging() {
        return mFakeDragging;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = ev.getActionIndex();
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = ev.getX(newPointerIndex);
            mLastMotionY = ev.getY(newPointerIndex);
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    private void endDrag() {
        mIsBeingDragged = false;
        mIsUnableToDrag = false;

        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private void setScrollingCacheEnabled(boolean enabled) {
        if (mScrollingCacheEnabled != enabled) {
            mScrollingCacheEnabled = enabled;
            if (USE_CACHE) {
                final int size = getChildCount();
                for (int i = 0; i < size; ++i) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() != GONE) {
                        child.setDrawingCacheEnabled(enabled);
                    }
                }
            }
        }
    }

    /**
     * Check if this ViewPager can be scrolled horizontally in a certain direction.
     *
     * @param direction Negative to check scrolling left, positive to check scrolling right.
     * @return Whether this ViewPager can be scrolled in the specified direction. It will always
     * return false if the specified direction is 0.
     */
    @Override
    public boolean canScrollHorizontally(int direction) {
        if (mAdapter == null) {
            return false;
        }

        final int width = getClientWidth();
        final int scrollX = getScrollX();
        if (direction < 0) {
            return (scrollX > (int) (width * mFirstOffset));
        } else if (direction > 0) {
            return (scrollX < (int) (width * mLastOffset));
        } else {
            return false;
        }
    }

    @Override
    public boolean canScrollVertically(int direction) {
        if (mAdapter == null) {
            return false;
        }

        final int height = getClientHeight();
        final int scrollY = getScrollY();
        if (direction < 0) {
            return (scrollY > (int) (height * mFirstOffset));
        } else if (direction > 0) {
            return (scrollY < (int) (height * mLastOffset));
        } else {
            return false;
        }
    }

    /**
     * Tests scrollability within child views of v given a delta of dx.
     *
     * @param v      View to test for horizontal scrollability
     * @param checkV Whether the view v passed should itself be checked for scrollability (true),
     *               or just its children (false).
     * @param dxy    Delta scrolled in pixels
     * @param x      X coordinate of the active touch point
     * @param y      Y coordinate of the active touch point
     * @return true if child views of v can be scrolled by delta of dx.
     */
    protected boolean canScroll(View v, boolean checkV, int dxy, int x, int y) {
        if (v instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) v;
            final int scrollX = v.getScrollX();
            final int scrollY = v.getScrollY();
            final int count = group.getChildCount();
            // Count backwards - let topmost views consume scroll distance first.
            for (int i = count - 1; i >= 0; i--) {
                // TODO: Add versioned support here for transformed views.
                // This will not work for transformed views in Honeycomb+
                final View child = group.getChildAt(i);
                if (x + scrollX >= child.getLeft() && x + scrollX < child.getRight()
                        && y + scrollY >= child.getTop() && y + scrollY < child.getBottom()
                        && canScroll(child, true, dxy, x + scrollX - child.getLeft(),
                        y + scrollY - child.getTop())) {
                    return true;
                }
            }
        }

        /*************方向是水平********************/
        if (mOrientation == Orientation.HORIZONTAL) {
            return checkV && v.canScrollHorizontally(-dxy);
        } else {
            /*************方向是垂直********************/
            return checkV && canScrollVertically(-dxy);
        }

    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Let the focused view and/or our descendants get the key first
        return super.dispatchKeyEvent(event) || executeKeyEvent(event);
    }

    /**
     * You can call this function yourself to have the scroll view perform
     * scrolling from a key event, just as if the event had been dispatched to
     * it by the view hierarchy.
     *
     * @param event The key event to execute.
     * @return Return true if the event was handled, else false.
     */

    //处理实体按键的左右滑动，现在不做处理
    public boolean executeKeyEvent(@NonNull KeyEvent event) {
        boolean handled = false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (event.hasModifiers(KeyEvent.META_ALT_ON)) {
                        handled = pageLeft();
                    } else {
                        handled = arrowScroll(FOCUS_LEFT);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (event.hasModifiers(KeyEvent.META_ALT_ON)) {
                        handled = pageRight();
                    } else {
                        handled = arrowScroll(FOCUS_RIGHT);
                    }
                    break;
                case KeyEvent.KEYCODE_TAB:
                    if (event.hasNoModifiers()) {
                        handled = arrowScroll(FOCUS_FORWARD);
                    } else if (event.hasModifiers(KeyEvent.META_SHIFT_ON)) {
                        handled = arrowScroll(FOCUS_BACKWARD);
                    }
                    break;
            }


        }
        return handled;
    }

    /**
     * Handle scrolling in response to a left or right arrow click.
     *
     * @param direction The direction corresponding to the arrow key that was pressed. It should be
     *                  either {@link View#FOCUS_LEFT} or {@link View#FOCUS_RIGHT}.
     * @return Whether the scrolling was handled successfully.
     */

    //跟实体按键相关，不做处理
    public boolean arrowScroll(int direction) {
        View currentFocused = findFocus();
        if (currentFocused == this) {
            currentFocused = null;
        } else if (currentFocused != null) {
            boolean isChild = false;
            for (ViewParent parent = currentFocused.getParent(); parent instanceof ViewGroup;
                 parent = parent.getParent()) {
                if (parent == this) {
                    isChild = true;
                    break;
                }
            }
            if (!isChild) {
                // This would cause the focus search down below to fail in fun ways.
                final StringBuilder sb = new StringBuilder();
                sb.append(currentFocused.getClass().getSimpleName());
                for (ViewParent parent = currentFocused.getParent(); parent instanceof ViewGroup;
                     parent = parent.getParent()) {
                    sb.append(" => ").append(parent.getClass().getSimpleName());
                }
                Log.e(TAG, "arrowScroll tried to find focus based on non-child "
                        + "current focused view " + sb.toString());
                currentFocused = null;
            }
        }

        boolean handled = false;

        View nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused,
                direction);
        if (nextFocused != null && nextFocused != currentFocused) {
            if (direction == View.FOCUS_LEFT) {
                // If there is nothing to the left, or this is causing us to
                // jump to the right, then what we really want to do is page left.
                final int nextLeft = getChildRectInPagerCoordinates(mTempRect, nextFocused).left;
                final int currLeft = getChildRectInPagerCoordinates(mTempRect, currentFocused).left;
                if (currentFocused != null && nextLeft >= currLeft) {
                    handled = pageLeft();
                } else {
                    handled = nextFocused.requestFocus();
                }
            } else if (direction == View.FOCUS_RIGHT) {
                // If there is nothing to the right, or this is causing us to
                // jump to the left, then what we really want to do is page right.
                final int nextLeft = getChildRectInPagerCoordinates(mTempRect, nextFocused).left;
                final int currLeft = getChildRectInPagerCoordinates(mTempRect, currentFocused).left;
                if (currentFocused != null && nextLeft <= currLeft) {
                    handled = pageRight();
                } else {
                    handled = nextFocused.requestFocus();
                }
            }
        } else if (direction == FOCUS_LEFT || direction == FOCUS_BACKWARD) {
            // Trying to move left and nothing there; try to page.
            handled = pageLeft();
        } else if (direction == FOCUS_RIGHT || direction == FOCUS_FORWARD) {
            // Trying to move right and nothing there; try to page.
            handled = pageRight();
        }
        if (handled) {
            playSoundEffect(SoundEffectConstants.getContantForFocusDirection(direction));
        }
        return handled;
    }

    private Rect getChildRectInPagerCoordinates(Rect outRect, View child) {
        if (outRect == null) {
            outRect = new Rect();
        }
        if (child == null) {
            outRect.set(0, 0, 0, 0);
            return outRect;
        }
        outRect.left = child.getLeft();
        outRect.right = child.getRight();
        outRect.top = child.getTop();
        outRect.bottom = child.getBottom();

        ViewParent parent = child.getParent();
        while (parent instanceof ViewGroup && parent != this) {
            final ViewGroup group = (ViewGroup) parent;
            outRect.left += group.getLeft();
            outRect.right += group.getRight();
            outRect.top += group.getTop();
            outRect.bottom += group.getBottom();

            parent = group.getParent();
        }
        return outRect;
    }

    boolean pageLeft() {
        if (mCurItem > 0) {
            setCurrentItem(mCurItem - 1, true);
            return true;
        }
        return false;
    }

    boolean pageRight() {
        if (mAdapter != null && mCurItem < (mAdapter.getCount() - 1)) {
            setCurrentItem(mCurItem + 1, true);
            return true;
        }
        return false;
    }

    /**
     * We only want the current page that is being shown to be focusable.
     */
    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        final int focusableCount = views.size();

        final int descendantFocusability = getDescendantFocusability();

        if (descendantFocusability != FOCUS_BLOCK_DESCENDANTS) {
            for (int i = 0; i < getChildCount(); i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() == VISIBLE) {
                    ItemInfo ii = infoForChild(child);
                    if (ii != null && ii.position == mCurItem) {
                        child.addFocusables(views, direction, focusableMode);
                    }
                }
            }
        }

        // we add ourselves (if focusable) in all cases except for when we are
        // FOCUS_AFTER_DESCENDANTS and there are some descendants focusable.  this is
        // to avoid the focus search finding layouts when a more precise search
        // among the focusable children would be more interesting.
        if (descendantFocusability != FOCUS_AFTER_DESCENDANTS
                || (focusableCount == views.size())) { // No focusable descendants
            // Note that we can't call the superclass here, because it will
            // add all views in.  So we need to do the same thing View does.
            if (!isFocusable()) {
                return;
            }
            if ((focusableMode & FOCUSABLES_TOUCH_MODE) == FOCUSABLES_TOUCH_MODE
                    && isInTouchMode() && !isFocusableInTouchMode()) {
                return;
            }
            if (views != null) {
                views.add(this);
            }
        }
    }

    /**
     * We only want the current page that is being shown to be touchable.
     */
    @Override
    public void addTouchables(ArrayList<View> views) {
        // Note that we don't call super.addTouchables(), which means that
        // we don't call View.addTouchables().  This is okay because a ViewPager
        // is itself not touchable.
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == VISIBLE) {
                ItemInfo ii = infoForChild(child);
                if (ii != null && ii.position == mCurItem) {
                    child.addTouchables(views);
                }
            }
        }
    }

    /**
     * We only want the current page that is being shown to be focusable.
     */
    @Override
    protected boolean onRequestFocusInDescendants(int direction,
                                                  Rect previouslyFocusedRect) {
        int index;
        int increment;
        int end;
        int count = getChildCount();
        if ((direction & FOCUS_FORWARD) != 0) {
            index = 0;
            increment = 1;
            end = count;
        } else {
            index = count - 1;
            increment = -1;
            end = -1;
        }
        for (int i = index; i != end; i += increment) {
            View child = getChildAt(i);
            if (child.getVisibility() == VISIBLE) {
                ItemInfo ii = infoForChild(child);
                if (ii != null && ii.position == mCurItem) {
                    if (child.requestFocus(direction, previouslyFocusedRect)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Dispatch scroll events from this ViewPager.
        if (event.getEventType() == AccessibilityEventCompat.TYPE_VIEW_SCROLLED) {
            return super.dispatchPopulateAccessibilityEvent(event);
        }

        // Dispatch all other accessibility events from the current page.
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == VISIBLE) {
                final ItemInfo ii = infoForChild(child);
                if (ii != null && ii.position == mCurItem
                        && child.dispatchPopulateAccessibilityEvent(event)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return generateDefaultLayoutParams();
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams && super.checkLayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    class MyAccessibilityDelegate extends AccessibilityDelegateCompat {

        @Override
        public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(host, event);
            event.setClassName(Test.class.getName());
            event.setScrollable(canScroll());
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED && mAdapter != null) {
                event.setItemCount(mAdapter.getCount());
                event.setFromIndex(mCurItem);
                event.setToIndex(mCurItem);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName(Test.class.getName());
            info.setScrollable(canScroll());
            /*************方向是水平********************/
            if (mOrientation == Orientation.HORIZONTAL) {
                if (canScrollHorizontally(1)) {
                    info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
                }
                if (canScrollHorizontally(-1)) {
                    info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
                }
            } else {
                /*************方向是垂直********************/
                if (canScrollVertically(1)) {
                    info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
                }
                if (canScrollVertically(-1)) {
                    info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
                }
            }

        }

        @Override
        public boolean performAccessibilityAction(View host, int action, Bundle args) {
            if (super.performAccessibilityAction(host, action, args)) {
                return true;
            }
            /*************方向是水平********************/
            if (mOrientation == Orientation.HORIZONTAL) {
                switch (action) {
                    case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD: {
                        if (canScrollHorizontally(1)) {
                            setCurrentItem(mCurItem + 1);
                            return true;
                        }
                    }
                    return false;
                    case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD: {
                        if (canScrollHorizontally(-1)) {
                            setCurrentItem(mCurItem - 1);
                            return true;
                        }
                    }
                    return false;
                }

            } else {
                /*************方向是垂直********************/
                switch (action) {
                    case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD: {
                        if (canScrollVertically(1)) {
                            setCurrentItem(mCurItem + 1);
                            return true;
                        }
                    }
                    return false;
                    case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD: {
                        if (canScrollVertically(-1)) {
                            setCurrentItem(mCurItem - 1);
                            return true;
                        }
                    }
                    return false;
                }

            }

            return false;
        }

        private boolean canScroll() {
            return (mAdapter != null) && (mAdapter.getCount() > 1);
        }
    }

    private class PagerObserver extends DataSetObserver {
        PagerObserver() {
        }

        @Override
        public void onChanged() {
            dataSetChanged();
        }

        @Override
        public void onInvalidated() {
            dataSetChanged();
        }
    }

    /**
     * Layout parameters that should be supplied for views added to a
     * ViewPager.
     */
    public static class LayoutParams extends ViewGroup.LayoutParams {
        /**
         * true if this view is a decoration on the pager itself and not
         * a view supplied by the adapter.
         */
        public boolean isDecor;

        /**
         * Gravity setting for use on decor views only:
         * Where to position the view page within the overall ViewPager
         * container; constants are defined in {@link Gravity}.
         */
        public int gravity;

        /**
         * Width as a 0-1 multiplier of the measured pager width
         */
        float widthFactor = 0.f;

        /**
         * true if this view was added during layout and needs to be measured
         * before being positioned.
         */
        boolean needsMeasure;

        /**
         * Adapter position this view is for if !isDecor
         */
        int position;

        /**
         * Current child index within the ViewPager that this view occupies
         */
        int childIndex;

        public LayoutParams() {
            super(MATCH_PARENT, MATCH_PARENT);
        }

        public LayoutParams(Context context, AttributeSet attrs) {
            super(context, attrs);

            final TypedArray a = context.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
            gravity = a.getInteger(0, Gravity.TOP);
            a.recycle();
        }
    }

    static class ViewPositionComparator implements Comparator<View> {
        @Override
        public int compare(View lhs, View rhs) {
            final LayoutParams llp = (LayoutParams) lhs.getLayoutParams();
            final LayoutParams rlp = (LayoutParams) rhs.getLayoutParams();
            if (llp.isDecor != rlp.isDecor) {
                return llp.isDecor ? 1 : -1;
            }
            return llp.position - rlp.position;
        }
    }
}
