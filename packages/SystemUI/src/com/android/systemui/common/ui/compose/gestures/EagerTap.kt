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

package com.android.systemui.common.ui.compose.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.coroutineScope

/**
 * Detects taps and double taps without waiting for the double tap minimum delay in between
 *
 * Using [detectTapGestures] with both a single tap and a double tap defined will send only one of
 * these event per user interaction. This variant will send the single tap at all times, with the
 * optional double tap if the user pressed a second time in a short period of time.
 *
 * Warning: Use this only if you know that reporting a single tap followed by a double tap won't be
 * a problem in your use case.
 *
 * @param doubleTapEnabled whether this should listen for double tap events. This value is captured
 *   at the first down movement.
 * @param onDoubleTap the double tap callback
 * @param onTap the single tap callback
 */
suspend fun PointerInputScope.detectEagerTapGestures(
    doubleTapEnabled: () -> Boolean,
    onDoubleTap: (Offset) -> Unit,
    onTap: () -> Unit,
) = coroutineScope {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()

        // Capture whether double tap is enabled on first down as this state can change following
        // the first tap
        val isDoubleTapEnabled = doubleTapEnabled()

        // wait for first tap up or long press
        val upOrCancel = waitForUpOrCancellation()

        if (upOrCancel != null) {
            // tap was successful.
            upOrCancel.consume()
            onTap.invoke()

            if (isDoubleTapEnabled) {
                // check for second tap
                val secondDown =
                    withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                        val minUptime =
                            upOrCancel.uptimeMillis + viewConfiguration.doubleTapMinTimeMillis
                        var change: PointerInputChange
                        // The second tap doesn't count if it happens before DoubleTapMinTime of the
                        // first tap
                        do {
                            change = awaitFirstDown()
                        } while (change.uptimeMillis < minUptime)
                        change
                    }

                if (secondDown != null) {
                    // Second tap down detected

                    // Might have a long second press as the second tap
                    val secondUp = waitForUpOrCancellation()
                    if (secondUp != null) {
                        secondUp.consume()
                        onDoubleTap(secondUp.position)
                    }
                }
            }
        }
    }
}
