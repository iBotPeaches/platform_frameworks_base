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

import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.apps.inputmethod.simpleime.ims.InputMethodServiceWrapper;

/** A simple implementation of an {@link InputMethodService}. */
public final class SimpleInputMethodService extends InputMethodServiceWrapper {

    private static final String TAG = "SimpleIMS";

    @Override
    public View onCreateInputView() {
        Log.i(TAG, "onCreateInputView()");
        final var simpleKeyboard = new SimpleKeyboardView(this);
        simpleKeyboard.setKeyPressListener(this::onKeyPress);
        return simpleKeyboard;
    }

    /**
     * Called when a key is pressed.
     *
     * @param keyCodeName the keycode of the key, as a string.
     * @param metaState   the flags indicating which meta keys are currently pressed.
     */
    private void onKeyPress(@NonNull String keyCodeName, int metaState) {
        final int keyCode = KeyCodeConstants.getKeyCode(keyCodeName);
        Log.v(TAG, "onKeyPress: " + keyCode);
        if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
            final var ic = getCurrentInputConnection();
            if (ic != null) {
                final var downTime = SystemClock.uptimeMillis();
                ic.sendKeyEvent(new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode,
                        0 /* repeat */, KeyCodeConstants.isAlphaKeyCode(keyCode) ? metaState : 0));
            }
        }
    }
}
