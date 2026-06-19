package com.natkibe.videoplayerpro.player

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Circular audio visualizer used by fullscreen play-as-audio mode.
 */
class AudioEqualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barCount = 112
    private val phaseOffsets = FloatArray(barCount) { index ->
        ((index * 37) % barCount).toFloat() / barCount
    }
    private var phase = 0f
    private var animator: ValueAnimator? = null
    private var isAnimating = false
    private var wasAnimatingBeforeDetach = false
    private var sweepGradient: SweepGradient? = null

    private val cyan = Color.rgb(13, 184, 255)
    private val blue = Color.rgb(47, 128, 237)
    private val violet = Color.rgb(111, 74, 255)
    private val magenta = Color.rgb(177, 0, 255)

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(dp(10f), BlurMaskFilter.Blur.NORMAL)
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (wasAnimatingBeforeDetach) startAnimation()
    }

    override fun onDetachedFromWindow() {
        wasAnimatingBeforeDetach = isAnimating
        cancelAnimation()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w * 0.5f
        val cy = h * 0.5f
        sweepGradient = SweepGradient(
            cx,
            cy,
            intArrayOf(cyan, blue, violet, magenta, cyan),
            floatArrayOf(0f, 0.25f, 0.56f, 0.82f, 1f)
        ).apply {
            val matrix = Matrix()
            matrix.setRotate(-136f, cx, cy)
            setLocalMatrix(matrix)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = min(width, height).toFloat()
        if (size <= 0f) return

        val cx = width * 0.5f
        val cy = height * 0.5f
        val outerRadius = size * 0.455f
        val barInnerRadius = size * 0.287f
        val barBaseLength = size * 0.074f
        val barExtraLength = size * 0.072f

        fillPaint.color = Color.argb(178, 0, 0, 0)
        canvas.drawCircle(cx, cy, outerRadius + dp(8f), fillPaint)

        sweepGradient?.let { gradient ->
            glowPaint.shader = gradient
            glowPaint.strokeWidth = dp(8f)
            glowPaint.alpha = 120
            canvas.drawCircle(cx, cy, outerRadius, glowPaint)

            ringPaint.shader = gradient
            ringPaint.strokeWidth = dp(2.2f)
            ringPaint.alpha = 255
            canvas.drawCircle(cx, cy, outerRadius, ringPaint)
        }

        barPaint.strokeWidth = dp(4.0f)
        for (i in 0 until barCount) {
            val t = i.toFloat() / barCount
            val angleDeg = -90f + (360f * t)
            val angle = Math.toRadians(angleDeg.toDouble())
            val waveA = normalizedSin((phase + phaseOffsets[i]) * TWO_PI + i * 0.17f)
            val waveB = normalizedSin((phase * 1.7f + t * 2.2f) * TWO_PI)
            val tapered = 0.46f + 0.54f * waveA * (0.62f + 0.38f * waveB)
            val barLength = barBaseLength + barExtraLength * tapered
            val startR = barInnerRadius
            val endR = startR + barLength
            val startX = cx + cos(angle).toFloat() * startR
            val startY = cy + sin(angle).toFloat() * startR
            val endX = cx + cos(angle).toFloat() * endR
            val endY = cy + sin(angle).toFloat() * endR

            barPaint.shader = null
            barPaint.color = colorAt(t)
            barPaint.alpha = (178 + tapered * 77).toInt().coerceIn(178, 255)
            canvas.drawLine(startX, startY, endX, endY, barPaint)
        }

        fillPaint.color = Color.argb(96, 0, 0, 0)
        canvas.drawCircle(cx, cy, size * 0.245f, fillPaint)

        val dotAngle = Math.toRadians((-74f + 360f * phase).toDouble())
        val dotX = cx + cos(dotAngle).toFloat() * outerRadius
        val dotY = cy + sin(dotAngle).toFloat() * outerRadius
        fillPaint.shader = null
        fillPaint.color = magenta
        fillPaint.alpha = 130
        fillPaint.maskFilter = BlurMaskFilter(dp(12f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(dotX, dotY, dp(18f), fillPaint)
        fillPaint.maskFilter = null
        fillPaint.color = colorAt((phase + 0.18f) % 1f)
        fillPaint.alpha = 255
        canvas.drawCircle(dotX, dotY, dp(12f), fillPaint)
    }

    fun startAnimation() {
        if (isAnimating && animator != null) return
        cancelAnimation()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { valueAnimator ->
                phase = valueAnimator.animatedValue as Float
                invalidate()
            }
            start()
        }
        isAnimating = true
        wasAnimatingBeforeDetach = false
    }

    fun pauseAnimation() {
        if (!isAnimating) return
        animator?.pause()
        isAnimating = false
        wasAnimatingBeforeDetach = true
    }

    fun resumeAnimation() {
        if (isAnimating) return
        val activeAnimator = animator
        if (activeAnimator == null) {
            startAnimation()
        } else {
            activeAnimator.resume()
            isAnimating = true
            wasAnimatingBeforeDetach = false
        }
    }

    fun cancelAnimation() {
        animator?.cancel()
        animator = null
        isAnimating = false
        wasAnimatingBeforeDetach = false
        phase = 0.13f
        invalidate()
    }

    private fun normalizedSin(value: Float): Float {
        return ((sin(value.toDouble()) + 1.0) * 0.5).toFloat()
    }

    private fun colorAt(t: Float): Int {
        return when {
            t < 0.35f -> lerpColor(cyan, blue, t / 0.35f)
            t < 0.68f -> lerpColor(blue, violet, (t - 0.35f) / 0.33f)
            else -> lerpColor(violet, magenta, (t - 0.68f) / 0.32f)
        }
    }

    private fun lerpColor(start: Int, end: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val sr = Color.red(start)
        val sg = Color.green(start)
        val sb = Color.blue(start)
        val er = Color.red(end)
        val eg = Color.green(end)
        val eb = Color.blue(end)
        return Color.rgb(
            (sr + (er - sr) * f).toInt(),
            (sg + (eg - sg) * f).toInt(),
            (sb + (eb - sb) * f).toInt()
        )
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private companion object {
        private const val TWO_PI = 6.2831855f
    }
}
