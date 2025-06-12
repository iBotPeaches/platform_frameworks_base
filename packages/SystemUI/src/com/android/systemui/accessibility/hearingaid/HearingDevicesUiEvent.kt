/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.accessibility.hearingaid

import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger

enum class HearingDevicesUiEvent(private val id: Int) : UiEventLogger.UiEventEnum {

    @UiEvent(doc = "Hearing devices dialog is shown") HEARING_DEVICES_DIALOG_SHOW(1848),
    @UiEvent(doc = "Pair new device") HEARING_DEVICES_PAIR(1849),
    @UiEvent(doc = "Connect to the device") HEARING_DEVICES_CONNECT(1850),
    @UiEvent(doc = "Disconnect from the device") HEARING_DEVICES_DISCONNECT(1851),
    @UiEvent(doc = "Set the device as active device") HEARING_DEVICES_SET_ACTIVE(1852),
    @UiEvent(doc = "Click on the device gear to enter device detail page")
    HEARING_DEVICES_GEAR_CLICK(1853),
    @UiEvent(doc = "Select a preset from preset spinner") HEARING_DEVICES_PRESET_SELECT(1854),
    @UiEvent(doc = "Click on related tool") HEARING_DEVICES_RELATED_TOOL_CLICK(1856),
    @UiEvent(doc = "Change the ambient volume with unified control")
    HEARING_DEVICES_AMBIENT_CHANGE_UNIFIED(2149),
    @UiEvent(doc = "Change the ambient volume with separated control")
    HEARING_DEVICES_AMBIENT_CHANGE_SEPARATED(2150),
    @UiEvent(doc = "Mute the ambient volume") HEARING_DEVICES_AMBIENT_MUTE(2151),
    @UiEvent(doc = "Unmute the ambient volume") HEARING_DEVICES_AMBIENT_UNMUTE(2152),
    @UiEvent(doc = "Expand the ambient volume controls")
    HEARING_DEVICES_AMBIENT_EXPAND_CONTROLS(2153),
    @UiEvent(doc = "Collapse the ambient volume controls")
    HEARING_DEVICES_AMBIENT_COLLAPSE_CONTROLS(2154),
    @UiEvent(doc = "Select a input routing option from input routing spinner")
    HEARING_DEVICES_INPUT_ROUTING_SELECT(2155),
    @UiEvent(doc = "Click on the device settings to enter hearing devices page")
    HEARING_DEVICES_SETTINGS_CLICK(2172);

    override fun getId(): Int = this.id
}
