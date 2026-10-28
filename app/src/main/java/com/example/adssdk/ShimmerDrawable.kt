package com.example.adssdk

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import java.util.WeakHashMap

/** Animated grey placeholder drawn over a view while an ad is loading. */
internal class ShimmerDrawable(private val radius: Float, private val drawBase: Boolean = true) : Drawable() {

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E6E6E6") }
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var progress = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidateSelf()
        }
    }

    fun start() {
        if (!animator.isStarted) animator.start()
    }

    fun stop() {
        animator.cancel()
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        rect.set(bounds)
        if (drawBase) canvas.drawRoundRect(rect, radius, radius, basePaint)

        val band = w * 0.6f
        val x = -band + (w + band) * progress
        shinePaint.shader = LinearGradient(
            x, 0f, x + band, 0f,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#F7F7F7"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, shinePaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * start(view, heightDp, layoutRes) while loading, stop(view) when the ad is shown or failed.
 * layoutRes = your own XML placeholder (0 = built-in grey box).
 */
internal object Shimmer {

    private class State(
        val drawable: ShimmerDrawable?,
        val oldMinHeight: Int,
        val oldForeground: Drawable?,
        val startedAt: Long = SystemClock.elapsedRealtime()
    ) {
        var overlay: View? = null
        var sweep: ShimmerDrawable? = null
        var layoutListener: View.OnLayoutChangeListener? = null
    }

    /** The shimmer is shown at least this long, even if the ad is ready sooner. */
    @Volatile
    var minDurationMs: Long = 3_000L

    private val states = WeakHashMap<View, State>()

    fun start(view: View, heightDp: Int, layoutRes: Int = 0) {
        stop(view)
        if (layoutRes == 0) {
            val d = ShimmerDrawable(dp(view.context, 10).toFloat())
            states[view] = State(d, view.minimumHeight, view.foreground)
            view.minimumHeight = dp(view.context, heightDp)
            view.foreground = d
            d.start()
            view.requestLayout()
        } else {
            val s = State(null, view.minimumHeight, view.foreground)
            states[view] = s
            if (view.width > 0) attachCustom(view, s, layoutRes)
            else {
                val l = object : View.OnLayoutChangeListener {
                    override fun onLayoutChange(
                        v: View, l: Int, t: Int, r: Int, b: Int,
                        ol: Int, ot: Int, or: Int, ob: Int
                    ) {
                        if (v.width > 0 && s.overlay == null && states[v] === s) {
                            v.removeOnLayoutChangeListener(this)
                            s.layoutListener = null
                            attachCustom(v, s, layoutRes)
                        }
                    }
                }
                s.layoutListener = l
                view.addOnLayoutChangeListener(l)
            }
        }
    }

    /** Inflates the user's XML placeholder and draws it on top of the container. */
    private fun attachCustom(view: View, s: State, layoutRes: Int) {
        val group = view as? ViewGroup ?: return
        val ph = LayoutInflater.from(view.context).inflate(layoutRes, null, false)
        if (ph.background == null) ph.setBackgroundColor(Color.WHITE) // hides the ad underneath
        val w = view.width - view.paddingLeft - view.paddingRight
        ph.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val h = ph.measuredHeight
        ph.layout(view.paddingLeft, view.paddingTop, view.paddingLeft + w, view.paddingTop + h)
        val sweep = ShimmerDrawable(0f, drawBase = false)
        ph.foreground = sweep
        sweep.callback = ph
        group.overlay.add(ph)
        s.overlay = ph
        s.sweep = sweep
        view.minimumHeight = h + view.paddingTop + view.paddingBottom
        sweep.start()
        view.requestLayout()
    }

    /** Stops the shimmer, but not before [minDurationMs] has passed since it started. */
    fun stopAfterMin(view: View) {
        val s = states[view] ?: return
        val wait = minDurationMs - (SystemClock.elapsedRealtime() - s.startedAt)
        if (wait <= 0) {
            stop(view)
        } else {
            view.postDelayed({ if (states[view] === s) stop(view) }, wait)
        }
    }

    fun stop(view: View) {
        val s = states.remove(view) ?: return
        s.drawable?.stop()
        s.sweep?.stop()
        s.layoutListener?.let { view.removeOnLayoutChangeListener(it) }
        s.overlay?.let { (view as? ViewGroup)?.overlay?.remove(it) }
        view.foreground = s.oldForeground
        view.minimumHeight = s.oldMinHeight
        view.requestLayout()
    }
}