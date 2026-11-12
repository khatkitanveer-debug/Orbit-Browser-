package com.orbit.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * v1.4.1 — Orbit ring loader: header ki blue loading LINE ki jagah Orbit-brand
 * ring loader. Ring page-load progress ke saath AAGE BADHTA hai:
 *   • arc sweep 0..360 = progress 0..100
 *   • satellite dot ring par orbit karta hai (Orbit logo wala motif)
 * 100 par WebViewClient se hide hota hai. Blue accent wahi Chrome-blue
 * (#8AB4F8) — theme untouched.
 */
class OrbitRingLoader : View {

    // XML inflation ke liye (Context, AttributeSet) constructor ZARURI hai —
    // iske bina activity_main.xml se inflate par crash hota (CI-safe).
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    private var prog = 0

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF3C4043.toInt()
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF8AB4F8.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8EAED.toInt()
    }
    private val rect = RectF()

    fun setProgress(p: Int) {
        val v = p.coerceIn(0, 100)
        if (v != prog) {
            prog = v
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val stroke = width * 0.11f
        val r = (minOf(width, height) / 2f) - stroke
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        canvas.drawCircle(cx, cy, r, track)
        val sweep = prog / 100f * 360f
        rect.set(cx - r, cy - r, cx + r, cy + r)
        if (sweep > 2f) {
            canvas.drawArc(rect, -90f, sweep, false, arc)
            // satellite dot — Orbit logo jaisa, progress ke saath ring par orbit
            val a = Math.toRadians((sweep - 90f).toDouble())
            canvas.drawCircle(
                cx + (r * Math.cos(a)).toFloat(),
                cy + (r * Math.sin(a)).toFloat(),
                stroke * 0.8f,
                dot,
            )
        }
    }
}
