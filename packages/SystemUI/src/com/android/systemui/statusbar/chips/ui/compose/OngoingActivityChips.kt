/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.chips.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.StatusBarChipsReturnAnimations
import com.android.systemui.statusbar.chips.ui.model.MultipleOngoingActivityChipsModel
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.NotificationIconContainerViewBinder

@Composable
fun OngoingActivityChips(
    chips: MultipleOngoingActivityChipsModel,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
    modifier: Modifier = Modifier,
) {
    if (StatusBarChipsReturnAnimations.isEnabled) {
        SideEffect {
            // Active chips must always be capable of animating to/from activities, even when they
            // are hidden. Therefore we always register their transitions.
            for (chip in chips.active) chip.transitionManager?.registerTransition?.invoke()
            // Inactive chips and chips in the overflow are never shown, so they must not have any
            // registered transition.
            for (chip in chips.overflow) chip.transitionManager?.unregisterTransition?.invoke()
            for (chip in chips.inactive) chip.transitionManager?.unregisterTransition?.invoke()
        }
    }

    val shownChips = chips.active.filter { !it.isHidden }
    if (shownChips.isNotEmpty()) {
        Row(
            modifier =
                modifier
                    .fillMaxHeight()
                    .padding(start = dimensionResource(R.dimen.ongoing_activity_chip_margin_start)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(dimensionResource(R.dimen.ongoing_activity_chip_margin_start)),
        ) {
            shownChips.forEach {
                key(it.key) {
                    OngoingActivityChip(
                        model = it,
                        iconViewStore = iconViewStore,
                        modifier = Modifier.sysuiResTag(it.key),
                    )
                }
            }
        }
    }
}
