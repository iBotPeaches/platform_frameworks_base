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

package com.android.systemui.cursorposition.data.model

/**
 * Represents the position of cursor hotspot on the screen. Hotspot is the specific pixel that
 * signifies the location of the pointer's interaction with the user interface. By default, hotspot
 * of a cursor is the tip of arrow.
 *
 * @property x The x-coordinate of the cursor hotspot, relative to the top-left corner of the
 *   screen.
 * @property y The y-coordinate of the cursor hotspot, relative to the top-left corner of the
 *   screen.
 * @property displayId The display on which the cursor is located.
 */
data class CursorPosition(val x: Float, val y: Float, val displayId: Int)
