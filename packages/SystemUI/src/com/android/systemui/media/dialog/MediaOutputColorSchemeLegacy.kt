/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.media.dialog

import android.content.Context
import com.android.settingslib.Utils
import com.android.systemui.monet.ColorScheme
import com.android.systemui.res.R

abstract class MediaOutputColorSchemeLegacy {
    companion object Factory {
        @JvmStatic
        fun fromSystemColors(context: Context): MediaOutputColorSchemeLegacy {
            return MediaOutputColorSchemeLegacySystem(context)
        }

        @JvmStatic
        fun fromDynamicColors(
            colorScheme: ColorScheme,
            isDarkTheme: Boolean,
        ): MediaOutputColorSchemeLegacy {
            return MediaOutputColorSchemeLegacyDynamic(colorScheme, isDarkTheme)
        }
    }

    abstract fun getColorConnectedItemBackground(): Int

    abstract fun getColorPositiveButtonText(): Int

    abstract fun getColorDialogBackground(): Int

    abstract fun getColorItemContent(): Int

    abstract fun getColorSeekbarProgress(): Int

    abstract fun getColorButtonBackground(): Int

    abstract fun getColorItemBackground(): Int
}

class MediaOutputColorSchemeLegacySystem(private val mContext: Context) :
    MediaOutputColorSchemeLegacy() {

    override fun getColorConnectedItemBackground() =
        Utils.getColorStateListDefaultColor(
            mContext,
            R.color.media_dialog_connected_item_background,
        )

    override fun getColorPositiveButtonText() =
        Utils.getColorStateListDefaultColor(mContext, R.color.media_dialog_solid_button_text)

    override fun getColorDialogBackground() =
        Utils.getColorStateListDefaultColor(mContext, R.color.media_dialog_background)

    override fun getColorItemContent() =
        Utils.getColorStateListDefaultColor(mContext, R.color.media_dialog_item_main_content)

    override fun getColorSeekbarProgress() =
        Utils.getColorStateListDefaultColor(mContext, R.color.media_dialog_seekbar_progress)

    override fun getColorButtonBackground() =
        Utils.getColorStateListDefaultColor(mContext, R.color.media_dialog_button_background)

    override fun getColorItemBackground() =
        Utils.getColorStateListDefaultColor(mContext, R.color.media_dialog_item_background)
}

class MediaOutputColorSchemeLegacyDynamic(colorScheme: ColorScheme, isDarkTheme: Boolean) :
    MediaOutputColorSchemeLegacy() {
    private var mColorItemContent: Int
    private var mColorSeekbarProgress: Int
    private var mColorButtonBackground: Int
    private var mColorItemBackground: Int
    private var mColorConnectedItemBackground: Int
    private var mColorPositiveButtonText: Int
    private var mColorDialogBackground: Int

    init {
        if (isDarkTheme) {
            mColorItemContent = colorScheme.accent1.s100 // A1-100
            mColorSeekbarProgress = colorScheme.accent2.s600 // A2-600
            mColorButtonBackground = colorScheme.accent1.s300 // A1-300
            mColorItemBackground = colorScheme.neutral2.s800 // N2-800
            mColorConnectedItemBackground = colorScheme.accent2.s800 // A2-800
            mColorPositiveButtonText = colorScheme.accent2.s800 // A2-800
            mColorDialogBackground = colorScheme.neutral1.s900 // N1-900
        } else {
            mColorItemContent = colorScheme.accent1.s800 // A1-800
            mColorSeekbarProgress = colorScheme.accent1.s300 // A1-300
            mColorButtonBackground = colorScheme.accent1.s600 // A1-600
            mColorItemBackground = colorScheme.accent2.s50 // A2-50
            mColorConnectedItemBackground = colorScheme.accent1.s100 // A1-100
            mColorPositiveButtonText = colorScheme.neutral1.s50 // N1-50
            mColorDialogBackground = colorScheme.backgroundColor
        }
    }

    override fun getColorConnectedItemBackground() = mColorConnectedItemBackground

    override fun getColorPositiveButtonText() = mColorPositiveButtonText

    override fun getColorDialogBackground() = mColorDialogBackground

    override fun getColorItemContent() = mColorItemContent

    override fun getColorSeekbarProgress() = mColorSeekbarProgress

    override fun getColorButtonBackground() = mColorButtonBackground

    override fun getColorItemBackground() = mColorItemBackground
}
