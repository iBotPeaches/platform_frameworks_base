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

package com.android.server.accessibility.gestures;

import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_END;

import static com.android.server.accessibility.gestures.TouchState.STATE_CLEAR;
import static com.android.server.accessibility.gestures.TouchState.STATE_TOUCH_EXPLORING;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.Display;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.accessibility.AccessibilityManagerService;
import com.android.server.accessibility.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class TouchStateTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private TouchState mTouchState;
    @Mock private AccessibilityManagerService mMockAms;

    @Before
    public void setup() {
        mTouchState = new TouchState(Display.DEFAULT_DISPLAY, mMockAms);
    }

    @EnableFlags(Flags.FLAG_POINTER_UP_MOTION_EVENT_IN_TOUCH_EXPLORATION)
    @Test
    public void injectedEvent_interactionEnd_pointerDown_startsTouchExploring() {
        mTouchState.mReceivedPointerTracker.mReceivedPointersDown = 1;
        mTouchState.onInjectedAccessibilityEvent(TYPE_TOUCH_INTERACTION_END);
        assertThat(mTouchState.getState()).isEqualTo(STATE_TOUCH_EXPLORING);
    }

    @EnableFlags(Flags.FLAG_POINTER_UP_MOTION_EVENT_IN_TOUCH_EXPLORATION)
    @Test
    public void injectedEvent_interactionEnd_pointerUp_clears() {
        mTouchState.mReceivedPointerTracker.mReceivedPointersDown = 0;
        mTouchState.onInjectedAccessibilityEvent(TYPE_TOUCH_INTERACTION_END);
        assertThat(mTouchState.getState()).isEqualTo(STATE_CLEAR);
    }

    @DisableFlags(Flags.FLAG_POINTER_UP_MOTION_EVENT_IN_TOUCH_EXPLORATION)
    @Test
    public void injectedEvent_interactionEnd_clears() {
        mTouchState.onInjectedAccessibilityEvent(TYPE_TOUCH_INTERACTION_END);
        assertThat(mTouchState.getState()).isEqualTo(STATE_CLEAR);
    }
}
