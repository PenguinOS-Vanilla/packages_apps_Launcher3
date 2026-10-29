/* Copyright (C) 2026 The PenguinOS Project. SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.util;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.WindowManager;
import com.android.internal.graphics.drawable.BackgroundBlurDrawable;

/** A live, bounded frost with a quiet rim; never blurs the icon artwork. */
public final class PenguinDockGlass extends Drawable {
    /** Shared dock material in renderengine BlurFilter; opt-in, independent of COS style. */
    public static final int MATERIAL = 11;
    private static final int DOCK_BLUR_DP = 6;
    private static final int SEARCH_BLUR_DP = 16;
    /** Close to the glass tone over a blurred backdrop. */
    private static final int FLAT_FILL = 0x2E000000;
    private static final int FALLBACK_FILL = 0x50000000;
    private final View owner;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private BackgroundBlurDrawable blur;
    private int alpha = 255;
    private float flatProgress;
    private final boolean searchPanel;
    private boolean capsule;
    public PenguinDockGlass(View owner) { this(owner, false); }
    public PenguinDockGlass(View owner, boolean searchPanel) {
        this.owner = owner;
        this.searchPanel = searchPanel;
    }
    @Override public void draw(Canvas canvas) {
        if (getBounds().isEmpty()) return;
        float density = owner.getResources().getDisplayMetrics().density;
        float radius = radius(density);
        WindowManager wm = owner.getContext().getSystemService(WindowManager.class);
        boolean enabled = wm != null && wm.isCrossWindowBlurEnabled();
        float live = 1f - flatProgress;
        if (enabled && live > 0f && owner.getViewRootImpl() != null) {
            if (blur == null) blur = owner.getViewRootImpl().createBackgroundBlurDrawable();
            // Spotlight is a large reading panel, so it needs a stronger frost than the dock.
            blur.setBlurRadius(Math.round((searchPanel ? SEARCH_BLUR_DP : DOCK_BLUR_DP)
                    * density));
            blur.setCornerRadius(radius);
            blur.setMaterial(MATERIAL);
            blur.setColor(0);
            blur.setBounds(getBounds());
            blur.setAlpha(Math.round(alpha * live));
            blur.draw(canvas);
        }
        rect.set(getBounds());
        paint.setStyle(Paint.Style.FILL);
        // One shared home-dock material and radius for every glass surface. Without a live
        // blur, a plain fill stands in for the glass.
        boolean noGlass = !enabled || (blur == null && live > 0f);
        int fill = noGlass ? FALLBACK_FILL : FLAT_FILL;
        float fillAlpha = noGlass ? 1f : flatProgress;
        paint.setColor(fill);
        paint.setAlpha(Math.round(colorAlpha(fill, alpha) * fillAlpha));
        canvas.drawRoundRect(rect, radius, radius, paint);
        rect.inset(density * 0.25f, density * 0.25f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density * 0.5f);
        paint.setColor(0x33FFFFFF);
        paint.setAlpha(colorAlpha(0x33FFFFFF, alpha));
        canvas.drawRoundRect(rect, radius, radius, paint);
    }
    /** Rounds the short sides fully, as iOS draws its small home pills. */
    public void setCapsule(boolean capsule) {
        if (this.capsule == capsule) return;
        this.capsule = capsule;
        invalidateSelf();
    }

    private float radius(float density) {
        return capsule ? getBounds().height() / 2f
                : Math.min(28 * density, getBounds().height() * 0.4f);
    }

    private static int colorAlpha(int color, int alpha) { return (color >>> 24) * alpha / 255; }

    /**
     * Replaces the live glass with a plain fill, from 0 (live) to 1 (plain). Use it while the
     * owner is blurred as a view: the compositor blur behind it would stay sharp-edged.
     */
    public void setFlatProgress(float progress) {
        progress = Math.max(0f, Math.min(1f, progress));
        if (flatProgress == progress) return;
        flatProgress = progress;
        invalidateSelf();
        owner.invalidate();
    }
    @Override public void getOutline(android.graphics.Outline outline) {
        float density = owner.getResources().getDisplayMetrics().density;
        float radius = radius(density);
        outline.setRoundRect(getBounds(), radius);
        outline.setAlpha(alpha / 255f);
    }
    @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
