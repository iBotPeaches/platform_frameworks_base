/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.collection

import android.app.Flags
import com.android.systemui.util.time.SystemClock

/** A helper class for replacing uptimeMillis with elapsedRealtime for entry creation times */
public object UseElapsedRealtimeForCreationTime {
    @JvmStatic
    fun getCurrentTime(clock: SystemClock): Long {
        if (Flags.notifEntryCreationTimeUseElapsedRealtime()) {
            return clock.elapsedRealtime()
        }
        return clock.uptimeMillis()
    }

    @JvmStatic
    fun getCurrentTime(): Long {
        if (Flags.notifEntryCreationTimeUseElapsedRealtime()) {
            return android.os.SystemClock.elapsedRealtime()
        }
        return android.os.SystemClock.uptimeMillis()

    }
}
