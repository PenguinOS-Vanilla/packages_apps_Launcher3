/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.quickstep.util;

import static com.android.launcher3.views.FloatingIconView.CLOSE_ICON_FADE_END;
import static com.android.launcher3.views.FloatingIconView.CLOSE_ICON_FADE_START;
import static com.android.launcher3.views.FloatingIconView.CLOSE_WINDOW_FADE_END;
import static com.android.launcher3.views.FloatingIconView.CLOSE_WINDOW_FADE_START;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;
import static com.android.quickstep.util.FloatingIconViewHelper.getFloatingIconView;

import android.graphics.RectF;
import android.view.View;
import com.android.app.animation.Interpolators;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.views.ClipIconView;
import com.android.launcher3.views.FloatingIconView;

/** Icon morph/fade and workspace reveal used by both ways of closing an app to Launcher. */
public final class HomeIconAnimation {
    private final QuickstepLauncher mLauncher;
    private final View mTargetView;
    private final RectF mIconLocation = new RectF();
    private RectF mTargetRect;
    private final FloatingIconView mIcon;
    private RectFSpringAnim mSpring;

    public HomeIconAnimation(QuickstepLauncher launcher, View targetView) {
        mLauncher = launcher;
        mTargetView = targetView;
        mIcon = getFloatingIconView(launcher, targetView, null,
                launcher.getTaskbarInteractor() == null ? null
                        : launcher.getTaskbarInteractor().findMatchingAsyncView(targetView),
                true /* hideOriginal */, mIconLocation, false /* isOpening */);
    }

    public RectF getTargetRect() {
        if (mTargetRect == null) mTargetRect = new RectF(mIconLocation);
        return mTargetRect;
    }
    public boolean isInHotseat() {
        return mTargetView.getTag() instanceof ItemInfo item && item.isInHotseat();
    }
    public void setAnimation(RectFSpringAnim spring) {
        mSpring = spring;
        spring.addAnimatorListener(mIcon);
        mIcon.setOnTargetChangeListener(spring::onTargetPositionChanged);
        // With ColorOS motion a touch must not cut the flight short: an app tapped meanwhile
        // launches while the closing window keeps flying into its icon.
        mIcon.setFinishOnTouchDown(!QuickstepTransitionManager.cosExperience(mLauncher));
        // Explicit cancellation still ends the spring before its view is recycled.
        mIcon.setFastFinishRunnable(spring::end);
    }
    public float getWindowAlpha(float progress) {
        return mSpring != null && mSpring.isCos() ? 1 - mSpring.getCosFadeProgress()
                : 1 - Interpolators.clampToProgress(
                        progress, CLOSE_WINDOW_FADE_START, CLOSE_WINDOW_FADE_END);
    }
    public void update(RectF rect, float progress, float radius, int overlayAlpha) {
        float iconAlpha = mSpring != null && mSpring.isCos() ? mSpring.getCosFadeProgress()
                : Interpolators.clampToProgress(progress, CLOSE_ICON_FADE_START, CLOSE_ICON_FADE_END);
        mIcon.update(iconAlpha, rect, progress, 1f - SHAPE_PROGRESS_DURATION,
                radius, false, overlayAlpha);
    }
    public boolean isAnimationReady() { return mIcon.isLaidOut(); }
    public void setTaskViewArtist(ClipIconView.TaskViewArtist artist) { mIcon.setOverlayArtist(artist); }
    public void cancel() { mIcon.fastFinish(); }
    public ScalingWorkspaceRevealAnim createReveal(boolean homeAlreadyVisible) {
        return new ScalingWorkspaceRevealAnim(mLauncher, mSpring, getTargetRect(),
                false /* playAlphaReveal */, true /* playBlur */, isInHotseat(),
                true /* fadeInHome */, homeAlreadyVisible);
    }
}
