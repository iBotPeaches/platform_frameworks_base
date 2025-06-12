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

package com.android.compose.theme.typography

import androidx.compose.ui.text.TextStyle

internal class TypographyTokens(
    typeScaleTokens: TypeScaleTokens,
    variableTypeScaleTokens: VariableFontTypeScaleEmphasizedTokens,
) {
    val bodyLarge =
        TextStyle(
            fontFamily = typeScaleTokens.bodyLargeFont,
            fontWeight = typeScaleTokens.bodyLargeWeight,
            fontSize = typeScaleTokens.bodyLargeSize,
            lineHeight = typeScaleTokens.bodyLargeLineHeight,
            letterSpacing = typeScaleTokens.bodyLargeTracking,
        )
    val bodyMedium =
        TextStyle(
            fontFamily = typeScaleTokens.bodyMediumFont,
            fontWeight = typeScaleTokens.bodyMediumWeight,
            fontSize = typeScaleTokens.bodyMediumSize,
            lineHeight = typeScaleTokens.bodyMediumLineHeight,
            letterSpacing = typeScaleTokens.bodyMediumTracking,
        )
    val bodySmall =
        TextStyle(
            fontFamily = typeScaleTokens.bodySmallFont,
            fontWeight = typeScaleTokens.bodySmallWeight,
            fontSize = typeScaleTokens.bodySmallSize,
            lineHeight = typeScaleTokens.bodySmallLineHeight,
            letterSpacing = typeScaleTokens.bodySmallTracking,
        )
    val displayLarge =
        TextStyle(
            fontFamily = typeScaleTokens.displayLargeFont,
            fontWeight = typeScaleTokens.displayLargeWeight,
            fontSize = typeScaleTokens.displayLargeSize,
            lineHeight = typeScaleTokens.displayLargeLineHeight,
            letterSpacing = typeScaleTokens.displayLargeTracking,
        )
    val displayMedium =
        TextStyle(
            fontFamily = typeScaleTokens.displayMediumFont,
            fontWeight = typeScaleTokens.displayMediumWeight,
            fontSize = typeScaleTokens.displayMediumSize,
            lineHeight = typeScaleTokens.displayMediumLineHeight,
            letterSpacing = typeScaleTokens.displayMediumTracking,
        )
    val displaySmall =
        TextStyle(
            fontFamily = typeScaleTokens.displaySmallFont,
            fontWeight = typeScaleTokens.displaySmallWeight,
            fontSize = typeScaleTokens.displaySmallSize,
            lineHeight = typeScaleTokens.displaySmallLineHeight,
            letterSpacing = typeScaleTokens.displaySmallTracking,
        )
    val headlineLarge =
        TextStyle(
            fontFamily = typeScaleTokens.headlineLargeFont,
            fontWeight = typeScaleTokens.headlineLargeWeight,
            fontSize = typeScaleTokens.headlineLargeSize,
            lineHeight = typeScaleTokens.headlineLargeLineHeight,
            letterSpacing = typeScaleTokens.headlineLargeTracking,
        )
    val headlineMedium =
        TextStyle(
            fontFamily = typeScaleTokens.headlineMediumFont,
            fontWeight = typeScaleTokens.headlineMediumWeight,
            fontSize = typeScaleTokens.headlineMediumSize,
            lineHeight = typeScaleTokens.headlineMediumLineHeight,
            letterSpacing = typeScaleTokens.headlineMediumTracking,
        )
    val headlineSmall =
        TextStyle(
            fontFamily = typeScaleTokens.headlineSmallFont,
            fontWeight = typeScaleTokens.headlineSmallWeight,
            fontSize = typeScaleTokens.headlineSmallSize,
            lineHeight = typeScaleTokens.headlineSmallLineHeight,
            letterSpacing = typeScaleTokens.headlineSmallTracking,
        )
    val labelLarge =
        TextStyle(
            fontFamily = typeScaleTokens.labelLargeFont,
            fontWeight = typeScaleTokens.labelLargeWeight,
            fontSize = typeScaleTokens.labelLargeSize,
            lineHeight = typeScaleTokens.labelLargeLineHeight,
            letterSpacing = typeScaleTokens.labelLargeTracking,
        )
    val labelMedium =
        TextStyle(
            fontFamily = typeScaleTokens.labelMediumFont,
            fontWeight = typeScaleTokens.labelMediumWeight,
            fontSize = typeScaleTokens.labelMediumSize,
            lineHeight = typeScaleTokens.labelMediumLineHeight,
            letterSpacing = typeScaleTokens.labelMediumTracking,
        )
    val labelSmall =
        TextStyle(
            fontFamily = typeScaleTokens.labelSmallFont,
            fontWeight = typeScaleTokens.labelSmallWeight,
            fontSize = typeScaleTokens.labelSmallSize,
            lineHeight = typeScaleTokens.labelSmallLineHeight,
            letterSpacing = typeScaleTokens.labelSmallTracking,
        )
    val titleLarge =
        TextStyle(
            fontFamily = typeScaleTokens.titleLargeFont,
            fontWeight = typeScaleTokens.titleLargeWeight,
            fontSize = typeScaleTokens.titleLargeSize,
            lineHeight = typeScaleTokens.titleLargeLineHeight,
            letterSpacing = typeScaleTokens.titleLargeTracking,
        )
    val titleMedium =
        TextStyle(
            fontFamily = typeScaleTokens.titleMediumFont,
            fontWeight = typeScaleTokens.titleMediumWeight,
            fontSize = typeScaleTokens.titleMediumSize,
            lineHeight = typeScaleTokens.titleMediumLineHeight,
            letterSpacing = typeScaleTokens.titleMediumTracking,
        )
    val titleSmall =
        TextStyle(
            fontFamily = typeScaleTokens.titleSmallFont,
            fontWeight = typeScaleTokens.titleSmallWeight,
            fontSize = typeScaleTokens.titleSmallSize,
            lineHeight = typeScaleTokens.titleSmallLineHeight,
            letterSpacing = typeScaleTokens.titleSmallTracking,
        )
    // GSF emphasized styles
    // note: we don't need to define fontWeight or axes values because they are pre-defined
    // as part of the font family in fonts_customization.xml (for performance optimization)
    val displayLargeEmphasized =
        TextStyle(
            fontFamily = variableTypeScaleTokens.displayLargeFont,
            fontSize = variableTypeScaleTokens.displayLargeSize,
            lineHeight = variableTypeScaleTokens.displayLargeLineHeight,
            letterSpacing = variableTypeScaleTokens.displayLargeTracking,
        )
    val displayMediumEmphasized =
        TextStyle(
            fontFamily = variableTypeScaleTokens.displayMediumFont,
            fontSize = variableTypeScaleTokens.displayMediumSize,
            lineHeight = variableTypeScaleTokens.displayMediumLineHeight,
            letterSpacing = variableTypeScaleTokens.displayMediumTracking,
        )
    val displaySmallEmphasized =
        TextStyle(
            fontFamily = variableTypeScaleTokens.displaySmallFont,
            fontSize = variableTypeScaleTokens.displaySmallSize,
            lineHeight = variableTypeScaleTokens.displaySmallLineHeight,
            letterSpacing = variableTypeScaleTokens.displaySmallTracking,
        )
    val headlineLargeEmphasized =
        TextStyle(
            fontFamily = variableTypeScaleTokens.headlineLargeFont,
            fontSize = variableTypeScaleTokens.headlineLargeSize,
            lineHeight = variableTypeScaleTokens.headlineLargeLineHeight,
            letterSpacing = variableTypeScaleTokens.headlineLargeTracking,
        )
    val headlineMediumEmphasized =
        TextStyle(
            fontFamily = variableTypeScaleTokens.headlineMediumFont,
            fontSize = variableTypeScaleTokens.headlineMediumSize,
            lineHeight = variableTypeScaleTokens.headlineMediumLineHeight,
            letterSpacing = variableTypeScaleTokens.headlineMediumTracking,
        )
    val headlineSmallEmphasized =
        TextStyle(
            fontFamily = variableTypeScaleTokens.headlineSmallFont,
            fontSize = variableTypeScaleTokens.headlineSmallSize,
            lineHeight = variableTypeScaleTokens.headlineSmallLineHeight,
            letterSpacing = variableTypeScaleTokens.headlineSmallTracking,
        )
    val titleLargeEmphasized =
        TextStyle(
            fontFamily = variableTypeScaleTokens.titleLargeFont,
            fontSize = variableTypeScaleTokens.titleLargeSize,
            lineHeight = variableTypeScaleTokens.titleLargeLineHeight,
            letterSpacing = variableTypeScaleTokens.titleLargeTracking,
        )
    val titleMediumEmphasized =
        TextStyle(
            fontFamily = variableTypeScaleTokens.titleMediumFont,
            fontSize = variableTypeScaleTokens.titleMediumSize,
            lineHeight = variableTypeScaleTokens.titleMediumLineHeight,
            letterSpacing = variableTypeScaleTokens.titleMediumTracking,
        )
    val titleSmallEmphasized =
        TextStyle(
            fontFamily = variableTypeScaleTokens.titleSmallFont,
            fontSize = variableTypeScaleTokens.titleSmallSize,
            lineHeight = variableTypeScaleTokens.titleSmallLineHeight,
            letterSpacing = variableTypeScaleTokens.titleSmallTracking,
        )
    val bodyLargeEmphasized =
        TextStyle(
            fontFamily = variableTypeScaleTokens.bodyLargeFont,
            fontSize = variableTypeScaleTokens.bodyLargeSize,
            lineHeight = variableTypeScaleTokens.bodyLargeLineHeight,
            letterSpacing = variableTypeScaleTokens.bodyLargeTracking,
        )
    val bodyMediumEmphasized =
        TextStyle(
            fontFamily = variableTypeScaleTokens.bodyMediumFont,
            fontSize = variableTypeScaleTokens.bodyMediumSize,
            lineHeight = variableTypeScaleTokens.bodyMediumLineHeight,
            letterSpacing = variableTypeScaleTokens.bodyMediumTracking,
        )
    val bodySmallEmphasized =
        TextStyle(
            fontFamily = variableTypeScaleTokens.bodySmallFont,
            fontSize = variableTypeScaleTokens.bodySmallSize,
            lineHeight = variableTypeScaleTokens.bodySmallLineHeight,
            letterSpacing = variableTypeScaleTokens.bodySmallTracking,
        )
    val labelLargeEmphasized =
        TextStyle(
            fontFamily = variableTypeScaleTokens.labelLargeFont,
            fontSize = variableTypeScaleTokens.labelLargeSize,
            lineHeight = variableTypeScaleTokens.labelLargeLineHeight,
            letterSpacing = variableTypeScaleTokens.labelLargeTracking,
        )
    val labelMediumEmphasized =
        TextStyle(
            fontFamily = variableTypeScaleTokens.labelMediumFont,
            fontSize = variableTypeScaleTokens.labelMediumSize,
            lineHeight = variableTypeScaleTokens.labelMediumLineHeight,
            letterSpacing = variableTypeScaleTokens.labelMediumTracking,
        )
    val labelSmallEmphasized =
        TextStyle(
            fontFamily = variableTypeScaleTokens.labelSmallFont,
            fontSize = variableTypeScaleTokens.labelSmallSize,
            lineHeight = variableTypeScaleTokens.labelSmallLineHeight,
            letterSpacing = variableTypeScaleTokens.labelSmallTracking,
        )
}
