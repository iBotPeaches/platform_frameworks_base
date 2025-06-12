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

package com.android.server.accessibility.magnification;

import static com.android.server.accessibility.AccessibilityManagerService.MAGNIFICATION_GESTURE_HANDLER_ID;

import android.annotation.NonNull;

import com.android.server.input.InputManagerInternal;

/**
 * Handles pointer motion event for full screen magnification.
 * Responsible for controlling magnification's cursor following feature.
 */
public class FullScreenMagnificationPointerMotionEventFilter implements
        InputManagerInternal.AccessibilityPointerMotionFilter {

    private final FullScreenMagnificationController mController;

    public FullScreenMagnificationPointerMotionEventFilter(
            FullScreenMagnificationController controller) {
        mController = controller;
    }

    /**
     * This call happens on the input hot path and it is extremely performance sensitive. It
     * also must not call back into native code.
     */
    @Override
    @NonNull
    public float[] filterPointerMotionEvent(float dx, float dy, float currentX, float currentY,
            int displayId) {
        if (!mController.isActivated(displayId)) {
            // unrelated display.
            return new float[]{dx, dy};
        }

        // TODO(361817142): implement centered and edge following types.

        // Continuous cursor following.
        float scale = mController.getScale(displayId);
        final float newCursorX = currentX + dx;
        final float newCursorY = currentY + dy;
        mController.setOffset(displayId,
                newCursorX - newCursorX * scale, newCursorY - newCursorY * scale,
                MAGNIFICATION_GESTURE_HANDLER_ID);

        // In the continuous mode, the cursor speed in physical display is kept.
        // Thus, we don't consume any motion delta.
        return new float[]{dx, dy};
    }
}
