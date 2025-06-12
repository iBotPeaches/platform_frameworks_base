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

package com.android.systemui.statusbar.pipeline.battery.ui.composable

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onLayoutRectChanged
import com.android.systemui.common.ui.compose.load
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.statusbar.phone.domain.interactor.IsAreaDark
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryColors
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryFrame
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryGlyph
import com.android.systemui.statusbar.pipeline.battery.shared.ui.PathSpec
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.BatteryViewModel
import kotlin.math.ceil

/**
 * Draws a battery directly on to a [Canvas]. The canvas is scaled to fill its container, and the
 * resulting battery is scaled using a FIT_CENTER type scaling that preserves the aspect ratio.
 */
@Composable
fun BatteryCanvas(
    path: PathSpec,
    innerWidth: Float,
    innerHeight: Float,
    glyphs: List<BatteryGlyph>,
    level: Int,
    isFull: Boolean,
    colorsProvider: () -> BatteryColors,
    modifier: Modifier = Modifier,
    contentDescription: String = "",
) {

    val totalWidth by
        remember(glyphs) {
            mutableFloatStateOf(
                if (glyphs.isEmpty()) {
                    0f
                } else {
                    // Pads in between each glyph, skipping the first
                    glyphs.drop(1).fold(glyphs.first().width) { acc: Float, next: BatteryGlyph ->
                        acc + INTER_GLYPH_PADDING_PX + next.width
                    }
                }
            )
        }

    Canvas(modifier = modifier.fillMaxSize(), contentDescription = contentDescription) {
        val scale = path.scaleTo(size.width, size.height)
        val colors = colorsProvider()

        scale(scale, pivot = Offset.Zero) {
            if (isFull) {
                // Saves a layer since we don't need background here
                drawPath(path = path.path, color = colors.fill)
            } else {
                // First draw the body
                drawPath(path.path, colors.background)
                // Then draw the body, clipped to the fill level
                clipRect(0f, 0f, innerWidth, innerHeight) {
                    drawRoundRect(
                        color = colors.fill,
                        topLeft = Offset.Zero,
                        size = Size(width = level.scaledLevel(), height = innerHeight),
                        cornerRadius = CornerRadius(2f),
                    )
                }
            }

            // Now draw the glyphs
            var horizontalOffset = (BatteryFrame.innerWidth - totalWidth) / 2
            for (glyph in glyphs) {
                // Move the glyph to the right spot
                val verticalOffset = (BatteryFrame.innerHeight - glyph.height) / 2
                inset(
                    // Never try and inset more than half of the available size - see b/400246091.
                    minOf(horizontalOffset, size.width / 2),
                    minOf(verticalOffset, size.height / 2),
                ) {
                    glyph.draw(this, colors)
                }

                horizontalOffset += glyph.width + INTER_GLYPH_PADDING_PX
            }
        }
    }
}

// Experimentally-determined value
private const val INTER_GLYPH_PADDING_PX = 0.8f

@Composable
fun UnifiedBattery(
    viewModelFactory: BatteryViewModel.Factory,
    isDark: IsAreaDark,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberViewModel(traceName = "UnifiedBattery") { viewModelFactory.create() }
    val path = viewModel.batteryFrame

    var bounds by remember { mutableStateOf(Rect()) }

    val colorProvider = {
        if (isDark.isDark(bounds)) {
            viewModel.colorProfile.dark
        } else {
            viewModel.colorProfile.light
        }
    }

    BatteryCanvas(
        path = path,
        innerWidth = viewModel.innerWidth,
        innerHeight = viewModel.innerHeight,
        glyphs = viewModel.glyphList,
        level = viewModel.level,
        isFull = viewModel.isFull,
        colorsProvider = colorProvider,
        modifier =
            modifier.onLayoutRectChanged { relativeLayoutBounds ->
                bounds =
                    with(relativeLayoutBounds.boundsInScreen) { Rect(left, top, right, bottom) }
            },
        contentDescription = viewModel.contentDescription.load() ?: "",
    )
}

/** Calculate the right-edge of the clip for the fill-rect, based on a level of [0-100] */
private fun Int.scaledLevel(): Float {
    val endSide = BatteryFrame.innerWidth
    return ceil((toFloat() / 100f) * endSide)
}
