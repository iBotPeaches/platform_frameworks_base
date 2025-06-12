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

import static android.provider.Settings.Secure.ACCESSIBILITY_AUTOCLICK_PANEL_POSITION;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.server.accessibility.autoclick.AutoclickTypePanel.AUTOCLICK_TYPE_LEFT_CLICK;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.AUTOCLICK_TYPE_SCROLL;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.AutoclickType;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.CORNER_BOTTOM_LEFT;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.CORNER_BOTTOM_RIGHT;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.CORNER_TOP_LEFT;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.CORNER_TOP_RIGHT;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.ClickPanelControllerInterface;
import static com.android.server.accessibility.autoclick.AutoclickTypePanel.POSITION_DELIMITER;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.testing.TestableLooper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.android.internal.R;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Test cases for {@link AutoclickTypePanel}. */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class AutoclickTypePanelTest {
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule
    public TestableContext mTestableContext =
            new TestableContext(getInstrumentation().getContext());

    private AutoclickTypePanel mAutoclickTypePanel;
    @Mock private WindowManager mMockWindowManager;
    private LinearLayout mLeftClickButton;
    private LinearLayout mRightClickButton;
    private LinearLayout mDoubleClickButton;
    private LinearLayout mDragButton;
    private LinearLayout mScrollButton;
    private LinearLayout mPauseButton;
    private LinearLayout mPositionButton;

    private @AutoclickType int mActiveClickType = AUTOCLICK_TYPE_LEFT_CLICK;
    private boolean mPaused;
    private boolean mHovered;

    private final ClickPanelControllerInterface clickPanelController =
            new ClickPanelControllerInterface() {
                @Override
                public void handleAutoclickTypeChange(@AutoclickType int clickType) {
                    mActiveClickType = clickType;
                }

                @Override
                public void toggleAutoclickPause(boolean paused) {
                    mPaused = paused;
                }

                @Override
                public void onHoverChange(boolean hovered) {
                    mHovered = hovered;
                }
            };

    @Before
    public void setUp() {
        mTestableContext.addMockSystemService(Context.WINDOW_SERVICE, mMockWindowManager);

        mAutoclickTypePanel =
                new AutoclickTypePanel(mTestableContext, mMockWindowManager,
                        mTestableContext.getUserId(), clickPanelController);
        View contentView = mAutoclickTypePanel.getContentViewForTesting();
        mLeftClickButton = contentView.findViewById(R.id.accessibility_autoclick_left_click_layout);
        mRightClickButton =
                contentView.findViewById(R.id.accessibility_autoclick_right_click_layout);
        mDoubleClickButton =
                contentView.findViewById(R.id.accessibility_autoclick_double_click_layout);
        mScrollButton = contentView.findViewById(R.id.accessibility_autoclick_scroll_layout);
        mDragButton = contentView.findViewById(R.id.accessibility_autoclick_drag_layout);
        mPauseButton = contentView.findViewById(R.id.accessibility_autoclick_pause_layout);
        mPositionButton = contentView.findViewById(R.id.accessibility_autoclick_position_layout);
    }

    @Test
    public void AutoclickTypePanel_initialState_expandedFalse() {
        assertThat(mAutoclickTypePanel.getExpansionStateForTesting()).isFalse();
    }

    @Test
    public void AutoclickTypePanel_initialState_correctButtonVisibility() {
        // On initialization, only left button is visible.
        assertThat(mLeftClickButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mRightClickButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(mDoubleClickButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(mDragButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(mScrollButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(mPauseButton.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void AutoclickTypePanel_initialState_correctButtonStyle() {
        verifyButtonHasSelectedStyle(mLeftClickButton);
    }

    @Test
    public void togglePanelExpansion_onClick_expandedTrue() {
        // On clicking left click button, the panel is expanded and all buttons are visible.
        mLeftClickButton.callOnClick();

        assertThat(mAutoclickTypePanel.getExpansionStateForTesting()).isTrue();
        assertThat(mLeftClickButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mRightClickButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mDoubleClickButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mDragButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mScrollButton.getVisibility()).isEqualTo(View.VISIBLE);

        // Pause button is always visible.
        assertThat(mPauseButton.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void togglePanelExpansion_onClickAgain_expandedFalse() {
        // By first click, the panel is expanded.
        mLeftClickButton.callOnClick();
        assertThat(mAutoclickTypePanel.getExpansionStateForTesting()).isTrue();

        // Clicks any button in the expanded state, the panel is expected to collapse
        // with only the clicked button visible.
        mScrollButton.callOnClick();

        assertThat(mAutoclickTypePanel.getExpansionStateForTesting()).isFalse();
        assertThat(mScrollButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mRightClickButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(mLeftClickButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(mDoubleClickButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(mDragButton.getVisibility()).isEqualTo(View.GONE);

        // Pause button is always visible.
        assertThat(mPauseButton.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void togglePanelExpansion_selectButton_correctStyle() {
        // By first click, the panel is expanded.
        mLeftClickButton.callOnClick();

        // Clicks any button in the expanded state to select a type button.
        mScrollButton.callOnClick();

        verifyButtonHasSelectedStyle(mScrollButton);
    }

    @Test
    public void togglePanelExpansion_selectButton_correctActiveClickType() {
        // By first click, the panel is expanded.
        mLeftClickButton.callOnClick();

        // Clicks any button in the expanded state to select a type button.
        mScrollButton.callOnClick();

        assertThat(mActiveClickType).isEqualTo(AUTOCLICK_TYPE_SCROLL);
    }

    @Test
    public void clickLeftClickButton_resumeAutoClick() {
        // Pause autoclick.
        mPauseButton.callOnClick();
        assertThat(mAutoclickTypePanel.isPaused()).isTrue();

        // Click the button and verify autoclick resumes.
        mLeftClickButton.callOnClick();
        assertThat(mAutoclickTypePanel.isPaused()).isFalse();
    }

    @Test
    public void clickRightClickButton_resumeAutoClick() {
        // Pause autoclick.
        mPauseButton.callOnClick();
        assertThat(mAutoclickTypePanel.isPaused()).isTrue();

        // Click the button and verify autoclick resumes.
        mRightClickButton.callOnClick();
        assertThat(mAutoclickTypePanel.isPaused()).isFalse();
    }

    @Test
    public void clickDoubleClickButton_resumeAutoClick() {
        // Pause autoclick.
        mPauseButton.callOnClick();
        assertThat(mAutoclickTypePanel.isPaused()).isTrue();

        // Click the button and verify autoclick resumes.
        mDoubleClickButton.callOnClick();
        assertThat(mAutoclickTypePanel.isPaused()).isFalse();
    }

    @Test
    public void clickDragButton_resumeAutoClick() {
        // Pause autoclick.
        mPauseButton.callOnClick();
        assertThat(mAutoclickTypePanel.isPaused()).isTrue();

        // Click the button and verify autoclick resumes.
        mDragButton.callOnClick();
        assertThat(mAutoclickTypePanel.isPaused()).isFalse();
    }

    @Test
    public void clickScrollButton_resumeAutoClick() {
        // Pause autoclick.
        mPauseButton.callOnClick();
        assertThat(mAutoclickTypePanel.isPaused()).isTrue();

        // Click the button and verify autoclick resumes.
        mScrollButton.callOnClick();
        assertThat(mAutoclickTypePanel.isPaused()).isFalse();
    }

    @Test
    public void clickPositionButton_resumeAutoClick() {
        // Pause autoclick.
        mPauseButton.callOnClick();
        assertThat(mAutoclickTypePanel.isPaused()).isTrue();

        // Click the button and verify autoclick resumes.
        mPositionButton.callOnClick();
        assertThat(mAutoclickTypePanel.isPaused()).isFalse();
    }

    @Test
    public void moveToNextCorner_positionButton_rotatesThroughAllPositions() {
        // Define all positions in sequence
        int[][] expectedPositions = {
                {CORNER_BOTTOM_RIGHT, Gravity.END | Gravity.BOTTOM, /*x=*/ 15, /*y=*/ 90},
                {CORNER_BOTTOM_LEFT, Gravity.START | Gravity.BOTTOM, /*x=*/ 15, /*y=*/ 90},
                {CORNER_TOP_LEFT, Gravity.START | Gravity.TOP, /*x=*/ 15, /*y=*/ 30},
                {CORNER_TOP_RIGHT, Gravity.END | Gravity.TOP, /*x=*/ 15, /*y=*/ 30},
                {CORNER_BOTTOM_RIGHT, Gravity.END | Gravity.BOTTOM, /*x=*/ 15, /*y=*/ 90}
        };

        // Check initial position
        verifyPanelPosition(expectedPositions[0]);

        // Move through all corners.
        for (int i = 1; i < expectedPositions.length; i++) {
            mPositionButton.callOnClick();
            verifyPanelPosition(expectedPositions[i]);
        }
    }

    @Test
    public void pauseButton_onClick() {
        mPauseButton.callOnClick();
        assertThat(mPaused).isTrue();
        assertThat(mAutoclickTypePanel.isPaused()).isTrue();

        mPauseButton.callOnClick();
        assertThat(mPaused).isFalse();
        assertThat(mAutoclickTypePanel.isPaused()).isFalse();
    }

    @Test
    public void onTouch_dragMove_updatesPosition() {
        View contentView = mAutoclickTypePanel.getContentViewForTesting();
        WindowManager.LayoutParams params = mAutoclickTypePanel.getLayoutParamsForTesting();
        int[] panelLocation = new int[2];
        contentView.getLocationOnScreen(panelLocation);

        // Define movement delta for both x and y directions.
        int delta = 15;

        // Dispatch initial down event.
        float touchX = panelLocation[0] + 10;
        float touchY = panelLocation[1] + 10;
        MotionEvent downEvent = MotionEvent.obtain(
                0, 0,
                MotionEvent.ACTION_DOWN, touchX, touchY, 0);
        contentView.dispatchTouchEvent(downEvent);

        // Create move event with delta, move from (x, y) to (x + delta, y + delta)
        MotionEvent moveEvent = MotionEvent.obtain(
                0, 0,
                MotionEvent.ACTION_MOVE, touchX + delta, touchY + delta, 0);
        contentView.dispatchTouchEvent(moveEvent);

        // Verify position update.
        assertThat(mAutoclickTypePanel.getIsDraggingForTesting()).isTrue();
        assertThat(params.gravity).isEqualTo(Gravity.LEFT | Gravity.TOP);
        assertThat(params.x).isEqualTo(panelLocation[0] + delta);
        assertThat(params.y).isEqualTo(panelLocation[1] + delta);
    }

    @Test
    public void dragAndEndAtRight_snapsToRightSide() {
        View contentView = mAutoclickTypePanel.getContentViewForTesting();
        WindowManager.LayoutParams params = mAutoclickTypePanel.getLayoutParamsForTesting();
        int[] panelLocation = new int[2];
        contentView.getLocationOnScreen(panelLocation);

        int screenWidth = mTestableContext.getResources().getDisplayMetrics().widthPixels;

        // Verify initial corner is bottom-right.
        assertThat(mAutoclickTypePanel.getCurrentCornerForTesting())
                .isEqualTo(CORNER_BOTTOM_RIGHT);

        dispatchDragSequence(contentView,
                /* startX =*/ panelLocation[0] + 10, /* startY =*/ panelLocation[1] + 10,
                /* endX =*/ (float) (screenWidth * 3) / 4, /* endY =*/ panelLocation[1] + 10);

        // Verify snapping to the right.
        assertThat(params.gravity).isEqualTo(Gravity.END | Gravity.TOP);
        assertThat(mAutoclickTypePanel.getCurrentCornerForTesting())
                .isEqualTo(CORNER_TOP_RIGHT);
    }

    @Test
    public void dragAndEndAtLeft_snapsToLeftSide() {
        View contentView = mAutoclickTypePanel.getContentViewForTesting();
        WindowManager.LayoutParams params = mAutoclickTypePanel.getLayoutParamsForTesting();
        int[] panelLocation = new int[2];
        contentView.getLocationOnScreen(panelLocation);

        int screenWidth = mTestableContext.getResources().getDisplayMetrics().widthPixels;

        // Verify initial corner is bottom-right.
        assertThat(mAutoclickTypePanel.getCurrentCornerForTesting())
                .isEqualTo(CORNER_BOTTOM_RIGHT);

        dispatchDragSequence(contentView,
                /* startX =*/ panelLocation[0] + 10, /* startY =*/ panelLocation[1] + 10,
                /* endX =*/ (float) screenWidth / 4, /* endY =*/ panelLocation[1] + 10);

        // Verify snapping to the left.
        assertThat(params.gravity).isEqualTo(Gravity.START | Gravity.TOP);
        assertThat(mAutoclickTypePanel.getCurrentCornerForTesting())
                .isEqualTo(CORNER_BOTTOM_LEFT);
    }

    @Test
    public void restorePanelPosition_noSavedPosition_useDefault() {
        // Given no saved position in Settings.
        Settings.Secure.putString(mTestableContext.getContentResolver(),
                ACCESSIBILITY_AUTOCLICK_PANEL_POSITION, null);

        // Create panel which triggers position restoration internally.
        AutoclickTypePanel panel = new AutoclickTypePanel(mTestableContext, mMockWindowManager,
                mTestableContext.getUserId(),
                clickPanelController);

        // Verify panel is positioned at default bottom-right corner.
        WindowManager.LayoutParams params = panel.getLayoutParamsForTesting();
        assertThat(panel.getCurrentCornerForTesting()).isEqualTo(CORNER_BOTTOM_RIGHT);
        assertThat(params.gravity).isEqualTo(Gravity.END | Gravity.BOTTOM);
        assertThat(params.x).isEqualTo(15);  // Default edge margin.
        assertThat(params.y).isEqualTo(90);  // Default bottom offset.
    }

    @Test
    public void restorePanelPosition_position_button() {
        // Move panel to top-left by clicking position button twice.
        mPositionButton.callOnClick();
        mPositionButton.callOnClick();

        // Hide panel to trigger position saving.
        mAutoclickTypePanel.hide();

        // Verify position is correctly saved in Settings.
        String savedPosition = Settings.Secure.getStringForUser(
                mTestableContext.getContentResolver(),
                ACCESSIBILITY_AUTOCLICK_PANEL_POSITION, mTestableContext.getUserId());
        String[] parts = savedPosition.split(POSITION_DELIMITER);
        assertThat(parts).hasLength(4);
        assertThat(Integer.parseInt(parts[0])).isEqualTo(Gravity.START | Gravity.TOP);
        assertThat(Integer.parseInt(parts[1])).isEqualTo(15);
        assertThat(Integer.parseInt(parts[2])).isEqualTo(30);
        assertThat(Integer.parseInt(parts[3])).isEqualTo(CORNER_TOP_LEFT);

        // Show panel to trigger position restoration.
        mAutoclickTypePanel.show();

        // Then verify position is restored correctly.
        WindowManager.LayoutParams params = mAutoclickTypePanel.getLayoutParamsForTesting();
        assertThat(params.gravity).isEqualTo(Gravity.START | Gravity.TOP);
        assertThat(params.x).isEqualTo(15);
        assertThat(params.y).isEqualTo(30);
        assertThat(mAutoclickTypePanel.getCurrentCornerForTesting()).isEqualTo(
                CORNER_TOP_LEFT);
    }

    @Test
    public void restorePanelPosition_dragToLeft() {
        // Get initial panel position.
        View contentView = mAutoclickTypePanel.getContentViewForTesting();
        int[] panelLocation = new int[2];
        contentView.getLocationOnScreen(panelLocation);

        // Simulate drag from initial position to left side of screen.
        int screenWidth = mTestableContext.getResources().getDisplayMetrics().widthPixels;
        dispatchDragSequence(contentView,
                /* startX =*/ panelLocation[0], /* startY =*/ panelLocation[1],
                /* endX =*/ (float) screenWidth / 4, /* endY =*/ panelLocation[1] + 10);

        // Hide panel to trigger position saving.
        mAutoclickTypePanel.hide();

        // Verify position is saved correctly.
        String savedPosition = Settings.Secure.getStringForUser(
                mTestableContext.getContentResolver(),
                ACCESSIBILITY_AUTOCLICK_PANEL_POSITION, mTestableContext.getUserId());
        String[] parts = savedPosition.split(POSITION_DELIMITER);
        assertThat(parts).hasLength(4);
        assertThat(Integer.parseInt(parts[0])).isEqualTo(Gravity.START | Gravity.TOP);
        assertThat(Integer.parseInt(parts[1])).isEqualTo(15);
        assertThat(Integer.parseInt(parts[2])).isEqualTo(panelLocation[1] + 10);
        assertThat(Integer.parseInt(parts[3])).isEqualTo(CORNER_BOTTOM_LEFT);

        // Show panel to trigger position restoration.
        mAutoclickTypePanel.show();

        // Then verify dragged position is restored.
        WindowManager.LayoutParams params = mAutoclickTypePanel.getLayoutParamsForTesting();
        assertThat(params.gravity).isEqualTo(Gravity.START | Gravity.TOP);
        assertThat(params.x).isEqualTo(15); // PANEL_EDGE_MARGIN
        assertThat(params.y).isEqualTo(panelLocation[1] + 10);
        assertThat(mAutoclickTypePanel.getCurrentCornerForTesting()).isEqualTo(
                CORNER_BOTTOM_LEFT);
    }

    // Helper method to handle drag event sequences
    private void dispatchDragSequence(View view, float startX, float startY, float endX,
            float endY) {
        // Down event
        MotionEvent downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, startX, startY,
                0);
        view.dispatchTouchEvent(downEvent);

        // Move event
        MotionEvent moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, endX, endY, 0);
        view.dispatchTouchEvent(moveEvent);

        // Up event
        MotionEvent upEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, endX, endY, 0);
        view.dispatchTouchEvent(upEvent);

        // Clean up
        downEvent.recycle();
        moveEvent.recycle();
        upEvent.recycle();
    }

    @Test
    public void hovered_IsHovered() {
        AutoclickLinearLayout mContext = mAutoclickTypePanel.getContentViewForTesting();

        assertThat(mAutoclickTypePanel.isHovered()).isFalse();
        mContext.onInterceptHoverEvent(getFakeMotionHoverMoveEvent());
        assertThat(mAutoclickTypePanel.isHovered()).isTrue();
    }

    @Test
    public void hovered_OnHoverChange_isHovered() {
        AutoclickLinearLayout mContext = mAutoclickTypePanel.getContentViewForTesting();

        mHovered = false;
        mContext.onHoverChanged(true);
        assertThat(mHovered).isTrue();
    }

    @Test
    public void hovered_OnHoverChange_isNotHovered() {
        AutoclickLinearLayout mContext = mAutoclickTypePanel.getContentViewForTesting();

        mHovered = true;
        mContext.onHoverChanged(false);
        assertThat(mHovered).isFalse();
    }

    private void verifyButtonHasSelectedStyle(@NonNull LinearLayout button) {
        GradientDrawable gradientDrawable = (GradientDrawable) button.getBackground();
        assertThat(gradientDrawable.getColor().getDefaultColor())
                .isEqualTo(mTestableContext.getColor(R.color.materialColorPrimary));
    }

    private void verifyPanelPosition(int[] expectedPosition) {
        WindowManager.LayoutParams params = mAutoclickTypePanel.getLayoutParamsForTesting();
        assertThat(mAutoclickTypePanel.getCurrentCornerForTesting()).isEqualTo(
                expectedPosition[0]);
        assertThat(params.gravity).isEqualTo(expectedPosition[1]);
        assertThat(params.x).isEqualTo(expectedPosition[2]);
        assertThat(params.y).isEqualTo(expectedPosition[3]);
    }

    private MotionEvent getFakeMotionHoverMoveEvent() {
        return MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 0,
                /* action= */ MotionEvent.ACTION_HOVER_MOVE,
                /* x= */ 0,
                /* y= */ 0,
                /* metaState= */ 0);
    }
}
