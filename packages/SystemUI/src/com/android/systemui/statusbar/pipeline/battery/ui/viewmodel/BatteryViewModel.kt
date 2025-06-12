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

package com.android.systemui.statusbar.pipeline.battery.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.statusbar.pipeline.battery.domain.interactor.BatteryAttributionModel.Charging
import com.android.systemui.statusbar.pipeline.battery.domain.interactor.BatteryAttributionModel.Defend
import com.android.systemui.statusbar.pipeline.battery.domain.interactor.BatteryAttributionModel.PowerSave
import com.android.systemui.statusbar.pipeline.battery.domain.interactor.BatteryInteractor
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryColors
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryFrame
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryGlyph
import com.android.systemui.statusbar.pipeline.battery.ui.model.AttributionGlyph
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** View-model for the unified, compose-based battery icon. */
@OptIn(ExperimentalCoroutinesApi::class)
class BatteryViewModel
@AssistedInject
constructor(interactor: BatteryInteractor, @Application context: Context) : ExclusiveActivatable() {
    private val hydrator: Hydrator = Hydrator("BatteryViewModel.hydrator")

    val batteryFrame = BatteryFrame.pathSpec
    val innerWidth = BatteryFrame.innerWidth
    val innerHeight = BatteryFrame.innerHeight
    val aspectRatio = BatteryFrame.innerWidth / BatteryFrame.innerHeight

    val level by
        hydrator.hydratedStateOf(traceName = "level", initialValue = 0, source = interactor.level)

    val isFull by
        hydrator.hydratedStateOf(
            traceName = "isFull",
            initialValue = false,
            source = interactor.isFull,
        )

    /** The current attribution, if any */
    private val attributionGlyph: Flow<AttributionGlyph?> =
        interactor.batteryAttributionType.map {
            when (it) {
                Charging ->
                    AttributionGlyph(
                        inline = BatteryGlyph.Bolt,
                        standalone = BatteryGlyph.BoltLarge,
                    )

                PowerSave ->
                    AttributionGlyph(
                        inline = BatteryGlyph.Plus,
                        standalone = BatteryGlyph.PlusLarge,
                    )

                Defend ->
                    AttributionGlyph(
                        inline = BatteryGlyph.Defend,
                        standalone = BatteryGlyph.DefendLarge,
                    )

                else -> null
            }
        }

    /** A [List<BatteryGlyph>] representation of the current [level] */
    private val levelGlyphs: Flow<List<BatteryGlyph>> =
        interactor.level.map { it.glyphRepresentation() }

    private val _glyphList: Flow<List<BatteryGlyph>> =
        interactor.isBatteryPercentSettingEnabled.flatMapLatest {
            if (it) {
                combine(interactor.isFull, levelGlyphs, attributionGlyph) {
                    isFull,
                    levelGlyphs,
                    attr ->
                    // Don't ever show "100<attr>", since it won't fit. Just show the attr
                    if (isFull && attr != null) {
                        listOf(attr.standalone)
                    } else if (attr != null) {
                        levelGlyphs + attr.inline
                    } else {
                        levelGlyphs
                    }
                }
            } else {
                attributionGlyph.map { attr ->
                    if (attr == null) {
                        emptyList()
                    } else {
                        listOf(attr.standalone)
                    }
                }
            }
        }

    /** For the status bar battery, this is the complete set of glyphs to show */
    val glyphList: List<BatteryGlyph> by
        hydrator.hydratedStateOf(
            traceName = "glyphList",
            initialValue = emptyList(),
            source = _glyphList,
        )

    private val _colorProfile: Flow<ColorProfile> =
        combine(interactor.batteryAttributionType, interactor.isCritical) { attr, isCritical ->
            when (attr) {
                Charging,
                Defend ->
                    ColorProfile(
                        dark = BatteryColors.DarkThemeChargingColors,
                        light = BatteryColors.LightThemeChargingColors,
                    )
                PowerSave ->
                    ColorProfile(
                        dark = BatteryColors.DarkThemePowerSaveColors,
                        light = BatteryColors.LightThemePowerSaveColors,
                    )
                else ->
                    if (isCritical) {
                        ColorProfile(
                            dark = BatteryColors.DarkThemeErrorColors,
                            light = BatteryColors.LightThemeErrorColors,
                        )
                    } else {
                        ColorProfile(
                            dark = BatteryColors.DarkThemeDefaultColors,
                            light = BatteryColors.LightThemeDefaultColors,
                        )
                    }
            }
        }

    /** For the current battery state, what is the relevant color profile to use */
    val colorProfile: ColorProfile by
        hydrator.hydratedStateOf(
            traceName = "colorProfile",
            initialValue =
                ColorProfile(
                    dark = BatteryColors.DarkThemeDefaultColors,
                    light = BatteryColors.LightThemeDefaultColors,
                ),
            source = _colorProfile,
        )

    val contentDescription: ContentDescription by
        hydrator.hydratedStateOf(
            traceName = "contentDescription",
            initialValue = ContentDescription.Loaded(null),
            source =
                combine(
                    interactor.batteryAttributionType,
                    interactor.isStateUnknown,
                    interactor.level,
                ) { attr, isUnknown, level ->
                    when {
                        isUnknown ->
                            ContentDescription.Resource(R.string.accessibility_battery_unknown)
                        attr == Defend -> {
                            val descr =
                                context.getString(
                                    R.string.accessibility_battery_level_charging_paused,
                                    level,
                                )

                            ContentDescription.Loaded(descr)
                        }
                        attr == Charging -> {
                            val descr =
                                context.getString(
                                    R.string.accessibility_battery_level_charging,
                                    level,
                                )
                            ContentDescription.Loaded(descr)
                        }
                        else -> {
                            val descr =
                                context.getString(R.string.accessibility_battery_level, level)
                            ContentDescription.Loaded(descr)
                        }
                    }
                },
        )

    val batteryTimeRemainingEstimate: String? by
        hydrator.hydratedStateOf(
            traceName = "timeRemainingEstimate",
            initialValue = null,
            source = interactor.batteryTimeRemainingEstimate,
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(): BatteryViewModel
    }

    companion object {
        // Status bar battery height, based on a 21x12 base canvas
        val STATUS_BAR_BATTERY_HEIGHT = 13.dp
        val STATUS_BAR_BATTERY_WIDTH = 22.75.dp

        fun Int.glyphRepresentation(): List<BatteryGlyph> = toString().map { it.toGlyph() }

        private fun Char.toGlyph(): BatteryGlyph =
            when (this) {
                '0' -> BatteryGlyph.Zero
                '1' -> BatteryGlyph.One
                '2' -> BatteryGlyph.Two
                '3' -> BatteryGlyph.Three
                '4' -> BatteryGlyph.Four
                '5' -> BatteryGlyph.Five
                '6' -> BatteryGlyph.Six
                '7' -> BatteryGlyph.Seven
                '8' -> BatteryGlyph.Eight
                '9' -> BatteryGlyph.Nine
                else -> throw IllegalArgumentException("cannot make glyph from char ($this)")
            }
    }
}

/** Wrap the light and dark color into a single object so the view can decide which one it needs */
data class ColorProfile(val dark: BatteryColors, val light: BatteryColors)
