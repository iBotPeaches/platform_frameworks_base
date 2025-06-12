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

package com.android.systemui.statusbar.chips

import com.android.systemui.Flags
import com.android.systemui.statusbar.phone.ongoingcall.StatusBarChipsModernization

/** Helper for reading or using the status_bar_chips_return_animations flag state. */
object StatusBarChipsReturnAnimations {
    /** The aconfig flag name */
    const val FLAG_NAME = Flags.FLAG_STATUS_BAR_CHIPS_RETURN_ANIMATIONS

    /** Is the feature enabled. */
    @JvmStatic
    inline val isEnabled
        get() = StatusBarChipsModernization.isEnabled && Flags.statusBarChipsReturnAnimations()
}
