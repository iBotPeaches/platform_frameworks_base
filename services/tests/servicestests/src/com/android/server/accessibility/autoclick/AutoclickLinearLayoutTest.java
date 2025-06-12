/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.accessibility.autoclick;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.view.MotionEvent;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test cases for {@link AutoclickLinearLayout}. */
@RunWith(AndroidTestingRunner.class)
public class AutoclickLinearLayoutTest {
    private boolean mHovered;

    private final AutoclickLinearLayout.OnHoverChangedListener mListener =
            new AutoclickLinearLayout.OnHoverChangedListener() {
                @Override
                public void onHoverChanged(boolean hovered) {
                    mHovered = hovered;
                }
            };

    @Rule
    public TestableContext mTestableContext =
            new TestableContext(getInstrumentation().getContext());
    private AutoclickLinearLayout mAutoclickLinearLayout;

    @Before
    public void setUp() {
        mAutoclickLinearLayout = new AutoclickLinearLayout(mTestableContext);
    }

    @Test
    public void autoclickLinearLayout_hoverChangedListener_setHovered() {
        mHovered = false;
        mAutoclickLinearLayout.setOnHoverChangedListener(mListener);
        mAutoclickLinearLayout.onHoverChanged(/* hovered= */ true);
        assertThat(mHovered).isTrue();
    }

    @Test
    public void autoclickLinearLayout_hoverChangedListener_setNotHovered() {
        mHovered = true;

        mAutoclickLinearLayout.setOnHoverChangedListener(mListener);
        mAutoclickLinearLayout.onHoverChanged(/* hovered= */ false);
        assertThat(mHovered).isFalse();
    }

    @Test
    public void autoclickLinearLayout_onInterceptHoverEvent_hovered() {
        mAutoclickLinearLayout.setHovered(false);
        mAutoclickLinearLayout.onInterceptHoverEvent(
                getFakeMotionEvent(MotionEvent.ACTION_HOVER_ENTER));
        assertThat(mAutoclickLinearLayout.isHovered()).isTrue();

        mAutoclickLinearLayout.setHovered(false);
        mAutoclickLinearLayout.onInterceptHoverEvent(
                getFakeMotionEvent(MotionEvent.ACTION_HOVER_MOVE));
        assertThat(mAutoclickLinearLayout.isHovered()).isTrue();
    }

    @Test
    public void autoclickLinearLayout_onInterceptHoverEvent_hoveredExit() {
        mAutoclickLinearLayout.setHovered(true);
        mAutoclickLinearLayout.onInterceptHoverEvent(
                getFakeMotionEvent(MotionEvent.ACTION_HOVER_EXIT));
        assertThat(mAutoclickLinearLayout.isHovered()).isFalse();
    }

    private MotionEvent getFakeMotionEvent(int motionEventAction) {
        return MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 0,
                /* action= */ motionEventAction,
                /* x= */ 0,
                /* y= */ 0,
                /* metaState= */ 0);
    }
}
