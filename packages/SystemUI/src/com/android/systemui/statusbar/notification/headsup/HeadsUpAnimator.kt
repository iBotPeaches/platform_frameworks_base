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

package com.android.systemui.statusbar.notification.headsup

import android.content.Context
import com.android.internal.policy.SystemBarUtils
import com.android.systemui.res.R
import com.android.systemui.statusbar.ui.SystemBarUtilsProxy

/**
 * A class shared between [StackScrollAlgorithm] and [StackStateAnimator] to ensure all heads up
 * animations use the same animation values.
 *
 * @param systemBarUtilsProxy optional utility class to provide the status bar height. Typically
 *   null in production code and non-null in tests.
 */
class HeadsUpAnimator(context: Context, private val systemBarUtilsProxy: SystemBarUtilsProxy?) {
    init {
        NotificationsHunSharedAnimationValues.unsafeAssertInNewMode()
    }

    var headsUpAppearHeightBottom: Int = 0
    var stackTopMargin: Int = 0

    private var headsUpAppearStartAboveScreen = context.fetchHeadsUpAppearStartAboveScreen()
    private var statusBarHeight = fetchStatusBarHeight(context)

    /**
     * Returns the Y translation for a heads-up notification animation.
     *
     * For an appear animation, the returned Y translation should be the starting value of the
     * animation. For a disappear animation, the returned Y translation should be the ending value
     * of the animation.
     */
    fun getHeadsUpYTranslation(isHeadsUpFromBottom: Boolean, hasStatusBarChip: Boolean): Int {
        if (NotificationsHunSharedAnimationValues.isUnexpectedlyInLegacyMode()) return 0

        if (isHeadsUpFromBottom) {
            // start from or end at the bottom of the screen
            return headsUpAppearHeightBottom + headsUpAppearStartAboveScreen
        }

        if (hasStatusBarChip) {
            // If this notification is also represented by a chip in the status bar, we don't want
            // any HUN transitions to obscure that chip.
            return statusBarHeight - stackTopMargin
        }

        // start from or end at the top of the screen
        return -stackTopMargin - headsUpAppearStartAboveScreen
    }

    /** Should be invoked when resource values may have changed. */
    fun updateResources(context: Context) {
        headsUpAppearStartAboveScreen = context.fetchHeadsUpAppearStartAboveScreen()
        statusBarHeight = fetchStatusBarHeight(context)
    }

    private fun Context.fetchHeadsUpAppearStartAboveScreen(): Int {
        return this.resources.getDimensionPixelSize(R.dimen.heads_up_appear_y_above_screen)
    }

    private fun fetchStatusBarHeight(context: Context): Int {
        return systemBarUtilsProxy?.getStatusBarHeight()
            ?: SystemBarUtils.getStatusBarHeight(context)
    }
}
