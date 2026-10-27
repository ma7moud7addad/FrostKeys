// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

class RoundedKeyboardFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val clipPath = Path()
    private val outlineClipPath = Path()
    private val clipRect = RectF()
    private var lastWidth = -1
    private var lastHeight = -1
    private var lastRadiusPx = -1f

    private var cachedRadiusPx = -1f
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Settings.PREF_KEYBOARD_CORNER_RADIUS) {
            cachedRadiusPx = -1f
            invalidateOutline()
            postInvalidate()
        }
    }

    init {
        // Also clip via HWUI so hardware-layered descendants respect the rounded keyboard shape.
        // Android 13+ supports path outlines, which lets us keep only the top corners rounded.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val radiusPx = if (cachedRadiusPx >= 0f) cachedRadiusPx else keyboardCornerRadiusPx()
                    if (view.width <= 0 || view.height <= 0 || radiusPx <= 0f) {
                        outline.setRect(0, 0, view.width, view.height)
                        return
                    }
                    outlineClipPath.reset()
                    outlineClipPath.addRoundRect(
                        0f,
                        0f,
                        view.width.toFloat(),
                        view.height.toFloat(),
                        floatArrayOf(radiusPx, radiusPx, radiusPx, radiusPx, 0f, 0f, 0f, 0f),
                        Path.Direction.CW
                    )
                    outline.setPath(outlineClipPath)
                }
            }
            clipToOutline = true
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.prefs().registerOnSharedPreferenceChangeListener(prefListener)
        cachedRadiusPx = -1f
        invalidateOutline()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.prefs().unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Rebuild outline as soon as the IME view gets its real size.
        lastWidth = -1
        lastHeight = -1
        invalidateOutline()
        postInvalidateOnAnimation()
    }

    override fun draw(canvas: Canvas) {
        if (cachedRadiusPx < 0f) {
            cachedRadiusPx = keyboardCornerRadiusPx()
        }
        val radiusPx = cachedRadiusPx
        if (radiusPx <= 0f || width <= 0 || height <= 0) {
            super.draw(canvas)
            return
        }

        updateClipPath(radiusPx)
        val saveCount = canvas.save()
        canvas.clipPath(clipPath)
        super.draw(canvas)
        canvas.restoreToCount(saveCount)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
    }
    private fun keyboardCornerRadiusPx(): Float {
        val radiusDp = Settings.readKeyboardCornerRadius(context.prefs()).coerceIn(
                Settings.KEYBOARD_CORNER_RADIUS_MIN_DP,
                Settings.KEYBOARD_CORNER_RADIUS_MAX_DP
            )
        return radiusDp * resources.displayMetrics.density
    }

    private fun updateClipPath(radiusPx: Float) {
        if (lastWidth == width && lastHeight == height && lastRadiusPx == radiusPx) {
            return
        }

        lastWidth = width
        lastHeight = height
        lastRadiusPx = radiusPx
        clipRect.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(
            clipRect,
            floatArrayOf(radiusPx, radiusPx, radiusPx, radiusPx, 0f, 0f, 0f, 0f),
            Path.Direction.CW
        )
    }

}
