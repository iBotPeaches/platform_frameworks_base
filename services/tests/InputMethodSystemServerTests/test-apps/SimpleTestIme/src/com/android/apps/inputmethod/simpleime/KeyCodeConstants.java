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

import android.view.KeyEvent;

import androidx.annotation.NonNull;

/** Holder of key codes and their name. */
final class KeyCodeConstants {

    private KeyCodeConstants() {
    }

    /**
     * Returns the keyCode value corresponding to the given keyCode name.
     *
     * @param keyCodeName the keyCode name.
     */
    static int getKeyCode(@NonNull String keyCodeName) {
        return switch (keyCodeName) {
            case "KEYCODE_A" -> KeyEvent.KEYCODE_A;
            case "KEYCODE_B" -> KeyEvent.KEYCODE_B;
            case "KEYCODE_C" -> KeyEvent.KEYCODE_C;
            case "KEYCODE_D" -> KeyEvent.KEYCODE_D;
            case "KEYCODE_E" -> KeyEvent.KEYCODE_E;
            case "KEYCODE_F" -> KeyEvent.KEYCODE_F;
            case "KEYCODE_G" -> KeyEvent.KEYCODE_G;
            case "KEYCODE_H" -> KeyEvent.KEYCODE_H;
            case "KEYCODE_I" -> KeyEvent.KEYCODE_I;
            case "KEYCODE_J" -> KeyEvent.KEYCODE_J;
            case "KEYCODE_K" -> KeyEvent.KEYCODE_K;
            case "KEYCODE_L" -> KeyEvent.KEYCODE_L;
            case "KEYCODE_M" -> KeyEvent.KEYCODE_M;
            case "KEYCODE_N" -> KeyEvent.KEYCODE_N;
            case "KEYCODE_O" -> KeyEvent.KEYCODE_O;
            case "KEYCODE_P" -> KeyEvent.KEYCODE_P;
            case "KEYCODE_Q" -> KeyEvent.KEYCODE_Q;
            case "KEYCODE_R" -> KeyEvent.KEYCODE_R;
            case "KEYCODE_S" -> KeyEvent.KEYCODE_S;
            case "KEYCODE_T" -> KeyEvent.KEYCODE_T;
            case "KEYCODE_U" -> KeyEvent.KEYCODE_U;
            case "KEYCODE_V" -> KeyEvent.KEYCODE_V;
            case "KEYCODE_W" -> KeyEvent.KEYCODE_W;
            case "KEYCODE_X" -> KeyEvent.KEYCODE_X;
            case "KEYCODE_Y" -> KeyEvent.KEYCODE_Y;
            case "KEYCODE_Z" -> KeyEvent.KEYCODE_Z;
            case "KEYCODE_SHIFT" -> KeyEvent.KEYCODE_SHIFT_LEFT;
            case "KEYCODE_DEL" -> KeyEvent.KEYCODE_DEL;
            case "KEYCODE_SPACE" -> KeyEvent.KEYCODE_SPACE;
            case "KEYCODE_ENTER" -> KeyEvent.KEYCODE_ENTER;
            case "KEYCODE_COMMA" -> KeyEvent.KEYCODE_COMMA;
            case "KEYCODE_PERIOD" -> KeyEvent.KEYCODE_PERIOD;
            default -> KeyEvent.KEYCODE_UNKNOWN;
        };
    }

    static boolean isAlphaKeyCode(int keyCode) {
        return keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z;
    }
}
