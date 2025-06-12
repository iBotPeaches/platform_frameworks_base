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

@file:OptIn(ExperimentalTextApi::class)

package com.android.compose.theme.typography

import android.content.Context
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

internal class TypefaceTokens(typefaceNames: TypefaceNames) {
    companion object {
        val WeightMedium = FontWeight.Medium
        val WeightRegular = FontWeight.Normal
    }

    private val brandFont = DeviceFontFamilyName(typefaceNames.brand)
    private val plainFont = DeviceFontFamilyName(typefaceNames.plain)

    // Google Sans Flex emphasized styles
    private val displayLargeEmphasizedFont =
        DeviceFontFamilyName("variable-display-large-emphasized")
    private val displayMediumEmphasizedFont =
        DeviceFontFamilyName("variable-display-medium-emphasized")
    private val displaySmallEmphasizedFont =
        DeviceFontFamilyName("variable-display-small-emphasized")
    private val headlineLargeEmphasizedFont =
        DeviceFontFamilyName("variable-headline-large-emphasized")
    private val headlineMediumEmphasizedFont =
        DeviceFontFamilyName("variable-headline-medium-emphasized")
    private val headlineSmallEmphasizedFont =
        DeviceFontFamilyName("variable-headline-small-emphasized")
    private val titleLargeEmphasizedFont = DeviceFontFamilyName("variable-title-large-emphasized")
    private val titleMediumEmphasizedFont = DeviceFontFamilyName("variable-title-medium-emphasized")
    private val titleSmallEmphasizedFont = DeviceFontFamilyName("variable-title-small-emphasized")
    private val bodyLargeEmphasizedFont = DeviceFontFamilyName("variable-body-large-emphasized")
    private val bodyMediumEmphasizedFont = DeviceFontFamilyName("variable-body-medium-emphasized")
    private val bodySmallEmphasizedFont = DeviceFontFamilyName("variable-body-small-emphasized")
    private val labelLargeEmphasizedFont = DeviceFontFamilyName("variable-label-large-emphasized")
    private val labelMediumEmphasizedFont = DeviceFontFamilyName("variable-label-medium-emphasized")
    private val labelSmallEmphasizedFont = DeviceFontFamilyName("variable-label-small-emphasized")

    val brand =
        FontFamily(
            Font(brandFont, weight = WeightMedium),
            Font(brandFont, weight = WeightRegular),
        )
    val plain =
        FontFamily(
            Font(plainFont, weight = WeightMedium),
            Font(plainFont, weight = WeightRegular),
        )

    val displayLargeEmphasized = FontFamily(Font(displayLargeEmphasizedFont))
    val displayMediumEmphasized = FontFamily(Font(displayMediumEmphasizedFont))
    val displaySmallEmphasized = FontFamily(Font(displaySmallEmphasizedFont))
    val headlineLargeEmphasized = FontFamily(Font(headlineLargeEmphasizedFont))
    val headlineMediumEmphasized = FontFamily(Font(headlineMediumEmphasizedFont))
    val headlineSmallEmphasized = FontFamily(Font(headlineSmallEmphasizedFont))
    val titleLargeEmphasized = FontFamily(Font(titleLargeEmphasizedFont))
    val titleMediumEmphasized = FontFamily(Font(titleMediumEmphasizedFont))
    val titleSmallEmphasized = FontFamily(Font(titleSmallEmphasizedFont))
    val bodyLargeEmphasized = FontFamily(Font(bodyLargeEmphasizedFont))
    val bodyMediumEmphasized = FontFamily(Font(bodyMediumEmphasizedFont))
    val bodySmallEmphasized = FontFamily(Font(bodySmallEmphasizedFont))
    val labelLargeEmphasized = FontFamily(Font(labelLargeEmphasizedFont))
    val labelMediumEmphasized = FontFamily(Font(labelMediumEmphasizedFont))
    val labelSmallEmphasized = FontFamily(Font(labelSmallEmphasizedFont))
}

internal data class TypefaceNames
private constructor(
    val brand: String,
    val plain: String,
) {
    private enum class Config(val configName: String, val default: String) {
        Brand("config_headlineFontFamily", "sans-serif"),
        Plain("config_bodyFontFamily", "sans-serif"),
    }

    companion object {
        fun get(context: Context): TypefaceNames {
            return TypefaceNames(
                brand = getTypefaceName(context, Config.Brand),
                plain = getTypefaceName(context, Config.Plain),
            )
        }

        private fun getTypefaceName(context: Context, config: Config): String {
            return context
                .getString(context.resources.getIdentifier(config.configName, "string", "android"))
                .takeIf { it.isNotEmpty() }
                ?: config.default
        }
    }
}
