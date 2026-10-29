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

package com.android.launcher3;

import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_BUBBLE_ADJUSTMENT_ANIM;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.android.launcher3.ShortcutAndWidgetContainer.TranslationProvider;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.dragndrop.SystemDragItemInfo;
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtilsKt;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.pageindicators.PageIndicatorDotsWithArrows;
import com.android.launcher3.util.HorizontalInsettableView;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.PenguinDockGlass;
import com.android.launcher3.util.PenguinDockMetrics;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.PendingAddWidgetInfo;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * View class that represents the bottom row of the home screen.
 */
public class Hotseat extends CellLayout implements Insettable {

    public static final int ALPHA_CHANNEL_TASKBAR_ALIGNMENT = 0;
    public static final int ALPHA_CHANNEL_PREVIEW_RENDERER = 1;
    public static final int ALPHA_CHANNEL_TASKBAR_STASH = 2;
    public static final int ALPHA_CHANNEL_ASSISTANT_VISIBILITY = 3;
    /** The Search pill gives way to the page dots while pages scroll. */
    public static final int ALPHA_CHANNEL_PAGE_DOTS = 4;
    public static final int ALPHA_CHANNEL_CHANNELS_COUNT = 5;

    @Retention(RetentionPolicy.RUNTIME)
    @IntDef({ALPHA_CHANNEL_TASKBAR_ALIGNMENT, ALPHA_CHANNEL_PREVIEW_RENDERER,
            ALPHA_CHANNEL_TASKBAR_STASH, ALPHA_CHANNEL_ASSISTANT_VISIBILITY,
            ALPHA_CHANNEL_PAGE_DOTS})
    public @interface HotseatQsbAlphaId {
    }

    public static final int ICONS_TRANSLATION_X_NAV_BAR_ALIGNMENT = 0;
    public static final int ICONS_TRANSLATION_X_STATE = 1;
    public static final int ICONS_TRANSLATION_X_CHANNELS_COUNT = 2;

    @Retention(RetentionPolicy.RUNTIME)
    @IntDef({ICONS_TRANSLATION_X_NAV_BAR_ALIGNMENT, ICONS_TRANSLATION_X_STATE})
    public @interface IconsTranslationX {
    }

    // Ratio of empty space, qsb should take up to appear visually centered.
    public static final float QSB_CENTER_FACTOR = .325f;
    private static final int BUBBLE_BAR_ADJUSTMENT_ANIMATION_DURATION_MS = 250;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHasVerticalHotseat;
    private Workspace<?> mWorkspace;
    private boolean mSendTouchToWorkspace;
    private final MultiValueAlpha mIconsAlphaChannels;
    private final @Nullable MultiValueAlpha mQsbAlphaChannels;

    private @Nullable MultiProperty mQsbTranslationX;

    private final MultiPropertyFactory mIconsTranslationXFactory;

    private final @Nullable View mQsb;
    private final View mSearchPill;
    private View mDockCompanion;
    private int mDockCompanionSlots;
    private int mDockPreferredSlots;
    private int mDockSlot;
    private final PenguinDockGlass mDockGlass = new PenguinDockGlass(this);
    private PenguinDockGlass mSearchPillGlass;
    /** Glass of the large-screen Search pill that stands in the QSB slot; null otherwise. */
    private @Nullable PenguinDockGlass mQsbPillGlass;
    /** The Search pill's centre as a distance from the bottom of the home screen; -1 if none. */
    private int mSearchPillCentreFromBottom = -1;
    private int mDockBlurMargin;
    /** Whether the dock lays out at its natural width (PenguinDockMetrics.usesNaturalDock). */
    private boolean mNaturalDock;

    /**
     * Recent apps the home dock laid out beside its pinned apps. Published once per measure as
     * one value, so the taskbar (possibly on its own thread) reads a coherent count.
     */
    private volatile int mPublishedDockRecentSlots;

    /** Recent apps the home dock shows beside its pinned apps, as laid out. */
    public int getDockCompanionSlots() {
        return mPublishedDockRecentSlots;
    }

    /**
     * Space kept clear above the search pill so a blur of the dock fades out inside this view
     * instead of being cut off at its top edge. Touches there still reach the workspace.
     */
    public int getDockBlurMarginPx() {
        return mDockBlurMargin;
    }

    /**
     * Sets how far the dock content is blurred as part of home, from 0 (sharp) to 1. The live
     * glass is a compositor blur that a view blur cannot soften, so it gives way to a plain
     * glass fill that blurs along with the icons.
     */
    public void setDockContentBlur(float progress) {
        mDockGlass.setFlatProgress(progress);
        if (mSearchPillGlass != null) mSearchPillGlass.setFlatProgress(progress);
        if (mQsbPillGlass != null) mQsbPillGlass.setFlatProgress(progress);
    }

    public void setDockCompanionSlotCount(int count) {
        int slots = Math.max(0, Math.min(3, count));
        if (mDockPreferredSlots != slots) {
            mDockPreferredSlots = slots;
            requestLayout();
        }
    }

    public void refreshDockCompanion() {
        if (mDockCompanion != null) mDockCompanion.callOnClick();
    }

    public void setDockCompanion(View companion) {
        if (mDockCompanion != null) removeView(mDockCompanion);
        mDockCompanion = companion;
        addView(companion);
        requestLayout();
    }

    @Override
    protected void dispatchDraw(android.graphics.Canvas canvas) {
        if (mDockCompanion != null && mNaturalDock) {
            View icons = getShortcutsAndWidgets();
            int iconHeight = mActivity.getDeviceProfile().getWorkspaceProfile().getIconSizePx();
            int gap = PenguinDockMetrics.glassPaddingPx(iconHeight);
            int centre = (icons.getTop() + icons.getBottom()) / 2;
            int verticalPad = Math.round(12 * getResources().getDisplayMetrics().density);
            mDockGlass.setBounds(Math.min(icons.getLeft(), mDockCompanion.getLeft()) - gap,
                    centre - iconHeight / 2 - verticalPad,
                    Math.max(icons.getRight(), mDockCompanion.getRight()) + gap,
                    centre + iconHeight / 2 + verticalPad);
            mDockGlass.setAlpha(Math.round(255 * icons.getAlpha()));
            mDockGlass.draw(canvas);
            mDockCompanion.setAlpha(icons.getAlpha());
        }
        super.dispatchDraw(canvas);
    }

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (Flags.enableQsbOnHotseat()) {
            if (mActivity.getDeviceProfile().getDeviceProperties().isLargeScreen()) {
                mQsb = LayoutInflater.from(context).inflate(R.layout.home_search_pill, this, false);
                mQsbPillGlass = new PenguinDockGlass(mQsb);
                mQsbPillGlass.setCapsule(true);
                mQsb.setBackground(mQsbPillGlass);
                mQsb.setOnClickListener(v -> {
                    if (mActivity instanceof Launcher launcher) launcher.openSearchFromPill(v);
                });
            } else {
                mQsb = LauncherComponentProvider.get(context).getQsbWidgetFactory().createView(this);
            }
            addView(mQsb);
            if (mQsb instanceof Reorderable qsbReorderable) {
                mQsbTranslationX = qsbReorderable.getTranslateDelegate()
                        .getTranslationX(MultiTranslateDelegate.INDEX_NAV_BAR_ANIM);
            }
            mQsbAlphaChannels = new MultiValueAlpha(mQsb, ALPHA_CHANNEL_CHANNELS_COUNT);
            mQsbAlphaChannels.setUpdateVisibility(true);
        } else {
            mQsb = null;
            mQsbAlphaChannels = null;
        }

        mSearchPill = LayoutInflater.from(context).inflate(R.layout.home_search_pill, this, false);
        mSearchPillGlass = new PenguinDockGlass(mSearchPill);
        mSearchPillGlass.setCapsule(true);
        mSearchPill.setBackground(mSearchPillGlass);
        mSearchPill.setOnClickListener(v -> {
            if (mActivity instanceof Launcher launcher) launcher.openSearchFromPill(v);
        });
        addView(mSearchPill);

        mIconsAlphaChannels = new MultiValueAlpha(getShortcutsAndWidgets(),
                ALPHA_CHANNEL_CHANNELS_COUNT);
        mIconsAlphaChannels.setUpdateVisibility(true);
        mIconsTranslationXFactory = new MultiPropertyFactory<>(getShortcutsAndWidgets(),
                VIEW_TRANSLATE_X, ICONS_TRANSLATION_X_CHANNELS_COUNT, Float::sum);
    }

    /** Provides translation X for hotseat icons for the channel. */
    public MultiProperty getIconsTranslationX(@IconsTranslationX int channelId) {
        return mIconsTranslationXFactory.get(channelId);
    }

    /** Provides translation X for hotseat Qsb. */
    @Nullable
    public MultiProperty getQsbTranslationX() {
        return mQsbTranslationX;
    }

    /**
     * Returns orientation specific cell X given invariant order in the hotseat
     */
    public int getCellXFromOrder(int rank) {
        return mHasVerticalHotseat ? 0 : rank;
    }

    /**
     * Returns orientation specific cell Y given invariant order in the hotseat
     */
    public int getCellYFromOrder(int rank) {
        return mHasVerticalHotseat ? (getCountY() - (rank + 1)) : 0;
    }

    boolean isHasVerticalHotseat() {
        return mHasVerticalHotseat;
    }

    /** Returns whether the hotseat is a valid drop target for the specified drag object. */
    public boolean isValidDropTarget(DropTarget.DragObject dragObject) {
        final ShortcutAndWidgetContainer shortcutAndWidgetContainer = getShortcutsAndWidgets();
        return shortcutAndWidgetContainer != null
                && shortcutAndWidgetContainer.getVisibility() == View.VISIBLE
                && isSupportedDrag(dragObject);
    }

    public void resetLayout(boolean hasVerticalHotseat) {
        ActivityContext activityContext = ActivityContext.lookupContext(getContext());
        boolean bubbleBarEnabled = activityContext.isBubbleBarEnabled();
        boolean hasBubbles = activityContext.hasBubbles();
        removeAllViewsInLayout();
        mHasVerticalHotseat = hasVerticalHotseat;
        DeviceProfile dp = mActivity.getDeviceProfile();

        if (bubbleBarEnabled) {
            if (dp.shouldAdjustHotseatForBubbleBar(getContext(), hasBubbles)) {
                getShortcutsAndWidgets().setTranslationProvider(
                        cellX -> dp.getHotseatAdjustedTranslation(getContext(), cellX));
                if (mQsb instanceof HorizontalInsettableView) {
                    HorizontalInsettableView insettableQsb = (HorizontalInsettableView) mQsb;
                    final float insetFraction =
                            (float) dp.getWorkspaceProfile().getIconSizePx()
                                    / dp.getHotseatProfile().getQsbWidth();
                    // post this to the looper so that QSB has a chance to redraw itself, e.g.
                    // after device rotation
                    mQsb.post(() -> insettableQsb.setHorizontalInsets(insetFraction));
                }
            } else {
                getShortcutsAndWidgets().setTranslationProvider(null);
                if (mQsb instanceof HorizontalInsettableView) {
                    ((HorizontalInsettableView) mQsb).setHorizontalInsets(0);
                }
            }
        }

        resetCellSize(dp);
        if (hasVerticalHotseat) {
            setGridSize(1, dp.getHotseatProfile().getNumShownIcons());
        } else {
            setGridSize(dp.getHotseatProfile().getNumShownIcons(), 1);
        }
    }

    /**
     * Adjust the hotseat icons for the bubble bar.
     *
     * <p>When the bubble bar becomes visible, if needed, this method animates the hotseat icons
     * to reduce the spacing between them and make room for the bubble bar. The QSB width is
     * animated as well to align with the hotseat icons.
     *
     * <p>When the bubble bar goes away, any adjustments that were previously made are reversed.
     */
    public void adjustForBubbleBar(boolean isBubbleBarVisible) {
        DeviceProfile dp = mActivity.getDeviceProfile();
        boolean shouldAdjust = isBubbleBarVisible
                && dp.shouldAdjustHotseatOrQsbForBubbleBar(getContext());
        boolean shouldAdjustHotseat = shouldAdjust
                && dp.shouldAlignBubbleBarWithHotseat();
        ShortcutAndWidgetContainer icons = getShortcutsAndWidgets();
        // update the translation provider for future layout passes of hotseat icons.
        if (shouldAdjustHotseat) {
            icons.setTranslationProvider(
                    cellX -> dp.getHotseatAdjustedTranslation(getContext(), cellX));
        } else {
            icons.setTranslationProvider(null);
        }
        AnimatorSet animatorSet = new AnimatorSet();
        for (int i = 0; i < icons.getChildCount(); i++) {
            View child = icons.getChildAt(i);
            if (child.getLayoutParams() instanceof CellLayoutLayoutParams lp) {
                float tx = shouldAdjustHotseat
                        ? dp.getHotseatAdjustedTranslation(getContext(), lp.getCellX()) : 0;
                if (child instanceof Reorderable) {
                    MultiTranslateDelegate mtd = ((Reorderable) child).getTranslateDelegate();
                    animatorSet.play(
                            mtd.getTranslationX(INDEX_BUBBLE_ADJUSTMENT_ANIM).animateToValue(tx));
                } else {
                    animatorSet.play(ObjectAnimator.ofFloat(child, VIEW_TRANSLATE_X, tx));
                }
            }
        }
        //TODO(b/381109832) refactor & simplify adjustment logic
        boolean shouldAdjustQsb =
                shouldAdjustHotseat || (shouldAdjust && dp.shouldAlignBubbleBarWithQSB());
        if (mQsb instanceof HorizontalInsettableView horizontalInsettableQsb) {
            final float currentInsetFraction = horizontalInsettableQsb.getHorizontalInsets();
            final float targetInsetFraction = shouldAdjustQsb
                    ? (float) dp.getWorkspaceProfile().getIconSizePx() / dp.getHotseatProfile()
                    .getQsbWidth()
                    : 0;
            ValueAnimator qsbAnimator =
                    ValueAnimator.ofFloat(currentInsetFraction, targetInsetFraction);
            qsbAnimator.addUpdateListener(animation -> {
                float insetFraction = (float) animation.getAnimatedValue();
                horizontalInsettableQsb.setHorizontalInsets(insetFraction);
            });
            animatorSet.play(qsbAnimator);
        }
        animatorSet.setDuration(BUBBLE_BAR_ADJUSTMENT_ANIMATION_DURATION_MS).start();
    }

    @Override
    protected int getTranslationXForCell(int cellX, int cellY) {
        TranslationProvider translationProvider = getShortcutsAndWidgets().getTranslationProvider();
        if (translationProvider == null) return 0;
        return (int) translationProvider.getTranslationX(cellX);
    }

    @Override
    public void setInsets(Rect insets) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = mActivity.getDeviceProfile();

        // The Search pill sits above the dock on every screen size and shares its spot with the
        // page dots, which show in its place only while pages scroll.
        mSearchPill.setVisibility(grid.isVerticalBarLayout()
                || grid.getDeviceProperties().getTaskbarConfiguration().isTaskbarPresent()
                || !LauncherPrefs.isUniversalSearchOnHome(getContext())
                ? View.GONE : View.VISIBLE);
        if (grid.isVerticalBarLayout()) {
            if (mQsb != null) mQsb.setVisibility(View.GONE);
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (grid.isSeascape()) {
                lp.gravity = Gravity.LEFT;
                lp.width = grid.getHotseatProfile().getBarSizePx() + insets.left;
            } else {
                lp.gravity = Gravity.RIGHT;
                lp.width = grid.getHotseatProfile().getBarSizePx() + insets.right;
            }
        } else {
            if (mQsb != null) {
                mQsb.setVisibility(grid.isHotseatQsbEnabled() ? View.VISIBLE : View.GONE);
            }
            lp.gravity = Gravity.BOTTOM;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = grid.getHotseatProfile().getBarSizePx();
        }

        // The dock's own geometry applies only where it lays out at its natural width; inline
        // navigation or search bars, two panels and vertical hotseats keep the stock layout.
        boolean dock = mDockCompanion != null && PenguinDockMetrics.usesNaturalDock(grid);
        if (dock && mQsb != null && mSearchPill.getVisibility() == View.VISIBLE) {
            mQsb.setVisibility(View.GONE);
        }
        Rect padding = grid.getHotseatLayoutPadding(getContext());
        if (dock) {
            int bottom = insets.bottom + Math.round(20 * getResources().getDisplayMetrics().density);
            if (lp.height > 0) lp.height += bottom - padding.bottom;
            padding.bottom = bottom;
        }
        boolean hasSearchPill = mSearchPill.getVisibility() == View.VISIBLE
                || (dock && mQsb != null && mQsb.getVisibility() == View.VISIBLE);
        int pillSpace = !hasSearchPill ? 0
                : getResources().getDimensionPixelSize(R.dimen.home_search_pill_height)
                        + 2 * getResources().getDimensionPixelSize(R.dimen.home_search_pill_gap);
        mDockBlurMargin = !dock ? 0
                : getResources().getDimensionPixelSize(R.dimen.ipad_dock_blur_margin);
        if (lp.height > 0) lp.height += pillSpace + mDockBlurMargin;
        setPadding(padding.left, padding.top + pillSpace + mDockBlurMargin, padding.right,
                padding.bottom);
        setLayoutParams(lp);
        if (getActiveSearchPill() == null) {
            mSearchPillCentreFromBottom = -1;
            bindPageIndicatorToSearchPill();
        }
        InsettableFrameLayout.dispatchInsets(this, insets);
    }

    public void setWorkspace(Workspace<?> w) {
        mWorkspace = w;
        setCellLayoutContainer(w);
        bindPageIndicatorToSearchPill();
    }

    /** Lets the page dots take the Search pill's place while pages scroll. */
    private void bindPageIndicatorToSearchPill() {
        if (mWorkspace == null
                || !(mWorkspace.getPageIndicator() instanceof PageIndicatorDotsWithArrows dots)) {
            return;
        }
        if (mSearchPillCentreFromBottom < 0) {
            dots.setSearchPill(-1, null);
            setSearchPillReveal(0f);
        } else {
            dots.setSearchPill(mSearchPillCentreFromBottom, reveal -> {
                setSearchPillReveal(reveal);
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    /** The Search pill shown above the dock, if any: the dock's own or the one in the QSB slot. */
    private @Nullable View getActiveSearchPill() {
        if (mSearchPill.getVisibility() == View.VISIBLE) return mSearchPill;
        if (mQsbPillGlass != null && mQsb.getVisibility() == View.VISIBLE) return mQsb;
        return null;
    }

    /** Keeps the page dots centred on the Search pill once it is laid out. */
    private void updateSearchPillCentre() {
        View pill = getActiveSearchPill();
        int centre = -1;
        if (pill != null && getParent() instanceof View parent) {
            centre = parent.getHeight() - (getTop() + (pill.getTop() + pill.getBottom()) / 2);
        }
        if (centre != mSearchPillCentreFromBottom) {
            mSearchPillCentreFromBottom = centre;
            // Moving the dots relayouts the parent, so wait for this layout pass to finish.
            post(this::bindPageIndicatorToSearchPill);
        }
    }

    /** Fades the Search pill out as far as the page dots are shown in its place. */
    private void setSearchPillReveal(float dotsReveal) {
        float shown = 1f - dotsReveal;
        float scale = 0.9f + 0.1f * shown;
        mSearchPill.setAlpha(shown);
        mSearchPill.setScaleX(scale);
        mSearchPill.setScaleY(scale);
        mSearchPill.setClickable(shown > 0.5f);
        if (mQsbPillGlass != null) {
            mQsbAlphaChannels.get(ALPHA_CHANNEL_PAGE_DOTS).setValue(shown);
            mQsb.setScaleX(scale);
            mQsb.setScaleY(scale);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We allow horizontal workspace scrolling from within the Hotseat. We do this by delegating
        // touch intercept the Workspace, and if it intercepts, delegating touch to the Workspace
        // for the remainder of the this input stream.
        int yThreshold = getMeasuredHeight() - getPaddingBottom();
        if (mWorkspace != null && ev.getY() <= yThreshold) {
            mSendTouchToWorkspace = mWorkspace.onInterceptTouchEvent(ev);
            return mSendTouchToWorkspace;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // See comment in #onInterceptTouchEvent
        if (mSendTouchToWorkspace) {
            final int action = event.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mSendTouchToWorkspace = false;
            }
            return mWorkspace.onTouchEvent(event);
        }
        // Always let touch follow through to Workspace.
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mDockCompanion != null) {
            int icon = mActivity.getDeviceProfile().getWorkspaceProfile().getIconSizePx();
            int total = MeasureSpec.getSize(widthMeasureSpec);
            int pins = Math.max(1, getCountX());
            mDockSlot = PenguinDockMetrics.slotPx(icon);
            mNaturalDock = !mHasVerticalHotseat
                    && PenguinDockMetrics.usesNaturalDock(mActivity.getDeviceProfile());
            // Stock geometry (inline navigation or search bar, two panels): no recents section.
            if (!mNaturalDock) mDockCompanionSlots = 0;
            if (mNaturalDock) {
                android.view.WindowInsets insets = getRootWindowInsets();
                android.graphics.Insets safe = insets == null ? android.graphics.Insets.NONE
                        : insets.getInsets(android.view.WindowInsets.Type.systemBars()
                                | android.view.WindowInsets.Type.displayCutout());
                // The dock is as wide as its icons, never a fixed width: the profile already
                // chose how many pinned apps fit, recents take what is left, then it centres.
                int width = Math.max(1, total - safe.left - safe.right);
                int fit = PenguinDockMetrics.slotCapacity(width, icon);
                mDockCompanionSlots = Math.min(Math.min(mDockPreferredSlots,
                        PenguinDockMetrics.recentSlots(fit)), Math.max(0, fit - pins));
                int cells = pins + mDockCompanionSlots;
                mDockSlot = PenguinDockMetrics.fittedSlotPx(width, icon, cells);
                int content = cells * mDockSlot;
                int start = safe.left + (width - content) / 2;
                int end = total - start - content;
                int companionWidth = mDockCompanionSlots * mDockSlot;
                // Every pin owns its entire dock slot, including its touch target.
                mBorderSpace.set(0, 0);
                boolean rtl = Utilities.isRtl(getResources());
                setPadding(start + (rtl ? companionWidth : 0), getPaddingTop(),
                        end + (rtl ? 0 : companionWidth), getPaddingBottom());
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mPublishedDockRecentSlots = mDockCompanion != null && mNaturalDock
                ? mDockCompanionSlots : 0;
        if (mDockCompanion != null) {
            // Without the iPad dock, its recents section and divider are not shown at all.
            mDockCompanion.setVisibility(mNaturalDock ? View.VISIBLE : View.GONE);
            int height = getShortcutsAndWidgets().getMeasuredHeight();
            mDockCompanion.measure(makeMeasureSpec(mDockCompanionSlots * mDockSlot,
                    MeasureSpec.EXACTLY), makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        if (mSearchPill.getVisibility() != View.GONE) {
            mSearchPill.measure(makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    makeMeasureSpec(getResources().getDimensionPixelSize(
                            R.dimen.home_search_pill_height), MeasureSpec.EXACTLY));
        }

        if (mQsb != null && mQsb.getVisibility() != View.GONE) {
            DeviceProfile dp = mActivity.getDeviceProfile();
            if (mQsbPillGlass != null || (dp.getDeviceProperties().isLargeScreen()
                    && mDockCompanion != null && mNaturalDock)) {
                mQsb.measure(makeMeasureSpec(dp.getDeviceProperties().getAvailableWidthPx(),
                                MeasureSpec.AT_MOST),
                        makeMeasureSpec(getResources().getDimensionPixelSize(
                                R.dimen.home_search_pill_height), MeasureSpec.EXACTLY));
            } else {
                mQsb.measure(
                        makeMeasureSpec(dp.getHotseatQsbWidth(), MeasureSpec.EXACTLY),
                        makeMeasureSpec(dp.getHotseatProfile().getQsbHeight(), MeasureSpec.EXACTLY));
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        layoutDock(changed, l, t, r, b);
        updateSearchPillCentre();
    }

    private void layoutDock(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mDockBlurMargin > 0) {
            // Scale about the same centre as before the blur margin was added above.
            setPivotX((r - l) / 2f);
            setPivotY((b - t + mDockBlurMargin) / 2f);
        }
        if (mDockCompanion != null) {
            View icons = getShortcutsAndWidgets();
            boolean rtl = Utilities.isRtl(getResources());
            int start = rtl ? icons.getLeft() - mDockCompanion.getMeasuredWidth() : icons.getRight();
            mDockCompanion.layout(start, icons.getTop(), start + mDockCompanion.getMeasuredWidth(),
                    icons.getBottom());
        }

        if (mSearchPill.getVisibility() != View.GONE) {
            int pillWidth = mSearchPill.getMeasuredWidth();
            int pillLeft = (r - l - pillWidth) / 2;
            int pillBottom = getPaddingTop()
                    - getResources().getDimensionPixelSize(R.dimen.home_search_pill_gap);
            mSearchPill.layout(pillLeft, pillBottom - mSearchPill.getMeasuredHeight(),
                    pillLeft + pillWidth, pillBottom);
        }

        if (mQsb == null || mQsb.getVisibility() == View.GONE) return;
        if (mDockCompanion != null && mNaturalDock) {
            int width = mQsb.getMeasuredWidth();
            int left = (r - l - width) / 2;
            int bottom = getShortcutsAndWidgets().getTop()
                    - getResources().getDimensionPixelSize(R.dimen.home_search_pill_gap);
            mQsb.layout(left, bottom - mQsb.getMeasuredHeight(), left + width, bottom);
            return;
        }

        int qsbMeasuredWidth = mQsb.getMeasuredWidth();
        int left;
        DeviceProfile dp = mActivity.getDeviceProfile();
        if (dp.getHotseatProfile().isQsbInline()) {
            int qsbSpace = dp.getHotseatProfile().getBorderSpace();
            left = Utilities.isRtl(getResources()) ? r - getPaddingRight() + qsbSpace
                    : l + getPaddingLeft() - qsbMeasuredWidth - qsbSpace;
        } else {
            left = (r - l - qsbMeasuredWidth) / 2;
        }
        int right = left + qsbMeasuredWidth;

        int height = b - t;
        int qsbHeight = dp.getDeviceProperties().isLargeScreen()
                ? mQsb.getMeasuredHeight() : dp.getHotseatProfile().getQsbHeight();
        int bottomOffset;
        int subtraction;
        if (dp.getHotseatProfile().isQsbInline()) {
            bottomOffset = dp.getHotseatBarBottomPadding();
            subtraction = (qsbHeight - dp.getHotseatProfile().getCellHeightPx()) / 2;
        } else if (dp.getDeviceProperties().getTaskbarConfiguration().isTaskbarPresent()) {
            int offsetY = (dp.getHotseatProfile().getBarSizePx() - qsbHeight)
                    + dp.getHotseatProfile().getQsbShadowHeight();
            int bottom = height - offsetY;
            mQsb.layout(left, bottom - qsbHeight, right, bottom);
            return;
        } else {
            bottomOffset = dp.getHotseatProfile().getBarBottomSpacePx();
            subtraction = dp.getHotseatProfile().getQsbShadowHeight();
        }
        int bottom = height - (bottomOffset - subtraction);
        mQsb.layout(left, bottom - qsbHeight, right, bottom);
    }

    /**
     * Sets the alpha value of the specified alpha channel of just our ShortcutAndWidgetContainer.
     */
    public void setIconsAlpha(float alpha, @HotseatQsbAlphaId int channelId) {
        getIconsAlpha(channelId).setValue(alpha);
    }

    /**
     * Sets the alpha value of just our QSB.
     */
    public void setQsbAlpha(float alpha, @HotseatQsbAlphaId int channelId) {
        MultiProperty prop = getQsbAlpha(channelId);
        if (prop != null) {
            prop.setValue(alpha);
        }
    }

    /** Returns the alpha channel for ShortcutAndWidgetContainer */
    public MultiProperty getIconsAlpha(@HotseatQsbAlphaId int channelId) {
        return mIconsAlphaChannels.get(channelId);
    }

    /** Returns the alpha channel for Qsb, or null if QSB is not present */
    @Nullable
    public MultiProperty getQsbAlpha(@HotseatQsbAlphaId int channelId) {
        return mQsbAlphaChannels != null ? mQsbAlphaChannels.get(channelId) : null;
    }

    /**
     * Returns the QSB inside hotseat, or null if QSB is not enabled.
     */
    @Nullable
    public View getQsb() {
        return mQsb;
    }

    @Nullable
    @Override
    public View mapOverItems(ItemOperator op) {
        if (mQsb != null && mQsb.getVisibility() != View.GONE
                && mQsb.getTag() instanceof ItemInfo info
                && op.evaluate(info, mQsb)) {
            return mQsb;
        }
        return super.mapOverItems(op);
    }

    /** Dumps the Hotseat internal state */
    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "Hotseat:");
        mIconsAlphaChannels.dump(
                prefix + "\t",
                writer,
                "mIconsAlphaChannels",
                "ALPHA_CHANNEL_TASKBAR_ALIGNMENT",
                "ALPHA_CHANNEL_PREVIEW_RENDERER",
                "ALPHA_CHANNEL_TASKBAR_STASH");
        if (mQsbAlphaChannels != null) {
            mQsbAlphaChannels.dump(
                    prefix + "\t",
                    writer,
                    "mQsbAlphaChannels",
                    "ALPHA_CHANNEL_TASKBAR_ALIGNMENT",
                    "ALPHA_CHANNEL_PREVIEW_RENDERER",
                    "ALPHA_CHANNEL_TASKBAR_STASH"
            );
        }
    }

    // TODO(b/479881252): Determine whether it still makes sense to disallow all instances of
    //  `SystemDragItemInfo` once `SystemDragController` is supported in all `ActivityContext`s. The
    //  current assumption is that `SystemDragItemInfo` indicates files dragged from another app.
    private boolean isSupportedDrag(DropTarget.DragObject d) {
        // Multi-cell folders belong on workspace pages, never in the one-row hotseat.
        return d.dragInfo.spanX == 1 && d.dragInfo.spanY == 1
                && !(HomeScreenFilesUtilsKt.isFileSystemItem(d.dragInfo)
                || d.dragInfo instanceof LauncherAppWidgetInfo
                || d.dragInfo instanceof PendingAddWidgetInfo
                || d.dragInfo instanceof SystemDragItemInfo);
    }

}
