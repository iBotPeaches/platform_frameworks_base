/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.compose.theme

import android.content.Context
import androidx.annotation.ColorRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.android.internal.R

/** CompositionLocal used to pass [AndroidColorScheme] down the tree. */
val LocalAndroidColorScheme =
    staticCompositionLocalOf<AndroidColorScheme> {
        throw IllegalStateException(
            "No AndroidColorScheme configured. Make sure to use LocalAndroidColorScheme in a " +
                "Composable surrounded by a PlatformTheme {}."
        )
    }

/**
 * The Android color scheme.
 *
 * This scheme contains the Material3 colors that are not available on
 * [androidx.compose.material3.MaterialTheme]. For other colors (e.g. primary), use
 * `MaterialTheme.colorScheme` instead.
 */
@Immutable
class AndroidColorScheme(
    // fixed tokens
    val primaryFixed: Color,
    val primaryFixedDim: Color,
    val onPrimaryFixed: Color,
    val onPrimaryFixedVariant: Color,
    val secondaryFixed: Color,
    val secondaryFixedDim: Color,
    val onSecondaryFixed: Color,
    val onSecondaryFixedVariant: Color,
    val tertiaryFixed: Color,
    val tertiaryFixedDim: Color,
    val onTertiaryFixed: Color,
    val onTertiaryFixedVariant: Color,

    // custom tokens
    val brandA: Color,
    val brandB: Color,
    val brandC: Color,
    val brandD: Color,
    val clockHour: Color,
    val clockMinute: Color,
    val clockSecond: Color,
    val onShadeActive: Color,
    val onShadeActiveVariant: Color,
    val onShadeInactive: Color,
    val onShadeInactiveVariant: Color,
    val onThemeApp: Color,
    val overviewBackground: Color,
    val shadeActive: Color,
    val shadeDisabled: Color,
    val shadeInactive: Color,
    val themeApp: Color,
    val themeAppRing: Color,
    val themeNotif: Color,
    val underSurface: Color,
    val weatherTemp: Color,
    val widgetBackground: Color,
    val surfaceEffect0: Color,
    val surfaceEffect1: Color,
    val surfaceEffect2: Color,
    val surfaceEffect3: Color,
) {
    companion object {
        internal fun color(context: Context, @ColorRes id: Int): Color {
            return Color(context.resources.getColor(id, context.theme))
        }

        operator fun invoke(context: Context): AndroidColorScheme {
            return AndroidColorScheme(
                // Fixed tokens.
                primaryFixed = color(context, R.color.system_primary_fixed),
                primaryFixedDim = color(context, R.color.system_primary_fixed_dim),
                onPrimaryFixed = color(context, R.color.system_on_primary_fixed),
                onPrimaryFixedVariant = color(context, R.color.system_on_primary_fixed_variant),
                secondaryFixed = color(context, R.color.system_secondary_fixed),
                secondaryFixedDim = color(context, R.color.system_secondary_fixed_dim),
                onSecondaryFixed = color(context, R.color.system_on_secondary_fixed),
                onSecondaryFixedVariant = color(context, R.color.system_on_secondary_fixed_variant),
                tertiaryFixed = color(context, R.color.system_tertiary_fixed),
                tertiaryFixedDim = color(context, R.color.system_tertiary_fixed_dim),
                onTertiaryFixed = color(context, R.color.system_on_tertiary_fixed),
                onTertiaryFixedVariant = color(context, R.color.system_on_tertiary_fixed_variant),

                // Custom tokens.
                brandA = color(context, R.color.customColorBrandA),
                brandB = color(context, R.color.customColorBrandB),
                brandC = color(context, R.color.customColorBrandC),
                brandD = color(context, R.color.customColorBrandD),
                clockHour = color(context, R.color.customColorClockHour),
                clockMinute = color(context, R.color.customColorClockMinute),
                clockSecond = color(context, R.color.customColorClockSecond),
                onShadeActive = color(context, R.color.customColorOnShadeActive),
                onShadeActiveVariant = color(context, R.color.customColorOnShadeActiveVariant),
                onShadeInactive = color(context, R.color.customColorOnShadeInactive),
                onShadeInactiveVariant = color(context, R.color.customColorOnShadeInactiveVariant),
                onThemeApp = color(context, R.color.customColorOnThemeApp),
                overviewBackground = color(context, R.color.customColorOverviewBackground),
                shadeActive = color(context, R.color.customColorShadeActive),
                shadeDisabled = color(context, R.color.customColorShadeDisabled),
                shadeInactive = color(context, R.color.customColorShadeInactive),
                themeApp = color(context, R.color.customColorThemeApp),
                themeAppRing = color(context, R.color.customColorThemeAppRing),
                themeNotif = color(context, R.color.customColorThemeNotif),
                underSurface = color(context, R.color.customColorUnderSurface),
                weatherTemp = color(context, R.color.customColorWeatherTemp),
                widgetBackground = color(context, R.color.customColorWidgetBackground),
                surfaceEffect0 = color(context, R.color.surface_effect_0),
                surfaceEffect1 = color(context, R.color.surface_effect_1),
                surfaceEffect2 = color(context, R.color.surface_effect_2),
                surfaceEffect3 = color(context, R.color.surface_effect_3),
            )
        }
    }
}
