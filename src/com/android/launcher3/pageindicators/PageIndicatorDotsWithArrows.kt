/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.pageindicators

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.android.launcher3.Insettable
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.PenguinDockGlass

class PageIndicatorDotsWithArrows
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr), PageIndicator, Insettable {

    private var leftArrow: PaginationArrow
    private var rightArrow: PaginationArrow
    private var pageIndicator: PageIndicator
    private var pageIndicatorContentContainer: LinearLayout
    private var markersCount: Int = 0
    private var activePage: Int = 0

    /** Where the home Search pill sits, as its centre's distance from the bottom; -1 if none. */
    private var searchPillCentreFromBottom = -1
    private var searchPillReveal: ((Float) -> Unit)? = null
    private val stockBackground: Drawable?
    private val searchPillGlass: PenguinDockGlass
    private val stockPadding = Rect()

    init {
        LayoutInflater.from(context).inflate(R.layout.page_indicator_dots_with_arrows, this, true)
        pageIndicatorContentContainer = findViewById(R.id.page_indicator_content_container)
        leftArrow = pageIndicatorContentContainer.findViewById(R.id.left_indicator_arrow)
        rightArrow = pageIndicatorContentContainer.findViewById(R.id.right_indicator_arrow)
        pageIndicator = pageIndicatorContentContainer.findViewById(R.id.page_indicator_dots)
        stockBackground = pageIndicatorContentContainer.background
        pageIndicatorContentContainer.run {
            stockPadding.set(paddingLeft, paddingTop, paddingRight, paddingBottom)
        }
        searchPillGlass = PenguinDockGlass(pageIndicatorContentContainer).apply { setCapsule(true) }
    }

    val isSearchPillMode: Boolean
        get() = searchPillReveal != null

    /**
     * Shares the home Search pill's spot: while the dots are hidden the Search pill shows, and
     * while pages scroll the dots fade in on the same glass in its place. [reveal] receives how
     * far the dots are shown, from 0 to 1. Pass null to go back to the stock indicator.
     */
    fun setSearchPill(centreFromBottomPx: Int, reveal: ((Float) -> Unit)?) {
        val wasPillMode = isSearchPillMode
        searchPillCentreFromBottom = if (reveal == null) -1 else centreFromBottomPx
        searchPillReveal = reveal
        val dots = pageIndicator as? PageIndicatorDots
        if (reveal == null) {
            dots?.setRevealListener(null)
            pageIndicatorContentContainer.background = stockBackground
            pageIndicatorContentContainer.alpha = 1f
            pageIndicatorContentContainer.minimumHeight = 0
            pageIndicatorContentContainer.setPadding(
                stockPadding.left, stockPadding.top, stockPadding.right, stockPadding.bottom)
        } else {
            val padH = resources.getDimensionPixelSize(R.dimen.home_search_pill_dots_padding)
            pageIndicatorContentContainer.background = searchPillGlass
            pageIndicatorContentContainer.minimumHeight =
                resources.getDimensionPixelSize(R.dimen.home_search_pill_height)
            pageIndicatorContentContainer.setPadding(padH, 0, padH, 0)
            dots?.setRevealListener { onDotsReveal() }
            // Scrolling reveals the dots and they always settle back to the Search pill.
            if (!wasPillMode) pageIndicator.setShouldAutoHide(true)
            onDotsReveal()
        }
        updateArrowVisibility()
        updateBackgroundVisibility()
        applySearchPillLayout()
    }

    /** Centres the indicator on the Search pill; returns false when not in Search pill mode. */
    fun applySearchPillLayout(): Boolean {
        if (searchPillCentreFromBottom < 0) return false
        val lp = layoutParams as? FrameLayout.LayoutParams ?: return false
        val height =
            maxOf(
                resources.getDimensionPixelSize(R.dimen.workspace_page_indicator_height),
                resources.getDimensionPixelSize(R.dimen.home_search_pill_height),
            )
        val margin = searchPillCentreFromBottom - height / 2
        if (lp.height != height || lp.bottomMargin != margin) {
            lp.height = height
            lp.bottomMargin = margin
            layoutParams = lp
        }
        return true
    }

    private fun onDotsReveal() {
        val reveal = (pageIndicator as? PageIndicatorDots)?.reveal ?: 0f
        pageIndicatorContentContainer.alpha = reveal
        val scale = 0.9f + 0.1f * reveal
        pageIndicatorContentContainer.scaleX = scale
        pageIndicatorContentContainer.scaleY = scale
        searchPillReveal?.invoke(reveal)
    }

    override fun setScroll(currentScroll: Int, totalScroll: Int) =
        pageIndicator.setScroll(currentScroll, totalScroll)

    override fun setActiveMarker(activePage: Int) {
        pageIndicator.setActiveMarker(activePage)
        this.activePage = activePage
        updateArrowVisibility()
    }

    override fun setMarkersCount(numMarkers: Int) {
        pageIndicator.setMarkersCount(numMarkers)
        markersCount = numMarkers
        updateArrowVisibility()
        updateBackgroundVisibility()
    }

    private fun updateArrowVisibility() {
        // if the markersCount is only 1 or less there is no reason to show the arrows
        setArrowsVisible(
            markersCount > 1 &&
                !isSearchPillMode &&
                Utilities.shouldEnableMouseInteractionChanges(context)
        )

        // This logic handles when the counter is on the corners when can't scroll anymore
        if (Utilities.shouldEnableMouseInteractionChanges(context)) {
            leftArrow.setAlpha(
                if (0 == activePage) PaginationArrow.DISABLED_ARROW_OPACITY
                else PaginationArrow.FULLY_OPAQUE
            )
            rightArrow.setAlpha(
                if (markersCount == activePage + 1) PaginationArrow.DISABLED_ARROW_OPACITY
                else PaginationArrow.FULLY_OPAQUE
            )
        }
    }

    fun setOnPrevArrowClickedListener(l: OnClickListener?) = leftArrow.setOnClickListener(l)

    fun setOnNextArrowClickedListener(l: OnClickListener?) = rightArrow.setOnClickListener(l)

    private fun setArrowsVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        leftArrow.visibility = visibility
        rightArrow.visibility = visibility
    }

    private fun updateBackgroundVisibility() {
        // The Search pill glass fades with the container instead.
        if (isSearchPillMode) return
        pageIndicatorContentContainer.background.alpha =
            if (markersCount > 1 && Utilities.shouldEnableMouseInteractionChanges(context)) 255
            else 0
    }

    /**
     * We need to override setInsets to prevent InsettableFrameLayout from applying different
     * margins on the pagination.
     */
    override fun setInsets(insets: Rect?) {}

    override fun setPauseScroll(pause: Boolean, isTwoPanels: Boolean) =
        pageIndicator.setPauseScroll(pause, isTwoPanels)

    override fun setShouldAutoHide(shouldAutoHide: Boolean) =
        pageIndicator.setShouldAutoHide(shouldAutoHide)

    override fun pauseAnimations() = pageIndicator.pauseAnimations()

    override fun skipAnimationsToEnd() = pageIndicator.skipAnimationsToEnd()

    override fun setPaintColor(color: Int) = pageIndicator.setPaintColor(color)
}
