package com.orbit.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.animation.ValueAnimator

/**
 * v1.7.0 — ORBIT LINE LOADER (user request: "round ring nahi chahiye, lambi line
 * hi chahiye magar usme koi orbit ka animation hona chahiye").
 *
 * Design:
 *   • full-width THIN line (3dp) — track subtle, fill Chrome-blue gradient
 *   • fill ke TIP par glowing comet dot (radial glow) = "orbit ka animation"
 *   • dot ke peeche 2 chhote trailing dots (comet trail) fade hote hue
 *   • progress 0 par bhi ek chhota pulsing stub dikhta hai (turant feedback —
 *     "finger tab hote hi" feel), 100 par WebViewClient hide kar deta hai
 *
 * API OrbitRingLoader jaisa hi: setProgress(0..100). activity_main.xml ring_wrap
 * ke andar child swap — MainActivity field type changed.
 */
class OrbitLineLoader : View {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    private var prog = 0

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8AB4F8.toInt() }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8AB4F8.toInt() }
    private val trail1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x668AB4F8 }
    private val trail2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x338AB4F8 }
    private val rect = RectF()

    // comet idle pulse — progress 0-5 par bhi line "zinda" dikhe (instant feel)
    private var pulse = 0f
    private var animator: ValueAnimator? = null

    init {
        val a = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulse = it.animatedValue as Float
                if (prog <= 4) invalidate()
            }
        }
        animator = a
        runCatching { a.start() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        runCatching { animator?.start() }
    }

    override fun onDetachedFromWindow() {
        runCatching { animator?.cancel() }
        super.onDetachedFromWindow()
    }

    fun setProgress(p: Int) {
        val v = p.coerceIn(0, 100)
        if (v != prog) {
            prog = v
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        val cy = h / 2f
        val lineH = h * 0.62f

        // track — full width subtle
        rect.set(0f, cy - lineH / 2f, w, cy + lineH / 2f)
        canvas.drawRoundRect(rect, lineH / 2f, lineH / 2f, track)

        // fill — gradient blue -> light, width = progress
        val frac = prog / 100f
        val fillW = (w * frac).coerceAtLeast(if (prog <= 4) w * (0.06f + 0.05f * pulse) else w * 0.06f)
        fill.shader = LinearGradient(
            0f, 0f, fillW, 0f,
            0xFF4A7DE0.toInt(), 0xFFB9D2FF.toInt(), Shader.TileMode.CLAMP,
        )
        rect.set(0f, cy - lineH / 2f, fillW, cy + lineH / 2f)
        canvas.drawRoundRect(rect, lineH / 2f, lineH / 2f, fill)

        // comet trail — 2 fading dots behind the tip (orbit animation feel)
        val spacing = w * 0.035f
        canvas.drawCircle((fillW - spacing * 1.6f).coerceAtLeast(0f), cy, lineH * 0.55f, trail2)
        canvas.drawCircle((fillW - spacing * 0.7f).coerceAtLeast(0f), cy, lineH * 0.8f, trail1)

        // tip glow + comet dot
        glow.shader = RadialGradient(
            fillW, cy, lineH * 3.2f,
            intArrayOf(0x88B9D2FF.toInt(), 0x22B9D2FF.toInt(), 0x00B9D2FF.toInt()),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(fillW, cy, lineH * 3.2f, glow)
        val dotPaint = Paint(glow).apply { shader = null; color = 0xFFE8F1FF.toInt() }
        canvas.drawCircle(fillW, cy, lineH * 1.05f, dotPaint)
    }
}
