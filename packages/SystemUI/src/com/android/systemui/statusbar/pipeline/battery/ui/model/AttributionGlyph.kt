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

package com.android.systemui.statusbar.pipeline.battery.ui.model

import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryGlyph

/**
 * Wrapper around potentially 2 glpyhs. This will allow the composable to draw the larger one if it
 * is the only one displayed. For example, if the percentage is not showing and the device is
 * plugged in, then we can show the larger charging bolt.
 */
data class AttributionGlyph(
    /** Can be used when this glyph is alongside any others */
    val inline: BatteryGlyph,
    /** Can be used when this glyph is the only foreground element */
    val standalone: BatteryGlyph,
)
