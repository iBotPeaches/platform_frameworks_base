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

package com.android.systemui.shared.clocks

import com.android.systemui.animation.AxisDefinition
import com.android.systemui.plugins.clocks.AxisType
import com.android.systemui.plugins.clocks.ClockAxisStyle
import com.android.systemui.plugins.clocks.ClockFontAxis

object FontUtils {
    fun AxisDefinition.toClockAxis(
        type: AxisType,
        currentValue: Float? = null,
        name: String,
        description: String,
    ): ClockFontAxis {
        return ClockFontAxis(
            key = this.tag,
            type = type,
            maxValue = this.maxValue,
            minValue = this.minValue,
            currentValue = currentValue ?: this.defaultValue,
            name = name,
            description = description,
        )
    }

    fun ClockAxisStyle.put(def: AxisDefinition, value: Float? = null) {
        this.put(def.tag, value ?: def.defaultValue)
    }

    operator fun ClockAxisStyle.set(def: AxisDefinition, value: Float) {
        this[def.tag] = value
    }

    operator fun ClockAxisStyle.get(def: AxisDefinition): Float {
        return this[def.tag] ?: def.defaultValue
    }
}
