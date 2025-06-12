/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an AS IS BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.pipeline.battery.shared.ui

import androidx.compose.ui.graphics.Color

sealed interface BatteryColors {
    val glyph: Color
    val fill: Color
    val background: Color

    data object LightThemeDefaultColors : BatteryColors {
        override val glyph = Color.White
        override val fill = Color.Black
        override val background = Color(0xFF8C8C8C)
    }

    data object LightThemeChargingColors : BatteryColors {
        override val glyph = Color(0xFF446600)
        override val fill = Color(0xFFB4FF1E)
        override val background = Color(0xFFD6FF83)
    }

    data object LightThemeErrorColors : BatteryColors {
        override val glyph = Color(0xFF79063A)
        override val fill = Color(0xFFFF0166)
        override val background = Color(0xFFFF8CBA)
    }

    data object LightThemePowerSaveColors : BatteryColors {
        override val glyph = Color(0xFF5A4E00)
        override val fill = Color(0xFFFFDA17)
        override val background = Color(0xFFFFEB7F)
    }

    data object DarkThemeDefaultColors : BatteryColors {
        override val glyph = Color.Black
        override val fill = Color.White
        override val background = Color(0xFFC5C5C5)
    }

    data object DarkThemeChargingColors : BatteryColors {
        override val glyph = Color(0xFF446600)
        override val fill = Color(0xFFB4FF1E)
        override val background = Color(0xFFD6FF83)
    }

    data object DarkThemeErrorColors : BatteryColors {
        override val glyph = Color(0xFF79063A)
        override val fill = Color(0xFFFF0166)
        override val background = Color(0xFFFF8CBA)
    }

    data object DarkThemePowerSaveColors : BatteryColors {
        override val glyph = Color(0xFF5A4E00)
        override val fill = Color(0xFFFFDA17)
        override val background = Color(0xFFFFEB7F)
    }
}
