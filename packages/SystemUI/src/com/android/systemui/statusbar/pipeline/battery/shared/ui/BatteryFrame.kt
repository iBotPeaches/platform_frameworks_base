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

package com.android.systemui.statusbar.pipeline.battery.shared.ui

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addSvg
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** Instead of reading from xml, we can define the battery here with a single Path */
object BatteryFrame {
    val pathSpec =
        PathSpec(
            path =
                Path().apply {
                    addSvg(
                        "M17.5 0H2C0.895431 0 0 0.895431 0 2V10C0 11.1046 0.89543 12 2 12H17.5C18.6046 12 19.5 11.1046 19.5 10V8H19.9231C20.5178 8 21 7.51785 21 6.92308V5.07692C21 4.48215 20.5178 4 19.9231 4H19.5V2C19.5 0.895431 18.6046 0 17.5 0Z"
                    )
                },
            viewportHeight = 12.dp,
            viewportWidth = 21.dp,
        )

    /** The width of the drawable that is usable for inside elements */
    const val innerWidth = 19.5f

    /** The height of the drawable that is usable for inside elements */
    const val innerHeight = 12f
}

/**
 * Encapsulates both the [Path] and the drawn dimensions of the internal SVG path. Use [scaleTo] to
 * determine the appropriate scale factor (x and y) to fit the frame into its container
 */
data class PathSpec(val path: Path, val viewportWidth: Dp, val viewportHeight: Dp) {
    /** Return the appropriate scale to achieve FIT_CENTER-type scaling */
    fun scaleTo(w: Float, h: Float): Float {
        // FIT_CENTER for the path, this determines how much we need to scale up to fit the bounds
        // without skewing
        val xScale = w / viewportWidth.value
        val yScale = h / viewportHeight.value

        return min(xScale, yScale)
    }
}
