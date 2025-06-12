/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.apps.inputmethod.simpleime;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** A simple implementation of a software keyboard view. */
final class SimpleKeyboardView extends FrameLayout {

    private static final String TAG = "SimpleKeyboard";

    private static final int[] SOFT_KEY_IDS = new int[]{
            R.id.key_pos_0_0,
            R.id.key_pos_0_1,
            R.id.key_pos_0_2,
            R.id.key_pos_0_3,
            R.id.key_pos_0_4,
            R.id.key_pos_0_5,
            R.id.key_pos_0_6,
            R.id.key_pos_0_7,
            R.id.key_pos_0_8,
            R.id.key_pos_0_9,
            R.id.key_pos_1_0,
            R.id.key_pos_1_1,
            R.id.key_pos_1_2,
            R.id.key_pos_1_3,
            R.id.key_pos_1_4,
            R.id.key_pos_1_5,
            R.id.key_pos_1_6,
            R.id.key_pos_1_7,
            R.id.key_pos_1_8,
            R.id.key_pos_2_0,
            R.id.key_pos_2_1,
            R.id.key_pos_2_2,
            R.id.key_pos_2_3,
            R.id.key_pos_2_4,
            R.id.key_pos_2_5,
            R.id.key_pos_2_6,
            R.id.key_pos_shift,
            R.id.key_pos_del,
            R.id.key_pos_symbol,
            R.id.key_pos_comma,
            R.id.key_pos_space,
            R.id.key_pos_period,
            R.id.key_pos_enter,
    };

    private final SparseArray<TextView> mSoftKeyViews = new SparseArray<>();

    @FunctionalInterface
    interface KeyPressListener {

        /**
         * Called when a key is pressed.
         *
         * @param keyCodeName the keycode of the key, as a string.
         * @param metaState   the flags indicating which meta keys are currently pressed.
         */
        void onKeyPress(@NonNull String keyCodeName, int metaState);
    }

    /** A listener to react to key presses. */
    @Nullable
    private KeyPressListener mKeyPressListener;

    /** The flags indicating which meta keys are currently pressed. */
    private int mMetaState;

    SimpleKeyboardView(@NonNull Context context) {
        this(context, null);
    }

    SimpleKeyboardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0 /* defStyleAttr */);
    }

    SimpleKeyboardView(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr, 0 /* defStyleRes */);
        LayoutInflater.from(context).inflate(R.layout.qwerty_10_9_9, this, true);
        mapSoftKeys();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        // Handle edge to edge for navigationBars insets (system nav bar)
        // and captionBars insets (IME navigation bar).
        final int insetBottom = insets.getInsets(WindowInsets.Type.systemBars()).bottom;
        setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), insetBottom);
        return insets.inset(0, 0, 0, insetBottom);
    }

    /**
     * Sets the key press listener.
     *
     * @param listener the listener to set.
     */
    void setKeyPressListener(@NonNull KeyPressListener listener) {
        mKeyPressListener = listener;
    }

    /** Maps the soft key ids to their corresponding views, and sets their on click listener. */
    private void mapSoftKeys() {
        for (final int id : SOFT_KEY_IDS) {
            final TextView softKeyView = requireViewById(id);
            mSoftKeyViews.put(id, softKeyView);
            final var keyCodeName = softKeyView.getTag() != null
                    ? softKeyView.getTag().toString() : null;
            softKeyView.setOnClickListener(v -> onKeyPress(keyCodeName));
        }
    }

    /**
     * Called when a key is pressed.
     *
     * @param keyCodeName the keycode of the key, as a string.
     */
    private void onKeyPress(@Nullable String keyCodeName) {
        Log.i(TAG, "onKeyPress: " + keyCodeName);
        if (TextUtils.isEmpty(keyCodeName)) {
            return;
        }
        if ("KEYCODE_SHIFT".equals(keyCodeName)) {
            onShiftPress();
            return;
        }

        if (mKeyPressListener != null) {
            mKeyPressListener.onKeyPress(keyCodeName, mMetaState);
        }
    }

    /**
     * Called when the shift key is pressed. This will toggle the capitalization of all the keys.
     */
    private void onShiftPress() {
        mMetaState = toggleShiftState(mMetaState);
        Log.v(TAG, "onShiftPress, new metaState: " + mMetaState);
        final boolean isShiftOn = isShiftOn(mMetaState);
        for (int i = 0; i < mSoftKeyViews.size(); i++) {
            final TextView softKeyView = mSoftKeyViews.valueAt(i);
            softKeyView.setAllCaps(isShiftOn);
        }
    }

    /**
     * Checks whether the shift meta key is pressed.
     *
     * @param state the flags indicating which meta keys are currently pressed.
     */
    private static boolean isShiftOn(int state) {
        return (state & KeyEvent.META_SHIFT_ON) == KeyEvent.META_SHIFT_ON;
    }

    /**
     * Toggles the value of the shift meta key being pressed.
     *
     * @param state the flags indicating which meta keys are currently pressed.
     * @return a new flag state, with the shift meta key value flipped.
     */
    private static int toggleShiftState(int state) {
        return state ^ KeyEvent.META_SHIFT_ON;
    }
}
