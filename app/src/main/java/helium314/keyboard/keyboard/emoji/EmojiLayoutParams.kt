/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.keyboard.emoji

import android.content.res.Resources
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils

internal class EmojiLayoutParams(res: Resources) {
    private val emojiListBottomMargin: Int
    val emojiKeyboardHeight: Int
    private val emojiCategoryPageIdViewHeight: Int
    val bottomRowKeyboardHeight: Int
    private val bottomPadding: Int

    init {
        val sv = Settings.getValues()
        val defaultKeyboardHeight = ResourceUtils.getSecondaryKeyboardHeight(res, sv)

        val keyVerticalGap = if (sv.mNarrowKeyGaps) {
            res.getFraction(R.fraction.config_key_vertical_gap_holo_narrow,
                defaultKeyboardHeight, defaultKeyboardHeight).toInt()
        } else {
            res.getFraction(R.fraction.config_key_vertical_gap_holo,
                defaultKeyboardHeight, defaultKeyboardHeight).toInt()
        }
        bottomPadding = (res.getFraction(R.fraction.config_keyboard_bottom_padding_holo,
            defaultKeyboardHeight, defaultKeyboardHeight) * sv.mBottomPaddingScale).toInt()
        val topPadding = res.getFraction(R.fraction.config_keyboard_top_padding_holo,
            defaultKeyboardHeight, defaultKeyboardHeight).toInt()

        val rowCount = KeyboardParams.DEFAULT_KEYBOARD_ROWS + if (sv.mShowsNumberRow) 1 else 0
        // Keep the emoji category/action row compact while preserving the overall emoji panel height.
        bottomRowKeyboardHeight = (((defaultKeyboardHeight - bottomPadding - topPadding) / rowCount - keyVerticalGap / 2) * 0.8f).toInt()

        val pageIdHeight = res.getDimension(R.dimen.config_emoji_category_page_id_height)
        emojiCategoryPageIdViewHeight = pageIdHeight.toInt()
        val offset = 1.25f * res.displayMetrics.density * sv.mKeyboardHeightScale // like ClipboardLayoutParams
        emojiListBottomMargin = 0
        // The emoji grid fills the panel; the smaller bottom row is overlaid at the bottom.
        emojiKeyboardHeight = defaultKeyboardHeight - bottomPadding + offset.toInt()
    }

    fun setEmojiListProperties(vp: RecyclerView) {
        val lp = vp.layoutParams as FrameLayout.LayoutParams
        lp.height = FrameLayout.LayoutParams.MATCH_PARENT
        lp.bottomMargin = emojiListBottomMargin
        vp.layoutParams = lp
        vp.clipToPadding = false
        vp.setPadding(0, 0, 0, bottomRowKeyboardHeight + bottomPadding)
    }

    fun setCategoryPageIdViewProperties(v: View) {
        val lp = v.layoutParams as FrameLayout.LayoutParams
        lp.height = emojiCategoryPageIdViewHeight
        lp.gravity = android.view.Gravity.BOTTOM
        lp.bottomMargin = bottomRowKeyboardHeight + bottomPadding
        v.layoutParams = lp
    }
}
