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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class FullScreenMagnificationPointerMotionEventFilterTest {
    @Mock
    private FullScreenMagnificationController mMockFullScreenMagnificationController;

    private FullScreenMagnificationPointerMotionEventFilter mFilter;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mFilter = new FullScreenMagnificationPointerMotionEventFilter(
                mMockFullScreenMagnificationController);
    }

    @Test
    public void inactiveDisplay_doNothing() {
        when(mMockFullScreenMagnificationController.isActivated(anyInt())).thenReturn(false);

        float[] delta = new float[]{1.f, 2.f};
        float[] result = mFilter.filterPointerMotionEvent(delta[0], delta[1], 3.0f, 4.0f, 0);
        assertThat(result).isEqualTo(delta);
    }

    @Test
    public void testContinuousMove() {
        when(mMockFullScreenMagnificationController.isActivated(anyInt())).thenReturn(true);
        when(mMockFullScreenMagnificationController.getScale(anyInt())).thenReturn(3.f);

        float[] delta = new float[]{5.f, 10.f};
        float[] result = mFilter.filterPointerMotionEvent(delta[0], delta[1], 20.f, 30.f, 0);
        assertThat(result).isEqualTo(delta);
        // At the first cursor move, it goes to (20, 30) + (5, 10) = (25, 40). The scale is 3.0.
        // The expected offset is (-25 * (3-1), -40 * (3-1)) = (-50, -80).
        verify(mMockFullScreenMagnificationController)
                .setOffset(eq(0), eq(-50.f), eq(-80.f), anyInt());

        float[] delta2 = new float[]{10.f, 5.f};
        float[] result2 = mFilter.filterPointerMotionEvent(delta2[0], delta2[1], 25.f, 40.f, 0);
        assertThat(result2).isEqualTo(delta2);
        // At the second cursor move, it goes to (25, 40) + (10, 5) = (35, 40). The scale is 3.0.
        // The expected offset is (-35 * (3-1), -45 * (3-1)) = (-70, -90).
        verify(mMockFullScreenMagnificationController)
                .setOffset(eq(0), eq(-70.f), eq(-90.f), anyInt());
    }
}
