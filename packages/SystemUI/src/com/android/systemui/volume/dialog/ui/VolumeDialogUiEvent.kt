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

package com.android.systemui.volume.dialog.ui

import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger

/** UI events for Volume Dialog. */
enum class VolumeDialogUiEvent(val metricId: Int) : UiEventLogger.UiEventEnum {
    @UiEvent(doc = "The ringer mode was toggled to silent") RINGER_MODE_SILENT(154),
    @UiEvent(doc = "The ringer mode was toggled to vibrate") RINGER_MODE_VIBRATE(155),
    @UiEvent(doc = "The ringer mode was toggled to normal") RINGER_MODE_NORMAL(334),
    @UiEvent(doc = "The volume dialog settings icon was clicked") VOLUME_DIALOG_SETTINGS_CLICK(143),
    @UiEvent(doc = "The volume dialog was shown because the volume changed")
    VOLUME_DIALOG_SHOW_VOLUME_CHANGED(128),
    @UiEvent(doc = "The volume dialog was shown because the volume changed remotely")
    VOLUME_DIALOG_SHOW_REMOTE_VOLUME_CHANGED(129),
    @UiEvent(doc = "The volume dialog was shown because the usb high temperature alarm changed")
    VOLUME_DIALOG_SHOW_USB_TEMP_ALARM_CHANGED(130),
    @UiEvent(doc = "The volume dialog was dismissed because of a touch outside the dialog")
    VOLUME_DIALOG_DISMISS_TOUCH_OUTSIDE(134),
    @UiEvent(
        doc =
            "The system asked the volume dialog to close, e.g. for a navigation bar " +
                "touch, or ActivityManager ACTION_CLOSE_SYSTEM_DIALOGS broadcast."
    )
    VOLUME_DIALOG_DISMISS_SYSTEM(135),
    @UiEvent(doc = "The volume dialog was dismissed because it timed out")
    VOLUME_DIALOG_DISMISS_TIMEOUT(136),
    @UiEvent(doc = "The volume dialog was dismissed because the screen turned off")
    VOLUME_DIALOG_DISMISS_SCREEN_OFF(137),
    @UiEvent(doc = "The volume dialog was dismissed because the settings icon was clicked")
    VOLUME_DIALOG_DISMISS_SETTINGS(138),
    @UiEvent(doc = "The volume dialog was dismissed because the stream no longer exists")
    VOLUME_DIALOG_DISMISS_STREAM_GONE(140),
    @UiEvent(
        doc = "The volume dialog was dismissed because the usb high temperature alarm " + "changed"
    )
    VOLUME_DIALOG_DISMISS_USB_TEMP_ALARM_CHANGED(142),
    @UiEvent(doc = "The right-most slider started tracking touch")
    VOLUME_DIALOG_SLIDER_STARTED_TRACKING_TOUCH(1620),
    @UiEvent(doc = "The right-most slider stopped tracking touch")
    VOLUME_DIALOG_SLIDER_STOPPED_TRACKING_TOUCH(1621);

    override fun getId() = metricId
}
