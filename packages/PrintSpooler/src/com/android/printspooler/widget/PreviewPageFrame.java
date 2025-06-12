/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.printspooler.widget;

import static android.view.accessibility.Flags.triStateChecked;

import android.content.Context;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

/**
 * This class represents the frame of page in the print preview list
 * that contains the page and a footer.
 */
public final class PreviewPageFrame extends LinearLayout {
    public PreviewPageFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return CompoundButton.class.getName();
    }

    @Override
    public boolean performClick() {
        final boolean result = super.performClick();
        // This widget is incorrectly using the notion of "selection"
        // to represent checked state. We can't send this event in
        // setSelected() because setSelected() is called when this widget
        // is not attached.
        if (triStateChecked()) {
            notifyViewAccessibilityStateChangedIfNeeded(
                    AccessibilityEvent.CONTENT_CHANGE_TYPE_CHECKED);
        }
        return result;
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setChecked(isSelected());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setSelected(false);
        info.setCheckable(true);
        if (triStateChecked()) {
            info.setChecked(isSelected() ? AccessibilityNodeInfo.CHECKED_STATE_TRUE :
                    AccessibilityNodeInfo.CHECKED_STATE_FALSE);
        } else {
            info.setChecked(isSelected());
        }
    }
}
