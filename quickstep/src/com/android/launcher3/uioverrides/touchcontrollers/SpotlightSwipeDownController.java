/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.uioverrides.touchcontrollers;

import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;

import static com.android.launcher3.MotionEventsUtils.isTrackpadMultiFingerSwipe;

import android.graphics.PointF;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherState;
import com.android.launcher3.util.TouchController;

/**
 * Opens Spotlight when home is swiped down, as on iOS, if the user turned it on. A swipe that
 * starts at the top edge still pulls down the notification shade.
 */
public class SpotlightSwipeDownController implements TouchController {

    private static final float TOP_EDGE_DP = 48;

    private final Launcher mLauncher;
    private final float mTouchSlop;
    private final PointF mDownEvent = new PointF();
    private boolean mCanIntercept;

    public SpotlightSwipeDownController(Launcher launcher) {
        mLauncher = launcher;
        mTouchSlop = ViewConfiguration.get(launcher).getScaledTouchSlop();
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == ACTION_DOWN) {
            mCanIntercept = canInterceptTouch(ev);
            mDownEvent.set(ev.getX(), ev.getY());
            return false;
        }
        if (!mCanIntercept || action != ACTION_MOVE) {
            return false;
        }
        float dy = ev.getY() - mDownEvent.y;
        float dx = ev.getX() - mDownEvent.x;
        if (dy > mTouchSlop && dy > Math.abs(dx)) {
            mCanIntercept = false;
            View anchor = mLauncher.getHotseat().getQsb();
            mLauncher.openSearchFromPill(anchor != null ? anchor : mLauncher.getHotseat());
            return true;
        }
        if (Math.abs(dx) > mTouchSlop || -dy > mTouchSlop) {
            mCanIntercept = false;
        }
        return false;
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        // Spotlight takes over from here; swallow the rest of the gesture.
        return true;
    }

    private boolean canInterceptTouch(MotionEvent ev) {
        if (!LauncherPrefs.get(mLauncher).get(LauncherPrefs.SWIPE_DOWN_SPOTLIGHT)
                || !LauncherPrefs.get(mLauncher).get(LauncherPrefs.UNIVERSAL_SEARCH)
                || !mLauncher.isInState(LauncherState.NORMAL)
                || isTrackpadMultiFingerSwipe(ev)
                || ev.getSource() == InputDevice.SOURCE_MOUSE) {
            return false;
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return false;
        }
        float density = mLauncher.getResources().getDisplayMetrics().density;
        return ev.getY() > mLauncher.getDeviceProfile().getInsets().top + TOP_EDGE_DP * density
                && ev.getY() < mLauncher.getDragLayer().getHeight()
                        - mLauncher.getDeviceProfile().getInsets().bottom;
    }
}
