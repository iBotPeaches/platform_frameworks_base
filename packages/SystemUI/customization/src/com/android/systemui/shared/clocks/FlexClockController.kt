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

package com.android.systemui.shared.clocks

import android.content.res.Resources
import com.android.systemui.animation.GSFAxes
import com.android.systemui.customization.R
import com.android.systemui.plugins.clocks.AlarmData
import com.android.systemui.plugins.clocks.AxisPresetConfig
import com.android.systemui.plugins.clocks.AxisType
import com.android.systemui.plugins.clocks.ClockAxisStyle
import com.android.systemui.plugins.clocks.ClockConfig
import com.android.systemui.plugins.clocks.ClockController
import com.android.systemui.plugins.clocks.ClockEventListener
import com.android.systemui.plugins.clocks.ClockEvents
import com.android.systemui.plugins.clocks.ClockFontAxis
import com.android.systemui.plugins.clocks.ClockFontAxis.Companion.merge
import com.android.systemui.plugins.clocks.ClockSettings
import com.android.systemui.plugins.clocks.WeatherData
import com.android.systemui.plugins.clocks.ZenData
import com.android.systemui.shared.clocks.FontUtils.put
import com.android.systemui.shared.clocks.FontUtils.toClockAxis
import com.android.systemui.shared.clocks.view.FlexClockView
import java.io.PrintWriter
import java.util.Locale
import java.util.TimeZone

/** Controller for the default flex clock */
class FlexClockController(private val clockCtx: ClockContext) : ClockController {
    override val smallClock =
        FlexClockFaceController(
            clockCtx.copy(messageBuffer = clockCtx.messageBuffers.smallClockMessageBuffer),
            isLargeClock = false,
        )

    override val largeClock =
        FlexClockFaceController(
            clockCtx.copy(messageBuffer = clockCtx.messageBuffers.largeClockMessageBuffer),
            isLargeClock = true,
        )

    override val config: ClockConfig by lazy {
        ClockConfig(
            DEFAULT_CLOCK_ID,
            clockCtx.resources.getString(R.string.clock_default_name),
            clockCtx.resources.getString(R.string.clock_default_description),
        )
    }

    override val events =
        object : ClockEvents {
            override var isReactiveTouchInteractionEnabled = false
                set(value) {
                    field = value
                    val view = largeClock.view as FlexClockView
                    view.isReactiveTouchInteractionEnabled = value
                }

            override fun onTimeZoneChanged(timeZone: TimeZone) {
                smallClock.events.onTimeZoneChanged(timeZone)
                largeClock.events.onTimeZoneChanged(timeZone)
            }

            override fun onTimeFormatChanged(is24Hr: Boolean) {
                smallClock.events.onTimeFormatChanged(is24Hr)
                largeClock.events.onTimeFormatChanged(is24Hr)
            }

            override fun onLocaleChanged(locale: Locale) {
                smallClock.events.onLocaleChanged(locale)
                largeClock.events.onLocaleChanged(locale)
            }

            override fun onWeatherDataChanged(data: WeatherData) {
                smallClock.events.onWeatherDataChanged(data)
                largeClock.events.onWeatherDataChanged(data)
            }

            override fun onAlarmDataChanged(data: AlarmData) {
                smallClock.events.onAlarmDataChanged(data)
                largeClock.events.onAlarmDataChanged(data)
            }

            override fun onZenDataChanged(data: ZenData) {
                smallClock.events.onZenDataChanged(data)
                largeClock.events.onZenDataChanged(data)
            }
        }

    override fun initialize(
        isDarkTheme: Boolean,
        dozeFraction: Float,
        foldFraction: Float,
        clockListener: ClockEventListener?,
    ) {
        smallClock.run {
            layerController.onViewBoundsChanged = { clockListener?.onBoundsChanged(it) }
            events.onThemeChanged(theme.copy(isDarkTheme = isDarkTheme))
            animations.onFontAxesChanged(clockCtx.settings.axes)
            animations.doze(dozeFraction)
            animations.fold(foldFraction)
            events.onTimeTick()
        }

        largeClock.run {
            layerController.onViewBoundsChanged = { clockListener?.onBoundsChanged(it) }
            events.onThemeChanged(theme.copy(isDarkTheme = isDarkTheme))
            animations.onFontAxesChanged(clockCtx.settings.axes)
            animations.doze(dozeFraction)
            animations.fold(foldFraction)
            events.onTimeTick()
        }
    }

    override fun dump(pw: PrintWriter) {}

    companion object {
        fun getDefaultAxes(settings: ClockSettings): List<ClockFontAxis> {
            return if (settings.clockId == FLEX_CLOCK_ID) {
                FONT_AXES.merge(LEGACY_FLEX_SETTINGS)
            } else FONT_AXES
        }

        private val FONT_AXES =
            listOf(
                GSFAxes.WEIGHT.toClockAxis(
                    type = AxisType.Float,
                    currentValue = 400f,
                    name = "Weight",
                    description = "Glyph Weight",
                ),
                GSFAxes.WIDTH.toClockAxis(
                    type = AxisType.Float,
                    currentValue = 80f,
                    name = "Width",
                    description = "Glyph Width",
                ),
                GSFAxes.ROUND.toClockAxis(
                    type = AxisType.Boolean,
                    currentValue = 100f,
                    name = "Round",
                    description = "Glyph Roundness",
                ),
                GSFAxes.SLANT.toClockAxis(
                    type = AxisType.Boolean,
                    currentValue = 0f,
                    name = "Slant",
                    description = "Glyph Slant",
                ),
            )

        private val LEGACY_FLEX_SETTINGS = ClockAxisStyle {
            put(GSFAxes.WEIGHT, 600f)
            put(GSFAxes.WIDTH, 100f)
            put(GSFAxes.ROUND, 100f)
            put(GSFAxes.SLANT, 0f)
        }

        private val PRESET_COUNT = 8
        private val PRESET_WIDTH_INIT = 30f
        private val PRESET_WIDTH_STEP = 12.5f
        private val PRESET_WEIGHT_INIT = 800f
        private val PRESET_WEIGHT_STEP = -100f
        private val BASE_PRESETS: List<ClockAxisStyle> = run {
            val presets = mutableListOf<ClockAxisStyle>()
            var weight = PRESET_WEIGHT_INIT
            var width = PRESET_WIDTH_INIT
            for (i in 1..PRESET_COUNT) {
                presets.add(
                    ClockAxisStyle {
                        put(GSFAxes.WEIGHT, weight)
                        put(GSFAxes.WIDTH, width)
                        put(GSFAxes.ROUND, 0f)
                        put(GSFAxes.SLANT, 0f)
                    }
                )

                weight += PRESET_WEIGHT_STEP
                width += PRESET_WIDTH_STEP
            }

            return@run presets
        }

        fun buildPresetGroup(resources: Resources, isRound: Boolean): AxisPresetConfig.Group {
            val round = if (isRound) GSFAxes.ROUND.maxValue else GSFAxes.ROUND.minValue
            return AxisPresetConfig.Group(
                presets = BASE_PRESETS.map { it.copy { put(GSFAxes.ROUND, round) } },
                // TODO(b/395647577): Placeholder Icon; Replace or remove
                icon = resources.getDrawable(R.drawable.clock_default_thumbnail, null),
            )
        }
    }
}
