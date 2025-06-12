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

package com.android.settingslib.notification.modes;

import android.service.notification.ZenModeConfig;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ZenModeSchedules {

    /**
     * Returns the {@link ZenModeConfig.ScheduleInfo} time schedule corresponding to the mode, or
     * {@code null} if the mode is not time-schedule-based.
     */
    @Nullable
    public static ZenModeConfig.ScheduleInfo getTimeSchedule(@NonNull ZenMode mode) {
        return ZenModeConfig.tryParseScheduleConditionId(mode.getRule().getConditionId());
    }

    /**
     * Returns the {@link ZenModeConfig.EventInfo} calendar schedule corresponding to the mode, or
     * {@code null} if the mode is not calendar-schedule-based.
     */
    @Nullable
    public static ZenModeConfig.EventInfo getCalendarSchedule(@NonNull ZenMode mode) {
        return ZenModeConfig.tryParseEventConditionId(mode.getRule().getConditionId());
    }

    private ZenModeSchedules() { }
}
