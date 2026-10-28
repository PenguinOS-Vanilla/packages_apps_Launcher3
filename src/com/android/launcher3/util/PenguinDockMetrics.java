/* Copyright (C) 2026 The PenguinOS Project. SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.util;

/**
 * Dock geometry shared by the home dock, the launcher's device profile and the taskbar.
 *
 * <p>The dock has no fixed icon count or width. Each icon takes its own size plus a gap that
 * follows the icon size setting, and the dock holds as many as fit across the screen.
 */
public final class PenguinDockMetrics {

    /** Pinned slots kept in the database, more than any screen shows at once. */
    public static final int MAX_PINNED = 20;
    /** Most recent apps the dock shows after its pinned apps. */
    public static final int MAX_RECENTS = 3;
    /** Pinned slots a narrow screen keeps before it shows any recent app. */
    private static final int MIN_PINNED_BEFORE_RECENTS = 5;

    private PenguinDockMetrics() { }

    /**
     * Whether the bottom dock lays out at its natural width. Hotseats sharing their row with
     * inline navigation buttons or an inline search bar, two-panel and vertical hotseats keep
     * the stock grid geometry. The profile, the home dock and the taskbar flight all use this.
     */
    public static boolean usesNaturalDock(com.android.launcher3.DeviceProfile dp) {
        var hotseat = dp.getHotseatProfile();
        // Phones keep the stock AOSP hotseat; the iPad dock is a large screen layout.
        return dp.getDeviceProperties().isLargeScreen()
                && !dp.isVerticalBarLayout()
                && !dp.getDeviceProperties().isTwoPanels()
                && !hotseat.getAreNavButtonsInline()
                && !hotseat.isQsbInline();
    }

    /** Space between two dock icons. */
    public static int gapPx(int iconSizePx) {
        return Math.round(iconSizePx * 0.25f);
    }

    /** Width of one dock slot: an icon and one gap. */
    public static int slotPx(int iconSizePx) {
        return iconSizePx + gapPx(iconSizePx);
    }

    /** Glass padding from the outer slot edges to the dock edge. */
    public static int glassPaddingPx(int iconSizePx) {
        return Math.round(iconSizePx * 0.17f);
    }

    /** Margin from the screen edge to the dock edge. */
    public static int screenMarginPx(int iconSizePx) {
        return Math.round(iconSizePx * 0.27f);
    }

    /** Width the dock slots can use on a screen of {@code widthPx}, without insets. */
    public static int usableWidthPx(int widthPx, int iconSizePx) {
        return Math.max(0, widthPx
                - 2 * (screenMarginPx(iconSizePx) + glassPaddingPx(iconSizePx)));
    }

    /** All slots, pinned and recent, that fit on a screen of {@code widthPx}. */
    public static int slotCapacity(int widthPx, int iconSizePx) {
        return Math.max(1, usableWidthPx(widthPx, iconSizePx) / Math.max(1, slotPx(iconSizePx)));
    }

    /** Slots left for recent apps once {@code capacity} slots fit. */
    public static int recentSlots(int capacity) {
        return Math.max(0, Math.min(MAX_RECENTS, capacity - MIN_PINNED_BEFORE_RECENTS));
    }

    /**
     * Slot width for {@code cells} slots on a screen of {@code widthPx}: the natural slot, or
     * less if a larger icon size setting no longer fits.
     */
    public static int fittedSlotPx(int widthPx, int iconSizePx, int cells) {
        int slot = slotPx(iconSizePx);
        int usable = usableWidthPx(widthPx, iconSizePx);
        return cells * slot > usable ? Math.max(1, usable / Math.max(1, cells)) : slot;
    }

    /**
     * Centre of dock slot {@code position} (pinned apps first, then recents) on a screen of
     * {@code widthPx}, with the dock centred.
     */
    public static float slotCenterX(int widthPx, int iconSizePx, int cells, float position) {
        int slot = fittedSlotPx(widthPx, iconSizePx, cells);
        return (widthPx - cells * slot) / 2f + slot * (position + 0.5f);
    }

    /** Pinned slots shown on a screen of {@code widthPx}, at most {@code maxPinned}. */
    public static int pinnedCapacity(int widthPx, int iconSizePx, int maxPinned) {
        int capacity = slotCapacity(widthPx, iconSizePx);
        return Math.max(1, Math.min(maxPinned, capacity - recentSlots(capacity)));
    }
}
