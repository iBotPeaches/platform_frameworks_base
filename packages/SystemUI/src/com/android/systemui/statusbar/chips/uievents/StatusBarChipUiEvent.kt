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

package com.android.systemui.statusbar.chips.uievents

import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger

/** All UI Events related to the status bar chips. */
enum class StatusBarChipUiEvent(private val _id: Int) : UiEventLogger.UiEventEnum {
    // New chip events, with chip type embedded in the event
    @UiEvent(doc = "New status bar chip: Call") STATUS_BAR_NEW_CHIP_CALL(2211),
    @UiEvent(doc = "New status bar chip: Screen record") STATUS_BAR_NEW_CHIP_SCREEN_RECORD(2212),
    @UiEvent(doc = "New status bar chip: Share screen/audio to another app")
    STATUS_BAR_NEW_CHIP_SHARE_TO_APP(2213),
    @UiEvent(doc = "New status bar chip: Cast screen/audio to different device")
    STATUS_BAR_NEW_CHIP_CAST_TO_OTHER_DEVICE(2214),
    @UiEvent(doc = "New status bar chip: Promoted notification")
    STATUS_BAR_NEW_CHIP_NOTIFICATION(2215),

    // Other chip events, which don't need the chip type embedded in the event because an instanceId
    // should also be provided with the new event and all subsequent events
    @UiEvent(doc = "A status bar chip was removed") STATUS_BAR_CHIP_REMOVED(2216),
    @UiEvent(doc = "A status bar chip was tapped to show more information")
    STATUS_BAR_CHIP_TAP_TO_SHOW(2217),
    @UiEvent(
        doc = "A status bar chip was re-tapped to hide the information that was previously shown"
    )
    STATUS_BAR_CHIP_TAP_TO_HIDE(2218);

    override fun getId() = _id
}
