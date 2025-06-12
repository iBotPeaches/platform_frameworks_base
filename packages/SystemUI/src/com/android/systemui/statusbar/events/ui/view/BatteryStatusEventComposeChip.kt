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

package com.android.systemui.statusbar.events.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.android.systemui.res.R
import com.android.systemui.statusbar.core.NewStatusBarIcons
import com.android.systemui.statusbar.events.BackgroundAnimatableView
import com.android.systemui.statusbar.pipeline.battery.domain.interactor.BatteryInteractor
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryColors.LightThemeChargingColors
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryFrame
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryGlyph
import com.android.systemui.statusbar.pipeline.battery.ui.composable.BatteryCanvas
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.BatteryViewModel
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.BatteryViewModel.Companion.glyphRepresentation

/**
 * [StatusEvent] chip for the battery plugged in status event. Shows the current battery level and
 * charging state in the status bar via the system event animation.
 *
 * This chip will fully replace [BatteryStatusChip] when [NewStatusBarIcons] is rolled out
 */
@SuppressLint("ViewConstructor")
class BatteryStatusEventComposeChip
@JvmOverloads
constructor(level: Int, context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs), BackgroundAnimatableView {
    private val roundedContainer: LinearLayout
    private val composeInner: ComposeView
    override val contentView: View
        get() = composeInner

    init {
        NewStatusBarIcons.unsafeAssertInNewMode()

        inflate(context, R.layout.status_bar_event_chip_compose, this)
        roundedContainer = requireViewById(R.id.rounded_container)
        composeInner = requireViewById(R.id.compose_view)
        composeInner.apply {
            setContent {
                val isFull = BatteryInteractor.isBatteryFull(level)
                BatteryCanvas(
                    modifier =
                        Modifier.width(BatteryViewModel.STATUS_BAR_BATTERY_WIDTH)
                            .height(BatteryViewModel.STATUS_BAR_BATTERY_HEIGHT),
                    path = BatteryFrame.pathSpec,
                    // TODO(b/394659067): get a content description for this chip
                    contentDescription = "",
                    innerWidth = BatteryFrame.innerWidth,
                    innerHeight = BatteryFrame.innerHeight,
                    // This event only happens when plugged in, so we always show it as charging
                    glyphs =
                        if (isFull) listOf(BatteryGlyph.Bolt)
                        else level.glyphRepresentation() + BatteryGlyph.Bolt,
                    level = level,
                    isFull = isFull,
                    colorsProvider = { LightThemeChargingColors },
                )
            }
        }
        updateResources()
    }

    /**
     * When animating as a chip in the status bar, we want to animate the width for the rounded
     * container. We have to subtract our own top and left offset because the bounds come to us as
     * absolute on-screen bounds.
     */
    override fun setBoundsForAnimation(l: Int, t: Int, r: Int, b: Int) {
        roundedContainer.setLeftTopRightBottom(l - left, t - top, r - left, b - top)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun updateResources() {
        roundedContainer.background = mContext.getDrawable(R.drawable.statusbar_chip_bg)
    }
}
