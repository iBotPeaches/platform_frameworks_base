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

package com.android.systemui.media.remedia.ui.viewmodel

import androidx.annotation.FloatRange
import androidx.compose.ui.geometry.Offset

/**
 * Models UI state for the navigation component of the UI (potentially containing the seek bar and
 * the buttons to its left and right).
 */
sealed interface MediaNavigationViewModel {

    /** The seek bar should be showing. */
    data class Showing(
        /** The progress to show on the seek bar, between `0` and `1`. */
        @FloatRange(from = 0.0, to = 1.0) val progress: Float,
        /**
         * The action button to the left of the seek bar; or `null` if it should be absent in the
         * UI.
         */
        val left: MediaSecondaryActionViewModel,
        /**
         * The action button to the right of the seek bar; or `null` if it should be absent in the
         * UI.
         */
        val right: MediaSecondaryActionViewModel,
        /**
         * Whether the portion of the seek bar track before the thumb should show the squiggle
         * animation.
         */
        val isSquiggly: Boolean,
        /**
         * Whether the UI should show as "scrubbing" because the user is actively moving the thumb
         * of the seek bar.
         */
        val isScrubbing: Boolean,
        /**
         * A callback to invoke while the user is "scrubbing" (e.g. actively moving the thumb of the
         * seek bar). The position/progress of the actual track should not be changed during this
         * time.
         */
        val onScrubChange: (progress: Float) -> Unit,
        /**
         * A callback to invoke once the user finishes "scrubbing" (e.g. stopped moving the thumb of
         * the seek bar). The position/progress should be committed.
         */
        val onScrubFinished: (delta: Offset) -> Unit,
        /** Accessibility string to attach to the seekbar UI element. */
        val contentDescription: String,
    ) : MediaNavigationViewModel

    /** The seek bar should be hidden. */
    data object Hidden : MediaNavigationViewModel
}
