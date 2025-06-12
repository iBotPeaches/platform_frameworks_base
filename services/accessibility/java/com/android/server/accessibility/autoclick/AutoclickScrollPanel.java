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

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import android.annotation.IntDef;
import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class AutoclickScrollPanel {
    public static final int DIRECTION_UP = 0;
    public static final int DIRECTION_DOWN = 1;
    public static final int DIRECTION_LEFT = 2;
    public static final int DIRECTION_RIGHT = 3;
    public static final int DIRECTION_EXIT = 4;
    public static final int DIRECTION_NONE = 5;

    @IntDef({
            DIRECTION_UP,
            DIRECTION_DOWN,
            DIRECTION_LEFT,
            DIRECTION_RIGHT,
            DIRECTION_EXIT,
            DIRECTION_NONE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScrollDirection {}

    private final Context mContext;
    private final View mContentView;
    private final WindowManager mWindowManager;
    private ScrollPanelControllerInterface mScrollPanelController;

    // Scroll panel buttons.
    private final ImageButton mUpButton;
    private final ImageButton mDownButton;
    private final ImageButton mLeftButton;
    private final ImageButton mRightButton;
    private final ImageButton mExitButton;

    private boolean mInScrollMode = false;

    /**
     * Interface for handling scroll operations.
     */
    public interface ScrollPanelControllerInterface {
        /**
         * Called when a button hover state changes.
         *
         * @param direction The direction associated with the button.
         * @param hovered Whether the button is being hovered or not.
         */
        void onHoverButtonChange(@ScrollDirection int direction, boolean hovered);
    }

    public AutoclickScrollPanel(Context context, WindowManager windowManager,
            ScrollPanelControllerInterface controller) {
        mContext = context;
        mWindowManager = windowManager;
        mScrollPanelController = controller;
        mContentView = LayoutInflater.from(context).inflate(
                R.layout.accessibility_autoclick_scroll_panel, null);

        // Initialize buttons.
        mUpButton = mContentView.findViewById(R.id.scroll_up);
        mLeftButton = mContentView.findViewById(R.id.scroll_left);
        mRightButton = mContentView.findViewById(R.id.scroll_right);
        mDownButton = mContentView.findViewById(R.id.scroll_down);
        mExitButton = mContentView.findViewById(R.id.scroll_exit);

        initializeButtonState();
    }

    /**
     * Sets up hover listeners for scroll panel buttons.
     */
    private void initializeButtonState() {
        // Set up hover listeners for all buttons.
        setupHoverListenerForButton(mUpButton, DIRECTION_UP);
        setupHoverListenerForButton(mLeftButton, DIRECTION_LEFT);
        setupHoverListenerForButton(mRightButton, DIRECTION_RIGHT);
        setupHoverListenerForButton(mDownButton, DIRECTION_DOWN);
        setupHoverListenerForButton(mExitButton, DIRECTION_EXIT);
    }

    /**
     * Shows the autoclick scroll panel.
     */
    public void show() {
        if (mInScrollMode) {
            return;
        }
        mWindowManager.addView(mContentView, getLayoutParams());
        mInScrollMode = true;
    }

    /**
     * Hides the autoclick scroll panel.
     */
    public void hide() {
        if (!mInScrollMode) {
            return;
        }
        mWindowManager.removeView(mContentView);
        mInScrollMode = false;
    }

    /**
     * Sets up a hover listener for a button.
     */
    private void setupHoverListenerForButton(ImageButton button, @ScrollDirection int direction) {
        button.setOnHoverListener((v, event) -> {
            if (mScrollPanelController == null) {
                return true;
            }

            boolean hovered;
            switch (event.getAction()) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    hovered = true;
                    break;
                case MotionEvent.ACTION_HOVER_MOVE:
                    // For direction buttons, continuously trigger scroll on hover move.
                    if (direction != DIRECTION_EXIT) {
                        hovered = true;
                    } else {
                        // Ignore hover move events for exit button.
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    hovered = false;
                    break;
                default:
                    return true;
            }

            // Notify the controller about the hover change.
            mScrollPanelController.onHoverButtonChange(direction, hovered);
            return true;
        });
    }

    /**
     * Retrieves the layout params for AutoclickScrollPanel, used when it's added to the Window
     * Manager.
     */
    @NonNull
    private WindowManager.LayoutParams getLayoutParams() {
        final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParams.privateFlags |= WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        layoutParams.setFitInsetsTypes(WindowInsets.Type.statusBars());
        layoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        layoutParams.format = PixelFormat.TRANSLUCENT;
        layoutParams.setTitle(AutoclickScrollPanel.class.getSimpleName());
        layoutParams.accessibilityTitle =
                mContext.getString(R.string.accessibility_autoclick_scroll_panel_title);
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.CENTER;
        return layoutParams;
    }

    @VisibleForTesting
    public boolean isVisible() {
        return mInScrollMode;
    }

    @VisibleForTesting
    public View getContentViewForTesting() {
        return mContentView;
    }

    @VisibleForTesting
    public WindowManager.LayoutParams getLayoutParamsForTesting() {
        return getLayoutParams();
    }
}
