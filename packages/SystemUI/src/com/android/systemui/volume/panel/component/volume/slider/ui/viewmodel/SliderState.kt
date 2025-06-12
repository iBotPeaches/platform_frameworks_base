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

package com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel

import com.android.systemui.common.shared.model.Icon
import com.android.systemui.haptics.slider.SliderHapticFeedbackFilter

/**
 * Models a state of a volume slider.
 *
 * @property disabledMessage is shown when [isEnabled] is false
 */
sealed interface SliderState {
    val value: Float
    val valueRange: ClosedFloatingPointRange<Float>
    val step: Float
    val hapticFilter: SliderHapticFeedbackFilter

    // Force preloaded icon
    val icon: Icon.Loaded?
    val isEnabled: Boolean
    val label: String

    val a11yClickDescription: String?
    val a11yStateDescription: String?
    val a11yContentDescription: String
    val disabledMessage: String?
    val isMutable: Boolean

    data object Empty : SliderState {
        override val value: Float = 0f
        override val valueRange: ClosedFloatingPointRange<Float> = 0f..1f
        override val hapticFilter = SliderHapticFeedbackFilter()
        override val icon: Icon.Loaded? = null
        override val label: String = ""
        override val disabledMessage: String? = null
        override val step: Float = 0f
        override val a11yClickDescription: String? = null
        override val a11yStateDescription: String? = null
        override val a11yContentDescription: String = label
        override val isEnabled: Boolean = true
        override val isMutable: Boolean = false
    }
}
