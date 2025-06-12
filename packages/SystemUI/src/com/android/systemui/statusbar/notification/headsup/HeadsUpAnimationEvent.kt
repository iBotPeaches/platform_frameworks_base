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

package com.android.systemui.statusbar.notification.headsup

import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow

/** Models the data needed for a heads-up notification animation. */
data class HeadsUpAnimationEvent(
    /** The row corresponding to the heads-up notification. */
    val row: ExpandableNotificationRow,
    /**
     * True if this notification should do a appearance animation, false if this notification should
     * do a disappear animation.
     */
    val isHeadsUpAppearance: Boolean,
    /** True if the status bar is showing a chip corresponding to this notification. */
    val hasStatusBarChip: Boolean,
)
