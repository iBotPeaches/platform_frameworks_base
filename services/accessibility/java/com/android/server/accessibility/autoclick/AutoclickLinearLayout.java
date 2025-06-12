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

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

/**
 * A custom LinearLayout that provides enhanced hover event handling.
 * This class overrides hover methods to track hover events for the entire panel ViewGroup,
 * including the descendant buttons. This allows for consistent hover behavior and feedback
 * across the entire layout.
 */
public class AutoclickLinearLayout extends LinearLayout {
    public interface OnHoverChangedListener {
        /**
         * Called when the hover state of the AutoclickLinearLayout changes.
         *
         * @param hovered {@code true} if the view is now hovered, {@code false} otherwise.
         */
        void onHoverChanged(boolean hovered);
    }

    private OnHoverChangedListener mListener;

    public AutoclickLinearLayout(Context context) {
        super(context);
    }

    public AutoclickLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoclickLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AutoclickLinearLayout(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setOnHoverChangedListener(OnHoverChangedListener listener) {
        mListener = listener;
    }

    @Override
    public boolean onInterceptHoverEvent(MotionEvent event) {
        int action = event.getActionMasked();
        setHovered(action == MotionEvent.ACTION_HOVER_ENTER
                || action == MotionEvent.ACTION_HOVER_MOVE);

        return false;
    }

    @Override
    public void onHoverChanged(boolean hovered) {
        super.onHoverChanged(hovered);

        if (mListener != null) {
            mListener.onHoverChanged(hovered);
        }
    }
}
