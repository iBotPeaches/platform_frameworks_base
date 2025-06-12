/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.shell;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Preferences related to bug reports.
 */
final class BugreportPrefs {

    static int getWarningState(Context context, int def) {
        String prefsBugreport = context.getResources().getString(
                com.android.internal.R.string.prefs_bugreport);
        String keyWarningState = context.getResources().getString(
                com.android.internal.R.string.key_warning_state);
        final SharedPreferences prefs = context.getSharedPreferences(prefsBugreport,
                Context.MODE_PRIVATE);
        return prefs.getInt(keyWarningState, def);
    }

    static void setWarningState(Context context, int value) {
        String prefsBugreport = context.getResources().getString(
                com.android.internal.R.string.prefs_bugreport);
        String keyWarningState = context.getResources().getString(
                com.android.internal.R.string.key_warning_state);
        final SharedPreferences prefs = context.getSharedPreferences(prefsBugreport,
                Context.MODE_PRIVATE);
        prefs.edit().putInt(keyWarningState, value).apply();
    }
}
