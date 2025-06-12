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

package com.android.systemui.communal.posturing.data.model

import androidx.annotation.FloatRange

data class PositionState(
    val stationary: StationaryState = StationaryState.Unknown,
    val orientation: OrientationState = OrientationState.Unknown,
) {
    sealed interface StationaryState {
        @get:FloatRange(from = 0.0, to = 1.0) val confidence: Float

        data object Unknown : StationaryState {
            override val confidence: Float = 0f
        }

        data class Stationary(override val confidence: Float) : StationaryState

        data class NotStationary(override val confidence: Float) : StationaryState
    }

    sealed interface OrientationState {
        @get:FloatRange(from = 0.0, to = 1.0) val confidence: Float

        data object Unknown : OrientationState {
            override val confidence: Float = 0f
        }

        data class Postured(override val confidence: Float) : OrientationState

        data class NotPostured(override val confidence: Float) : OrientationState
    }
}
