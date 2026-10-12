/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.util

import android.content.Context
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Penguin Laboratory's Liquid Glass. SurfaceFlinger bends the wallpaper under the launcher's
 * blurred surfaces; this bends the icons and widgets the launcher draws under them itself, and
 * lights their rims.
 */
object LiquidGlass {
    private const val SETTING = "penguin_liquid_glass"

    @Volatile private var enabled: Boolean? = null

    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        enabled
            ?: synchronized(this) {
                enabled
                    ?: run {
                        val resolver = context.applicationContext.contentResolver
                        val read = { Settings.Secure.getInt(resolver, SETTING, 0) != 0 }
                        resolver.registerContentObserver(
                            Settings.Secure.getUriFor(SETTING),
                            false,
                            object : ContentObserver(Handler(Looper.getMainLooper())) {
                                override fun onChange(selfChange: Boolean) {
                                    enabled = read()
                                }
                            },
                        )
                        read().also { enabled = it }
                    }
            }

    private const val LENS =
        """
        uniform shader content;
        uniform float4 bounds;
        uniform float radius;
        uniform float band;
        uniform float refraction;

        float roundRect(float2 p, float2 c, float2 h, float r) {
            float2 q = abs(p - c) - (h - r);
            return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
        }

        half4 main(float2 xy) {
            float2 c = (bounds.xy + bounds.zw) * 0.5;
            float2 h = (bounds.zw - bounds.xy) * 0.5;
            float r = min(radius, min(h.x, h.y));
            float d = roundRect(xy, c, h, r);
            if (d > 0.0) return content.eval(xy);
            float e = 0.75;
            float2 g = float2(
                roundRect(xy + float2(e, 0.0), c, h, r) - roundRect(xy - float2(e, 0.0), c, h, r),
                roundRect(xy + float2(0.0, e), c, h, r) - roundRect(xy - float2(0.0, e), c, h, r));
            float2 n = g / max(length(g), 0.0001);
            float t = clamp(1.0 + d / band, 0.0, 1.0);
            return content.eval(xy - n * refraction * t * t * t);
        }
        """

    private val lens by lazy { RuntimeShader(LENS) }

    /**
     * [blur] followed by the lens of a glass slab over [bounds], in the coordinates of the node the
     * effect is set on.
     */
    @JvmStatic
    fun lensEffect(blur: RenderEffect, bounds: RectF, radius: Float, density: Float): RenderEffect {
        val half = minOf(bounds.width(), bounds.height()) * 0.5f
        if (half < 1f) return blur
        // At least 14dp of curve, but never past the middle of a small slab.
        val band = maxOf(radius * 1.1f, 14f * density).coerceAtMost(half)
        lens.setFloatUniform("bounds", bounds.left, bounds.top, bounds.right, bounds.bottom)
        lens.setFloatUniform("radius", radius)
        lens.setFloatUniform("band", band)
        lens.setFloatUniform("refraction", band * 0.75f)
        return RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(lens, "content"),
            blur,
        )
    }

    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPath = Path()

    /**
     * Light catching a glass slab's rim, brightest at the top-left and bottom-right, and a faint
     * sheen across its top. [shape] is the slab's outline, or null for a rounded rect.
     */
    @JvmStatic
    fun drawRim(canvas: Canvas, bounds: RectF, radius: Float, shape: Path?, density: Float, alpha: Float) {
        if (bounds.isEmpty || alpha <= 0f) return
        val path =
            shape
                ?: rimPath.apply {
                    reset()
                    addRoundRect(bounds, radius, radius, Path.Direction.CW)
                }
        val a = alpha.coerceIn(0f, 1f)
        val key = RimKey(bounds.left, bounds.top, bounds.right, bounds.bottom, a)
        val shaders = rimShaders.get(key) ?: newRimShaders(bounds, a).also { rimShaders.put(key, it) }
        rimPaint.strokeWidth = 1.4f * density
        rimPaint.shader = shaders.rim
        sheenPaint.shader = shaders.sheen
        val count = canvas.save()
        canvas.clipPath(path)
        // Centred on the edge, so only the inner half of the stroke shows.
        canvas.drawPath(path, rimPaint)
        canvas.drawRect(bounds, sheenPaint)
        canvas.restoreToCount(count)
    }

    private data class RimKey(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val alpha: Float,
    )

    private class RimShaders(val rim: Shader, val sheen: Shader)

    private val rimShaders = android.util.LruCache<RimKey, RimShaders>(16)

    private fun newRimShaders(bounds: RectF, a: Float) =
        RimShaders(
            // A hairline, lit only where the curve faces the light (top-left) and catches its
            // bounce (bottom-right), fading out along the straight sides, as iOS's glass edges are.
            LinearGradient(
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                intArrayOf(white(0.55f * a), white(0.08f * a), 0, white(0.05f * a), white(0.30f * a)),
                floatArrayOf(0f, 0.22f, 0.5f, 0.78f, 1f),
                Shader.TileMode.CLAMP,
            ),
            LinearGradient(
                0f,
                bounds.top,
                0f,
                bounds.top + bounds.height() * 0.3f,
                white(0.05f * a),
                0,
                Shader.TileMode.CLAMP,
            ),
        )

    private fun white(alpha: Float) = (Math.round(alpha.coerceIn(0f, 1f) * 255f) shl 24) or 0xFFFFFF
}
