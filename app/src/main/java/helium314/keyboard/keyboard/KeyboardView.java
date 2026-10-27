/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard;

import static helium314.keyboard.keyboard.KeyboardTheme.STYLE_ROUNDED;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.os.Trace;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import helium314.keyboard.keyboard.emoji.EmojiPageKeyboardView;
import helium314.keyboard.keyboard.internal.KeyDrawParams;
import helium314.keyboard.keyboard.internal.KeyVisualAttributes;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.common.ColorType;
import helium314.keyboard.latin.common.Colors;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.common.KeyBackgroundUtils;
import helium314.keyboard.latin.common.StringUtilsKt;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.suggestions.MoreSuggestions;
import helium314.keyboard.latin.suggestions.MoreSuggestionsView;
import helium314.keyboard.latin.utils.TypefaceUtils;

import java.util.HashSet;

/** A view that renders a virtual {@link Keyboard}. */
// todo: this ThemeStyle-dependent stuff really should not be in here!
public class KeyboardView extends View {
    // XML attributes
    private final KeyVisualAttributes mKeyVisualAttributes;
    // Default keyLabelFlags from {@link KeyboardTheme}.
    // Currently only "alignHintLabelToBottom" is supported.
    private final int mDefaultKeyLabelFlags;
    private final float mKeyHintLetterPadding;
    private final String mKeyPopupHintLetter;
    private final float mKeyPopupHintLetterPadding;
    private final float mKeyShiftedLetterHintPadding;
    private final float mKeyTextShadowRadius;
    private final float mVerticalCorrection;
    private final Drawable mKeyBackground;
    private final Drawable mFunctionalKeyBackground;
    private final Drawable mActionKeyBackground;
    private final Drawable mSpacebarBackground;
    private final float mSpacebarIconWidthRatio;
    private final Rect mKeyBackgroundPadding = new Rect();
    private static final float KET_TEXT_SHADOW_RADIUS_DISABLED = -1.0f;
    private Colors mColors;
    private float mKeyScaleForText;
    protected float mFontSizeMultiplier;

    // The maximum key label width in the proportion to the key width.
    private static final float MAX_LABEL_RATIO = 0.90f;

    // Main keyboard
    // TODO: Consider having a dummy keyboard object to make this @NonNull
    @Nullable
    private Keyboard mKeyboard;
    @NonNull
    private final KeyDrawParams mKeyDrawParams = new KeyDrawParams();

    // Drawing
    /** True if all keys should be drawn */
    private boolean mInvalidateAllKeys;
    /** The keys that should be drawn */
    private final HashSet<Key> mInvalidatedKeys = new HashSet<>();
    /** The working rectangle for clipping */
    private final Rect mClipRect = new Rect();
    /** The keyboard bitmap buffer for faster updates */
    private Bitmap mOffscreenBuffer;
    /** Flag for whether the key hints should be displayed */
    private boolean mShowsHints;
    /**
     * Scale for downscaling icons and fixed size backgrounds if keyboard height is
     * set below 80%
     */
    private float mIconScaleFactor;
    /** The canvas for the above mutable keyboard bitmap */
    @NonNull
    private final Canvas mOffscreenCanvas = new Canvas();
    @NonNull
    private final Paint mPaint = new Paint();
    @NonNull
    private final Paint mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics mFontMetrics = new Paint.FontMetrics();
    private final Rect mEmojiLabelBounds = new Rect();

    public KeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public KeyboardView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        mColors = Settings.getValues().mColors;

        final TypedArray keyboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.KeyboardView, defStyle, R.style.KeyboardView);
        if (this instanceof MoreSuggestionsView)
            mKeyBackground = mColors.selectAndColorDrawable(keyboardViewAttr,
                    ColorType.MORE_SUGGESTIONS_WORD_BACKGROUND);
        else if (isPopupKeysView())
            mKeyBackground = mColors.selectAndColorDrawable(keyboardViewAttr, ColorType.KEY_PREVIEW_BACKGROUND);
        else
            mKeyBackground = mColors.selectAndColorDrawable(keyboardViewAttr, ColorType.KEY_BACKGROUND);
        mKeyBackground.getPadding(mKeyBackgroundPadding);
        mFunctionalKeyBackground = mColors.selectAndColorDrawable(keyboardViewAttr,
                ColorType.FUNCTIONAL_KEY_BACKGROUND);
        mSpacebarBackground = mColors.selectAndColorDrawable(keyboardViewAttr, ColorType.SPACE_BAR_BACKGROUND);
        if (isPopupKeysView())
            mActionKeyBackground = mColors.selectAndColorDrawable(keyboardViewAttr,
                    ColorType.ACTION_KEY_BACKGROUND);
        else if (this instanceof PopupKeysKeyboardView)
            mActionKeyBackground = mColors.selectAndColorDrawable(keyboardViewAttr,
                    ColorType.ACTION_KEY_POPUP_KEYS_BACKGROUND);
        else
            mActionKeyBackground = mColors.selectAndColorDrawable(keyboardViewAttr, ColorType.ACTION_KEY_BACKGROUND);

        mSpacebarIconWidthRatio = keyboardViewAttr.getFloat(
                R.styleable.KeyboardView_spacebarIconWidthRatio, 1.0f);
        mKeyHintLetterPadding = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_keyHintLetterPadding, 0.0f);
        mKeyPopupHintLetter = Settings.getValues().mShowsPopupHints
                ? keyboardViewAttr.getString(R.styleable.KeyboardView_keyPopupHintLetter)
                : "";
        mKeyPopupHintLetterPadding = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_keyPopupHintLetterPadding, 0.0f);
        mKeyShiftedLetterHintPadding = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_keyShiftedLetterHintPadding, 0.0f);
        mKeyTextShadowRadius = keyboardViewAttr.getFloat(
                R.styleable.KeyboardView_keyTextShadowRadius, KET_TEXT_SHADOW_RADIUS_DISABLED);
        mVerticalCorrection = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_verticalCorrection, 0.0f);
        keyboardViewAttr.recycle();

        final TypedArray keyAttr = context.obtainStyledAttributes(attrs,
                R.styleable.Keyboard_Key, defStyle, R.style.KeyboardView);
        mDefaultKeyLabelFlags = keyAttr.getInt(R.styleable.Keyboard_Key_keyLabelFlags, 0);
        mKeyVisualAttributes = KeyVisualAttributes.newInstance(keyAttr);
        keyAttr.recycle();

        mPaint.setAntiAlias(true);
    }

    @Nullable
    public KeyVisualAttributes getKeyVisualAttribute() {
        return mKeyVisualAttributes;
    }

    private static void blendAlpha(@NonNull final Paint paint, final int alpha) {
        final int color = paint.getColor();
        paint.setARGB((paint.getAlpha() * alpha) / Constants.Color.ALPHA_OPAQUE,
                Color.red(color), Color.green(color), Color.blue(color));
    }

    public void setHardwareAcceleratedDrawingEnabled(final boolean enabled) {
        if (!enabled)
            return;
        // TODO: Should use LAYER_TYPE_SOFTWARE when hardware acceleration is off?
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /**
     * Attaches a keyboard to this view. The keyboard can be switched at any time
     * and the
     * view will re-layout itself to accommodate the keyboard.
     *
     * @see Keyboard
     * @see #getKeyboard()
     * @param keyboard the keyboard to display in this view
     */
    public void setKeyboard(@NonNull final Keyboard keyboard) {
        setKeyboardInternal(keyboard, true /* requestLayout */);
    }

    protected void setKeyboardWithoutRequestLayout(@NonNull final Keyboard keyboard) {
        setKeyboardInternal(keyboard, false /* requestLayout */);
    }

    private void setKeyboardInternal(@NonNull final Keyboard keyboard, final boolean requestLayout) {
        Trace.beginSection(requestLayout ? "KeyboardView#setKeyboard" : "KeyboardView#setKeyboardNoLayout");
        try {
            if (keyboard instanceof MoreSuggestions) {
                mColors.setBackground(this, ColorType.MORE_SUGGESTIONS_BACKGROUND);
            } else if (keyboard instanceof PopupKeysKeyboard) {
                mColors.setBackground(this, ColorType.KEY_PREVIEW_BACKGROUND);
            } else {
                // actual background color/drawable is applied to main_keyboard_frame
                setBackgroundColor(Color.TRANSPARENT);
            }

            mKeyboard = keyboard;
            mKeyScaleForText = (float) Math.sqrt(1 / Settings.getValues().mKeyboardHeightScale);
            final int scaledKeyHeight = (int) ((keyboard.mMostCommonKeyHeight - keyboard.mVerticalGap)
                    * mKeyScaleForText);
            mKeyDrawParams.updateParams(scaledKeyHeight, mKeyVisualAttributes);
            mKeyDrawParams.updateParams(scaledKeyHeight, keyboard.mKeyVisualAttributes);
            invalidateAllKeys();
            if (requestLayout) {
                requestLayout();
            }
            mFontSizeMultiplier = mKeyboard.mId.isEmojiKeyboard()
                    // In the case of EmojiKeyFit, the size of emojis is taken care of by the size
                    // of the keys
                    ? (Settings.getValues().mEmojiKeyFit ? 1 : Settings.getValues().mFontSizeMultiplierEmoji)
                    : Settings.getValues().mFontSizeMultiplier;
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Returns the current keyboard being displayed by this view.
     *
     * @return the currently attached keyboard
     * @see #setKeyboard(Keyboard)
     */
    @Nullable
    public Keyboard getKeyboard() {
        return mKeyboard;
    }

    protected float getVerticalCorrection() {
        return mVerticalCorrection;
    }

    @NonNull
    protected KeyDrawParams getKeyDrawParams() {
        return mKeyDrawParams;
    }

    protected void updateKeyDrawParams(final int keyHeight) {
        mKeyDrawParams.updateParams(keyHeight, mKeyVisualAttributes);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        // The main keyboard expands to the entire this {@link KeyboardView}.
        final int width = keyboard.mOccupiedWidth + getPaddingLeft() + getPaddingRight();
        final int height = keyboard.mOccupiedHeight + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(@NonNull final Canvas canvas) {
        super.onDraw(canvas);
        if (canvas.isHardwareAccelerated()) {
            onDrawKeyboard(canvas);
            return;
        }

        final boolean bufferNeedsUpdates = mInvalidateAllKeys || !mInvalidatedKeys.isEmpty();
        if (bufferNeedsUpdates || mOffscreenBuffer == null) {
            if (maybeAllocateOffscreenBuffer()) {
                mInvalidateAllKeys = true;
                // TODO: Stop using the offscreen canvas even when in software rendering
                mOffscreenCanvas.setBitmap(mOffscreenBuffer);
            }
            onDrawKeyboard(mOffscreenCanvas);
        }
        canvas.drawBitmap(mOffscreenBuffer, 0.0f, 0.0f, null);
    }

    private boolean maybeAllocateOffscreenBuffer() {
        final int width = getWidth();
        final int height = getHeight();
        if (width == 0 || height == 0) {
            return false;
        }
        if (mOffscreenBuffer != null && mOffscreenBuffer.getWidth() == width
                && mOffscreenBuffer.getHeight() == height) {
            return false;
        }
        freeOffscreenBuffer();
        mOffscreenBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        return true;
    }

    private void freeOffscreenBuffer() {
        mOffscreenCanvas.setBitmap(null);
        mOffscreenCanvas.setMatrix(null);
        if (mOffscreenBuffer != null) {
            mOffscreenBuffer.recycle();
            mOffscreenBuffer = null;
        }
    }

    private void onDrawKeyboard(@NonNull final Canvas canvas) {
        Trace.beginSection("KeyboardView#onDrawKeyboard");
        try {
            final Keyboard keyboard = getKeyboard();
            if (keyboard == null) {
                return;
            }

            mShowsHints = Settings.getValues().mShowsHints;
            final float scale = Settings.getValues().mKeyboardHeightScale;
            mIconScaleFactor = scale < 0.8f ? scale + 0.2f : 1f;
            final Paint paint = mPaint;
            final Drawable background = getBackground();
            // Calculate clip region and set.
            final boolean drawAllKeys = mInvalidateAllKeys || mInvalidatedKeys.isEmpty();
            final boolean isHardwareAccelerated = canvas.isHardwareAccelerated();
            // With hardware acceleration, onDraw() records the View display list. If we
            // only emit
            // the dirty key's draw commands here, untouched keys can disappear until a full
            // invalidation rebuilds the list. Keep the old full-draw behavior for hardware
            // canvases, and reserve partial redraws for the software offscreen buffer path.
            if (drawAllKeys || isHardwareAccelerated) {
                if (!isHardwareAccelerated && background != null) {
                    // Need to draw keyboard background on {@link #mOffscreenBuffer}.
                    canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR);
                    background.draw(canvas);
                }
                // Draw all keys.
                for (final Key key : keyboard.getSortedKeys()) {
                    onDrawKey(key, canvas, paint);
                }
            } else {
                for (final Key key : mInvalidatedKeys) {
                    if (!keyboard.hasKey(key)) {
                        continue;
                    }
                    if (!isHardwareAccelerated && background != null) {
                        // Need to redraw key's background on {@link #mOffscreenBuffer}.
                        final int x = key.getX() + getPaddingLeft();
                        final int y = key.getY() + getPaddingTop();
                        mClipRect.set(x, y, x + key.getWidth(), y + key.getHeight());
                        canvas.save();
                        canvas.clipRect(mClipRect);
                        canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR);
                        background.draw(canvas);
                        canvas.restore();
                    }
                    onDrawKey(key, canvas, paint);
                }
            }

            mInvalidatedKeys.clear();
            mInvalidateAllKeys = false;
        } finally {
            Trace.endSection();
        }
    }

    private void onDrawKey(@NonNull final Key key, @NonNull final Canvas canvas,
            @NonNull final Paint paint) {
        final int keyDrawX = key.getDrawX() + getPaddingLeft();
        final int keyDrawY = key.getY() + getPaddingTop();
        canvas.translate(keyDrawX, keyDrawY);

        final KeyVisualAttributes attr = key.getVisualAttributes();
        // don't use the raw key height, linear font scaling with height is too extreme
        final KeyDrawParams params = mKeyDrawParams.mayCloneAndUpdateParams((int) (key.getHeight() * mKeyScaleForText),
                attr);
        params.mAnimAlpha = Constants.Color.ALPHA_OPAQUE;

        if (!key.isSpacer()) {
            final Drawable background = key.selectBackgroundDrawable(
                    mKeyBackground, mFunctionalKeyBackground, mSpacebarBackground, mActionKeyBackground);
            onDrawKeyBackground(key, canvas, background);
        }
        onDrawKeyTopVisuals(key, canvas, paint, params);

        canvas.translate(-keyDrawX, -keyDrawY);
    }

    // Draw key background.
    protected void onDrawKeyBackground(@NonNull final Key key, @NonNull final Canvas canvas,
            @NonNull final Drawable background) {
        final int keyWidth = key.getDrawWidth();
        final int keyHeight = key.getHeight();
        final float labelCharWidth = TypefaceUtils.getReferenceCharWidth(paint);
        final float hintX;
        final float hintBaseline = paint.ascent();
        paint.setTypeface(params.mTypeface);
        paint.setTextSize(params.mHintLetterSize);
        paint.setColor(false ? mColors.get(ColorType.KEY_TEXT) : params.mHintLabelColor);
        paint.setTextAlign(Align.CENTER);
        if (mColors.getThemeStyle().equals(STYLE_ROUNDED) || mColors.getThemeStyle().equals(STYLE_ROUNDED)) {
            if (key.getBackgroundType() == Key.BACKGROUND_TYPE_SPACEBAR)
                hintX = keyWidth + hintBaseline + labelCharWidth * 0.1f;
            else
                hintX = key.hasFunctionalBackground() || key.hasActionKeyBackground() ? keyWidth / 2.0f
                        : keyWidth - mKeyHintLetterPadding - labelCharWidth / 2.0f;
        } else {
            hintX = keyWidth - mKeyHintLetterPadding - TypefaceUtils.getReferenceCharWidth(paint) / 2.0f;
        }
        final float hintY = keyHeight - mKeyPopupHintLetterPadding;
        paint.setTypeface(KeyboardTypeface.resolve(mKeyPopupHintLetter, params.mTypeface));
        canvas.drawText(mKeyPopupHintLetter, hintX, hintY, paint);
    }

    protected static void drawIcon(@NonNull final Canvas canvas, @NonNull final Drawable icon,
            final int x, final int y, final int width, final int height) {
        drawIcon(canvas, icon, x, y, width, height, false);
    }

    protected static void drawIcon(@NonNull final Canvas canvas, @NonNull final Drawable icon,
            final int x, final int y, final int width, final int height,
            final boolean mirrorHorizontally) {
        final int saveCount = canvas.save();
        canvas.translate(x, y);
        if (mirrorHorizontally) {
            canvas.translate(width, 0);
            canvas.scale(-1.0f, 1.0f);
        }
        icon.setBounds(0, 0, width, height);
        icon.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    public Paint newLabelPaint(@Nullable final Key key) {
        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        if (key == null) {
            paint.setTypeface(KeyboardTypeface.resolve(null, mKeyDrawParams.mTypeface));
            paint.setTextSize(mKeyDrawParams.mLabelSize);
        } else {
            paint.setColor(false ? mColors.get(ColorType.KEY_TEXT) : key.selectTextColor(mKeyDrawParams));
            paint.setTypeface(KeyboardTypeface.resolve(key.getLabel(), key.selectTypeface(mKeyDrawParams)));
            paint.setTextSize(key.selectTextSize(mKeyDrawParams) * mFontSizeMultiplier);
        }
        return paint;
    }

    /**
     * Requests a redraw of the entire keyboard. Calling {@link #invalidate} is not
     * sufficient
     * because the keyboard renders the keys to an off-screen buffer and an
     * invalidate() only
     * draws the cached buffer.
     *
     * @see #invalidateKey(Key)
     */
    public void updateThemeColors(final Colors colors) {
        mColors = colors;
        if (mKeyBackground != null) {
            colors.setColor(mKeyBackground,
                    this instanceof MoreSuggestionsView ? ColorType.MORE_SUGGESTIONS_WORD_BACKGROUND
                            : (isPopupKeysView() ? ColorType.KEY_PREVIEW_BACKGROUND
                                    : ColorType.KEY_BACKGROUND));
        }
        if (mFunctionalKeyBackground != null) {
            colors.setColor(mFunctionalKeyBackground, ColorType.FUNCTIONAL_KEY_BACKGROUND);
        }
        if (mSpacebarBackground != null) {
            colors.setColor(mSpacebarBackground, ColorType.SPACE_BAR_BACKGROUND);
        }
        if (mActionKeyBackground != null) {
            colors.setColor(mActionKeyBackground,
                    isPopupKeysView() ? ColorType.ACTION_KEY_BACKGROUND
                            : this instanceof PopupKeysKeyboardView ? ColorType.ACTION_KEY_POPUP_KEYS_BACKGROUND
                                    : ColorType.ACTION_KEY_BACKGROUND);
        }
        final Keyboard keyboard = getKeyboard();
        if (keyboard instanceof MoreSuggestions) {
            colors.setBackground(this, ColorType.MORE_SUGGESTIONS_BACKGROUND);
        } else if (keyboard instanceof PopupKeysKeyboard) {
            colors.setBackground(this, ColorType.KEY_PREVIEW_BACKGROUND);
        }
        invalidateAllKeys();
    }

    public void invalidateAllKeys() {
        mInvalidatedKeys.clear();
        mInvalidateAllKeys = true;
        postInvalidateOnAnimation();
    }

    /**
     * Invalidates a key so that it will be redrawn on the next repaint. Use this
     * method if only
     * one key is changing it's content. Any changes that affect the position or
     * size of the key
     * may not be honored.
     *
     * @param key key in the attached {@link Keyboard}.
     * @see #invalidateAllKeys
     */
    public void invalidateKey(@Nullable final Key key) {
        if (mInvalidateAllKeys || key == null) {
            return;
        }
        mInvalidatedKeys.add(key);
        final int x = key.getX() + getPaddingLeft();
        final int y = key.getY() + getPaddingTop();
        postInvalidateOnAnimation(x, y, x + key.getWidth(), y + key.getHeight());
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        freeOffscreenBuffer();
    }

    public void deallocateMemory() {
        freeOffscreenBuffer();
    }

    private boolean isSpecialKey(@NonNull final Key key) {
        if (key.isShift())
            return true;
        final int code = key.getCode();
        return code == KeyCode.SYMBOL
                || code == KeyCode.ALPHA
                || code == KeyCode.SYMBOL_ALPHA
                || code == KeyCode.NUMPAD
                || code == Constants.CODE_COMMA
                || code == KeyCode.EMOJI
                || code == Constants.CODE_PERIOD
                || code == KeyCode.DELETE;
    }

    private void setKeyIconColor(Key key, Drawable icon, Keyboard keyboard) {
        if (shouldDrawSelectionOnlyPopupKey(key)) {
            mColors.setColor(icon, isPopupKeySelected(key)
                    ? ColorType.ACTION_KEY_ICON
                    : ColorType.KEY_PREVIEW_TEXT);
        } else if (false && (key.hasActionKeyBackground() || isSpecialKey(key))) {
            mColors.setColor(icon, ColorType.KEY_ICON);
        } else if (key.hasActionKeyBackground()) {
            mColors.setColor(icon, ColorType.ACTION_KEY_ICON);
        } else if (key.isShift() && keyboard != null) {
            if (keyboard.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED
                    || keyboard.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED
                    || keyboard.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED
                    || keyboard.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED)
                mColors.setColor(icon, ColorType.SHIFT_KEY_ICON);
            else
                mColors.setColor(icon, ColorType.KEY_ICON); // normal key if not shifted
        } else if (key.getBackgroundType() != Key.BACKGROUND_TYPE_NORMAL) {
            mColors.setColor(icon, ColorType.KEY_ICON);
        } else if (this instanceof PopupKeysKeyboardView) {
            mColors.setColor(icon, ColorType.POPUP_KEY_ICON);
        } else if (key.getCode() == Constants.CODE_SPACE || key.getCode() == KeyCode.ZWNJ) {
            // set color of default number pad space bar icon for Holo style, or for
            // zero-width non-joiner (zwnj) on some layouts like nepal
            mColors.setColor(icon, ColorType.KEY_ICON);
        } else {
            mColors.setColor(icon, ColorType.KEY_TEXT);
        }
    }

    private boolean isPopupKeysView() {
        return this instanceof PopupKeysKeyboardView && !(this instanceof MoreSuggestionsView);
    }

    private boolean shouldDrawSelectionOnlyPopupKey(@NonNull final Key key) {
        return isPopupKeysView()
                && !key.isSpacer()
                && (key.getBackgroundType() == Key.BACKGROUND_TYPE_NORMAL
                        || key.getBackgroundType() == Key.BACKGROUND_TYPE_ACTION);
    }

    private static boolean isPopupKeySelected(@NonNull final Key key) {
        return key.isPressed() || key.isLocked();
    }

    private void drawPopupKeySelectionBackground(@NonNull final Key key, @NonNull final Canvas canvas) {
        if (!isPopupKeySelected(key)) {
            return;
        }

        final float keyWidth = key.getDrawWidth();
        final float keyHeight = key.getHeight();
        final float highlightInset = Math.max(1f, keyHeight * 0.01f);
        final float highlightHeight = Math.max(1f, keyHeight - highlightInset * 2f);
        final float centerX = keyWidth * 0.5f;
        final float centerY = keyHeight * 0.5f;

        mBackgroundPaint.setColor(getStrongPopupSelectionColor());

        if (keyWidth <= keyHeight * 1.25f) {
            final float radius = Math.max(1f,
                    Math.min(keyWidth - highlightInset * 2f, highlightHeight) * 0.5f);
            canvas.drawCircle(centerX, centerY, radius, mBackgroundPaint);
            return;
        }

        final float highlightWidth = Math.max(highlightHeight, keyWidth - highlightInset * 2f);
        final float left = centerX - highlightWidth * 0.5f;
        final float top = centerY - highlightHeight * 0.5f;
        final float radius = highlightHeight * 0.5f;
        canvas.drawRoundRect(left, top, left + highlightWidth, top + highlightHeight,
                radius, radius, mBackgroundPaint);
    }

    private int getStrongPopupSelectionColor() {
        final int actionColor = mColors.get(ColorType.ACTION_KEY_BACKGROUND);
        final float[] hsv = new float[3];
        Color.colorToHSV(Color.rgb(Color.red(actionColor), Color.green(actionColor),
                Color.blue(actionColor)), hsv);
        hsv[1] = Math.max(hsv[1], 0.55f);
        if (hsv[2] > 0.70f) {
            hsv[2] = 0.70f;
        } else if (hsv[2] < 0.35f) {
            hsv[2] = 0.35f;
        }
        return Color.HSVToColor(Constants.Color.ALPHA_OPAQUE, hsv);
    }

}
