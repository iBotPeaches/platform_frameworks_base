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

package com.android.settingslib.bluetooth

/**
 * BatteryLevelsInfo contains the battery levels of different components of a bluetooth device.
 * The range of a valid battery level is [0-100], and -1 if the battery level is not applicable.
 */
data class BatteryLevelsInfo(
    val leftBatteryLevel: Int,
    val rightBatteryLevel: Int,
    val caseBatteryLevel: Int,
    val overallBatteryLevel: Int,
)