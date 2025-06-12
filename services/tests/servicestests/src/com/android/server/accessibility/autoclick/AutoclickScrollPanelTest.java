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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.never;

import android.content.Context;
import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.testing.TestableLooper;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;

import com.android.internal.R;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Test cases for {@link AutoclickScrollPanel}. */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class AutoclickScrollPanelTest {
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule
    public TestableContext mTestableContext =
            new TestableContext(getInstrumentation().getContext());

    @Mock private WindowManager mMockWindowManager;
    @Mock private AutoclickScrollPanel.ScrollPanelControllerInterface mMockScrollPanelController;

    private AutoclickScrollPanel mScrollPanel;

    // Scroll panel buttons.
    private ImageButton mUpButton;
    private ImageButton mDownButton;
    private ImageButton mLeftButton;
    private ImageButton mRightButton;
    private ImageButton mExitButton;

    @Before
    public void setUp() {
        mTestableContext.addMockSystemService(Context.WINDOW_SERVICE, mMockWindowManager);
        mScrollPanel = new AutoclickScrollPanel(mTestableContext, mMockWindowManager,
                mMockScrollPanelController);

        View contentView = mScrollPanel.getContentViewForTesting();

        // Initialize buttons.
        mUpButton = contentView.findViewById(R.id.scroll_up);
        mDownButton = contentView.findViewById(R.id.scroll_down);
        mLeftButton = contentView.findViewById(R.id.scroll_left);
        mRightButton = contentView.findViewById(R.id.scroll_right);
        mExitButton = contentView.findViewById(R.id.scroll_exit);
    }

    @Test
    public void show_addsViewToWindowManager() {
        mScrollPanel.show();

        // Verify view is added to window manager.
        verify(mMockWindowManager).addView(any(), any(WindowManager.LayoutParams.class));

        // Verify isVisible reflects correct state.
        assertThat(mScrollPanel.isVisible()).isTrue();
    }

    @Test
    public void show_alreadyVisible_doesNotAddAgain() {
        // Show twice.
        mScrollPanel.show();
        mScrollPanel.show();

        // Verify addView was only called once.
        verify(mMockWindowManager, times(1)).addView(any(), any());
    }

    @Test
    public void hide_removesViewFromWindowManager() {
        // First show the panel.
        mScrollPanel.show();
        // Then hide it.
        mScrollPanel.hide();
        // Verify view is removed from window manager.
        verify(mMockWindowManager).removeView(any());
        // Verify scroll panel is hidden.
        assertThat(mScrollPanel.isVisible()).isFalse();
    }

    @Test
    public void initialState_correctButtonVisibility() {
        // Verify all expected buttons exist in the view.
        assertThat(mUpButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mDownButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mLeftButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mRightButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mExitButton.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void directionButtons_hoverEvents_callsHoverButtonChange() {
        // Test hover enter on direction button.
        triggerHoverEvent(mUpButton, MotionEvent.ACTION_HOVER_ENTER);
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_UP), eq(/* hovered= */ true));

        // Test hover move.
        reset(mMockScrollPanelController);
        triggerHoverEvent(mUpButton, MotionEvent.ACTION_HOVER_MOVE);
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_UP), eq(/* hovered= */ true));

        // Test hover exit.
        reset(mMockScrollPanelController);
        triggerHoverEvent(mUpButton, MotionEvent.ACTION_HOVER_EXIT);
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_UP), eq(/* hovered= */ false));
    }

    @Test
    public void exitButton_hoverEvents_callsHoverButtonChange() {
        // Test hover enter on exit button.
        triggerHoverEvent(mExitButton, MotionEvent.ACTION_HOVER_ENTER);
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_EXIT), eq(/* hovered= */ true));

        // Test hover exit - should call the hover change method with false.
        reset(mMockScrollPanelController);
        triggerHoverEvent(mExitButton, MotionEvent.ACTION_HOVER_EXIT);
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_EXIT), eq(/* hovered= */ false));

        // Test exit button hover move - should be ignored.
        reset(mMockScrollPanelController);
        triggerHoverEvent(mExitButton, MotionEvent.ACTION_HOVER_MOVE);
        verify(mMockScrollPanelController, never()).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_EXIT), anyBoolean());
    }

    @Test
    public void hoverOnButtonSequence_handledCorrectly() {
        // Test a realistic sequence of events.
        // Case 1. Hover enter on up button, then hover move with in up button twice.
        reset(mMockScrollPanelController);
        triggerHoverEvent(mUpButton, MotionEvent.ACTION_HOVER_ENTER);
        triggerHoverEvent(mUpButton, MotionEvent.ACTION_HOVER_MOVE);
        triggerHoverEvent(mUpButton, MotionEvent.ACTION_HOVER_MOVE);
        verify(mMockScrollPanelController, times(3)).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_UP), eq(true));

        // Case 2. Move from left button to exit button.
        reset(mMockScrollPanelController);
        triggerHoverEvent(mLeftButton, MotionEvent.ACTION_HOVER_ENTER);
        triggerHoverEvent(mLeftButton, MotionEvent.ACTION_HOVER_MOVE);
        triggerHoverEvent(mLeftButton, MotionEvent.ACTION_HOVER_EXIT);
        triggerHoverEvent(mExitButton, MotionEvent.ACTION_HOVER_MOVE);
        triggerHoverEvent(mExitButton, MotionEvent.ACTION_HOVER_ENTER);
        triggerHoverEvent(mExitButton, MotionEvent.ACTION_HOVER_EXIT);

        // Verify left button events - 2 'true' calls (enter+move) and 1 'false' call (exit).
        verify(mMockScrollPanelController, times(2)).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_LEFT), eq(/* hovered= */ true));
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_LEFT), eq(/* hovered= */ false));
        // Verify exit button events - hover_move is ignored so 1 'true' call (enter) and 1
        // 'false' call (exit).
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_EXIT), eq(/* hovered= */ true));
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_EXIT), eq(/* hovered= */ false));

        // Case 3. Quick transitions between buttons: left → right → down → exit
        reset(mMockScrollPanelController);
        triggerHoverEvent(mLeftButton, MotionEvent.ACTION_HOVER_EXIT);
        triggerHoverEvent(mRightButton, MotionEvent.ACTION_HOVER_ENTER);
        triggerHoverEvent(mRightButton, MotionEvent.ACTION_HOVER_EXIT);
        triggerHoverEvent(mDownButton, MotionEvent.ACTION_HOVER_ENTER);
        triggerHoverEvent(mDownButton, MotionEvent.ACTION_HOVER_EXIT);
        triggerHoverEvent(mExitButton, MotionEvent.ACTION_HOVER_ENTER);

        // Verify all hover enter/exit events were properly handled
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_LEFT), eq(/* hovered= */ false));
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_RIGHT), eq(/* hovered= */ true));
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_RIGHT), eq(/* hovered= */ false));
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_DOWN), eq(/* hovered= */ true));
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_DOWN), eq(/* hovered= */ false));
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_EXIT), eq(/* hovered= */ true));
    }

    // Helper method to simulate a hover event on a view.
    private void triggerHoverEvent(View view, int action) {
        MotionEvent event = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 0,
                /* action= */ action,
                /* x= */ 0,
                /* y= */ 0,
                /* metaState= */ 0);

        // Dispatch the event to the view's OnHoverListener.
        view.dispatchGenericMotionEvent(event);
        event.recycle();
    }
}
