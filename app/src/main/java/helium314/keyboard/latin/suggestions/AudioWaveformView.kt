/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.suggestions

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import kotlin.math.max
import kotlin.math.min

/** Small, level-driven waveform for the in-keyboard voice status row. */
class AudioWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var audioLevel = 0f
    private var isListening = false
    private var pulse = 0f
    private val barShape = floatArrayOf(0.28f, 0.52f, 0.78f, 1f, 0.78f, 0.52f, 0.28f)
    private val barRect = RectF()
    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 800L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulse = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        contentDescription = context.getString(R.string.voice_input_waveform_description)
    }

    fun setAudioLevel(level: Float) {
        audioLevel = level.coerceIn(0f, 1f)
        invalidate()
    }

    fun setListening(listening: Boolean) {
        if (isListening == listening) return
        isListening = listening
        if (listening && isAttachedToWindow) {
            pulseAnimator.start()
        } else {
            pulseAnimator.cancel()
            pulse = 0f
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isListening && !pulseAnimator.isStarted) pulseAnimator.start()
    }

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val colors = runCatching { Settings.getValues().mColors }.getOrNull()
        paint.color = colors?.get(ColorType.KEY_TEXT) ?: 0xff607d8b.toInt()
        val count = barShape.size
        val gap = width * 0.045f
        val barWidth = min(width / (count * 2.6f), 3.5f * resources.displayMetrics.density)
        val totalWidth = count * barWidth + (count - 1) * gap
        val startX = (width - totalWidth) / 2f
        val centerY = height / 2f
        val maxHeight = max(4f * resources.displayMetrics.density, height * 0.82f)
        val pulseAmount = if (isListening) pulse * 0.12f else 0f

        for (i in 0 until count) {
            val liveHeight = audioLevel * barShape[i] * 0.72f
            val idleHeight = if (isListening) pulseAmount * barShape[count / 2] else 0f
            val barHeight = max(3f * resources.displayMetrics.density,
                maxHeight * min(1f, 0.12f + liveHeight + idleHeight))
            val left = startX + i * (barWidth + gap)
            barRect.set(left, centerY - barHeight / 2f, left + barWidth, centerY + barHeight / 2f)
            val radius = barWidth / 2f
            canvas.drawRoundRect(barRect, radius, radius, paint)
        }
    }
}
