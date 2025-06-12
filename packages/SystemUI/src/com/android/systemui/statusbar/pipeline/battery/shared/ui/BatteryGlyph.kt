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
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * *What*
 *
 * Here we define the glyphs that can show in the battery icon. Anything that can render inside of
 * the composed icon must be defined here, as an svg path with a [width] and [height]. The
 * dimensions of the glyphs should be relative to a 19.5x12 canvas.
 *
 * *Why*
 *
 * In short:
 * 1. text rendering is too heavyweight for what we need here.
 * 2. We don't want to rely on the existence of fonts for correctness
 *
 * We need _exactly_ the glyphs representing "0" -> "9", plus a small handful of attributions to
 * render inside of the battery asset itself. Doing this with text + other svg assets means that we
 * need to lean on the entire text rendering stack just to get 1-3 characters to show. This would
 * also end up taking into account things like ellipsizing, which we straight up do not need.
 *
 * Secondly, we want this to work at all display sizes _without depending on the source font for
 * correctness_. This icon should render correctly as if it were a collection of pre-baked svg
 * assets.
 *
 * *How can I change the font*
 *
 * In order to customize the look of these glyphs, you can do the following:
 * 1. Render your asset (0-9 digit, or other symbol) into a 19.5x12 canvas
 * 2. Make sure that this asset fits in all potential other contexts 2a. If you are updating a
 *    number, make sure it fits with all other numbers, and next to any attributions (charging
 *    symbol, power save symbol, etc.) 2b. If you are updating a symbol, make sure likewise that it
 *    fits next to all number pairings
 * 3. Trace the glyph into an SVG path. _Ensure that there is no extra whitespace around the SVG
 *    path!_
 * 4. Update or add the glyph here, copying the SVG path and updating the [width] and [height] to
 *    the appropriate value from the svg view box
 *
 * *What about localization?*
 *
 * Localization will be handled manually. Given that we are throwing away the text system, we will
 * have to discern every textual variant of the 0-9 glyphs and override their values here based on
 * the locale. This is still being worked on and the design is TBD.
 *
 * *Why are there "Large" variants?*
 *
 * To keep things simple, we just package up a given attribution potentially twice. If displaying by
 * itself, then we can use the "large" variant of the given glyph. Else, we consider it to be inline
 * amongst other glyphs and use the default version. The selection between which one to use happens
 * down in the view model layer.
 */

/** Top-level, common interface. Glyphs are all defined on a 19.5x12 canvas */
interface Glyph {
    /** The exact width of this glyph, on the 19.5x12 canvas */
    val width: Float
    /** The exact height of this glyph, on the 19.5x12 canvas */
    val height: Float

    fun draw(drawScope: DrawScope, colors: BatteryColors)
}

/** If you have just a single path, we can draw you for free */
interface SinglePathGlyph : Glyph {
    /** A single path defines this glyph, thus its draw function is simple */
    val path: Path

    /** Draw this glyph in the given [drawScope], with the given [colors] */
    override fun draw(drawScope: DrawScope, colors: BatteryColors) {
        drawScope.apply { drawPath(path, color = colors.glyph) }
    }
}

/** Text bad, glyph good */
sealed interface BatteryGlyph : Glyph {
    data object Bolt : BatteryGlyph, SinglePathGlyph {
        override val path: Path =
            Path().apply {
                addSvg(
                    "M2.766,4.23L1.588,6.545C1.448,6.869 1.808,7.171 2.046,6.931L5.913,3.12C6.106,2.958 6.004,2.657 5.815,2.679L3.08,2.679L4.46,0.489C4.468,0.477 4.474,0.464 4.479,0.451C4.594,0.132 4.236,-0.149 4.006,0.094L0.1,3.783C-0.068,3.921 -0.005,4.238 0.2,4.23L2.766,4.23Z"
                )
            }

        override val width: Float = 6.01f
        override val height: Float = 7.02f
    }

    data object BoltLarge : BatteryGlyph, SinglePathGlyph {
        override val path =
            Path().apply {
                addSvg(
                    "M7.191,3L4.031,3L4.721,0.5C4.791,0.25 4.601,0 4.341,0C4.231,0 4.121,0.05 4.051,0.13L0.081,4.49C-0.099,4.69 0.041,5 0.311,5L3.471,5L2.781,7.5C2.711,7.75 2.901,8 3.161,8C3.271,8 3.381,7.95 3.451,7.87L7.421,3.51C7.601,3.31 7.461,3 7.191,3Z"
                )
            }

        override val width: Float = 7.5f
        override val height: Float = 8f
    }

    data object Plus : BatteryGlyph, SinglePathGlyph {
        override val path =
            Path().apply {
                addSvg(
                    "M3.719,0.724C3.719,0.324 3.395,0 2.996,0C2.596,0 2.272,0.324 2.272,0.724L2.272,2.276L0.719,2.276C0.32,2.276 -0.004,2.6 -0.004,3C-0.004,3.4 0.32,3.724 0.719,3.724L2.272,3.724L2.272,5.276C2.272,5.676 2.596,6 2.996,6C3.395,6 3.719,5.676 3.719,5.276L3.719,3.724L5.272,3.724C5.672,3.724 5.996,3.4 5.996,3C5.996,2.6 5.672,2.276 5.272,2.276L3.719,2.276L3.719,0.724Z"
                )
            }

        override val width: Float = 6f
        override val height: Float = 6f
    }

    data object PlusLarge : BatteryGlyph, SinglePathGlyph {
        override val path =
            Path().apply {
                addSvg(
                    "M5.586,1.086C5.586,0.486 5.1,0 4.5,0C3.9,0 3.414,0.486 3.414,1.086L3.414,3.414L1.086,3.414C0.486,3.414 0,3.9 0,4.5C0,5.1 0.486,5.586 1.086,5.586L3.414,5.586L3.414,7.914C3.414,8.514 3.9,9 4.5,9C5.1,9 5.586,8.514 5.586,7.914L5.586,5.586L7.914,5.586C8.514,5.586 9,5.1 9,4.5C9,3.9 8.514,3.414 7.914,3.414L5.586,3.414L5.586,1.086Z"
                )
            }

        override val width: Float = 9f
        override val height: Float = 9f
    }

    data object Defend : BatteryGlyph {
        private val fgPath =
            Path().apply {
                addSvg(
                    "M4.915,1.774C5.027,1.811 5.129,1.84 5.214,1.861C5.332,1.889 5.431,1.99 5.427,2.111C5.38,3.714 4.811,5.322 3.203,5.964L3.203,1.036C3.319,1.034 3.418,1.061 3.502,1.119C3.679,1.24 3.92,1.367 4.173,1.482C4.426,1.597 4.691,1.7 4.915,1.774Z"
                )
            }

        private val bgPath =
            Path().apply {
                addSvg(
                    "M3.602,0.118C3.373,-0.039 2.967,-0.039 2.738,0.118C2.486,0.29 2.144,0.47 1.784,0.633C1.425,0.797 1.049,0.943 0.73,1.048C0.571,1.1 0.426,1.143 0.305,1.172C0.138,1.212 -0.002,1.356 0.003,1.527C0.07,3.804 0.886,6.088 3.17,7C5.454,6.088 6.27,3.804 6.337,1.527C6.342,1.356 6.202,1.212 6.035,1.172C5.914,1.143 5.769,1.1 5.61,1.048C5.291,0.943 4.915,0.797 4.556,0.633C4.196,0.47 3.854,0.29 3.602,0.118Z"
                )
            }

        override fun draw(drawScope: DrawScope, colors: BatteryColors) {
            drawScope.apply {
                // Bg path first
                drawPath(path = bgPath, color = colors.glyph)
                // Then fg path, so it renders on top. Use the fill color so it matches
                drawPath(path = fgPath, color = colors.fill)
            }
        }

        override val width = 6.33f
        override val height = 7f
    }

    data object DefendLarge : BatteryGlyph {
        private val fgPath =
            Path().apply {
                addSvg(
                    "M3.5,6.919C4.12,6.559 5.75,5.349 5.98,2.409C5.32,2.159 4.37,1.729 3.5,1.129L3.5,6.919Z"
                )
            }

        private val bgPath =
            Path().apply {
                addSvg(
                    "M3.5,8.009C3.29,8.009 0.17,6.639 0,2.079C0,1.859 0.13,1.659 0.33,1.589C0.92,1.379 2.18,0.879 3.19,0.109C3.28,0.039 3.39,-0.001 3.5,-0.001C3.61,-0.001 3.72,0.039 3.81,0.109C4.82,0.879 6.08,1.379 6.67,1.589C6.88,1.659 7.01,1.859 7,2.079C6.83,6.639 3.71,8.009 3.5,8.009Z"
                )
            }

        override fun draw(drawScope: DrawScope, colors: BatteryColors) {
            drawScope.apply {
                // Bg path first
                drawPath(path = bgPath, color = colors.glyph)
                // Then fg path, so it renders on top. Use the fill color so it matches
                drawPath(path = fgPath, color = colors.fill, alpha = 1f)
            }
        }

        override val width = 7.01f
        override val height = 8f
    }

    data object One : BatteryGlyph, SinglePathGlyph {
        override val path =
            Path().apply {
                addSvg(
                    "M3.308,9.524C3.044,9.524 2.816,9.429 2.626,9.238C2.435,9.047 2.34,8.818 2.34,8.549L2.34,2.517L1.339,3.141C1.135,3.262 0.916,3.304 0.682,3.265C0.448,3.221 0.266,3.1 0.136,2.901C0.01,2.705 -0.029,2.491 0.019,2.257C0.067,2.019 0.19,1.837 0.39,1.711L2.769,0.19C2.847,0.138 2.925,0.095 3.003,0.06C3.081,0.021 3.189,0.001 3.328,0.001C3.596,0.001 3.822,0.097 4.004,0.287C4.186,0.478 4.277,0.712 4.277,0.989L4.277,8.549C4.277,8.818 4.181,9.047 3.991,9.238C3.8,9.429 3.572,9.524 3.308,9.524Z"
                )
            }

        override val width = 4.28f
        override val height = 9.52f
    }

    data object Two : BatteryGlyph, SinglePathGlyph {
        override val path =
            Path().apply {
                addSvg(
                    "M1.091,9.47C0.779,9.47 0.519,9.37 0.311,9.171C0.103,8.967 -0.001,8.716 -0.001,8.417C-0.001,8.274 0.021,8.157 0.064,8.066C0.107,7.975 0.155,7.891 0.207,7.813L2.131,4.562C2.378,4.181 2.577,3.839 2.729,3.535C2.881,3.228 2.957,2.946 2.957,2.69L2.957,2.398C2.957,2.173 2.907,1.999 2.807,1.878C2.712,1.757 2.564,1.696 2.365,1.696C2.222,1.696 2.103,1.737 2.008,1.819C1.912,1.898 1.839,2.006 1.787,2.144C1.735,2.279 1.674,2.42 1.605,2.567C1.535,2.714 1.418,2.827 1.254,2.905C1.093,2.983 0.913,3 0.714,2.957C0.515,2.914 0.35,2.803 0.22,2.625C0.09,2.443 0.036,2.229 0.058,1.982C0.084,1.731 0.188,1.438 0.37,1.105C0.556,0.771 0.822,0.504 1.169,0.305C1.516,0.101 1.96,-0.001 2.502,-0.001C3.217,-0.001 3.795,0.199 4.237,0.598C4.683,0.996 4.906,1.551 4.906,2.261L4.906,2.502C4.906,2.935 4.818,3.358 4.64,3.77C4.462,4.181 4.222,4.619 3.918,5.083L2.411,7.702L2.437,7.748L4.302,7.748C4.54,7.748 4.744,7.832 4.913,8.001C5.086,8.17 5.173,8.372 5.173,8.606C5.173,8.844 5.086,9.048 4.913,9.217C4.744,9.385 4.54,9.47 4.302,9.47L1.091,9.47Z"
                )
            }

        override val width = 5.17f
        override val height = 9.47f
    }

    data object Three : BatteryGlyph, SinglePathGlyph {
        override val path =
            Path().apply {
                addSvg(
                    "M2.433,9.633C1.883,9.633 1.434,9.531 1.088,9.327C0.741,9.123 0.473,8.842 0.282,8.482C0.096,8.122 0,7.826 -0.004,7.592C-0.004,7.353 0.059,7.154 0.184,6.993C0.314,6.833 0.477,6.731 0.672,6.688C0.867,6.645 1.044,6.66 1.205,6.733C1.365,6.807 1.48,6.905 1.549,7.026C1.623,7.147 1.686,7.284 1.738,7.436C1.79,7.587 1.866,7.715 1.965,7.819C2.065,7.923 2.204,7.975 2.381,7.975C2.594,7.975 2.763,7.904 2.888,7.76C3.014,7.613 3.077,7.384 3.077,7.071L3.077,6.298C3.077,5.973 3.01,5.741 2.875,5.602C2.745,5.464 2.555,5.394 2.303,5.394L2.154,5.394C1.954,5.394 1.781,5.321 1.634,5.174C1.491,5.026 1.419,4.851 1.419,4.647C1.419,4.443 1.489,4.27 1.627,4.127C1.77,3.98 1.939,3.906 2.134,3.906L2.277,3.906C2.511,3.906 2.68,3.839 2.784,3.704C2.893,3.566 2.947,3.334 2.947,3.009L2.947,2.391C2.947,2.118 2.895,1.921 2.791,1.8C2.691,1.679 2.548,1.618 2.362,1.618C2.214,1.618 2.095,1.655 2.004,1.728C1.918,1.798 1.848,1.895 1.796,2.021C1.749,2.147 1.692,2.264 1.627,2.372C1.562,2.48 1.45,2.571 1.289,2.645C1.129,2.714 0.956,2.73 0.769,2.69C0.587,2.647 0.431,2.545 0.301,2.385C0.176,2.22 0.122,2.01 0.139,1.754C0.161,1.499 0.267,1.224 0.457,0.929C0.652,0.634 0.912,0.407 1.237,0.246C1.567,0.082 1.974,-0.001 2.459,-0.001C3.183,-0.001 3.755,0.188 4.175,0.565C4.6,0.942 4.812,1.453 4.812,2.099L4.812,2.573C4.812,3.089 4.7,3.503 4.474,3.815C4.249,4.123 3.928,4.346 3.512,4.484L3.512,4.536C3.998,4.632 4.37,4.844 4.63,5.174C4.895,5.498 5.027,5.96 5.027,6.558L5.027,7.163C5.027,7.938 4.793,8.545 4.325,8.983C3.861,9.416 3.231,9.633 2.433,9.633Z"
                )
            }

        override val width = 5.03f
        override val height = 9.63f
    }

    data object Four : BatteryGlyph, SinglePathGlyph {
        override val path =
            Path().apply {
                addSvg(
                    "M3.839,9.544C3.579,9.544 3.356,9.451 3.169,9.264C2.987,9.078 2.896,8.855 2.896,8.595L2.896,6.691L3.078,6.457L3.078,1.926L4.099,2.556L3.065,2.556L1.668,5.93L3.709,5.93L4.099,5.819L4.892,5.819C5.121,5.819 5.319,5.902 5.483,6.066C5.652,6.227 5.737,6.422 5.737,6.652C5.737,6.881 5.652,7.078 5.483,7.243C5.319,7.408 5.121,7.49 4.892,7.49L1.174,7.49C0.84,7.49 0.561,7.382 0.335,7.165C0.11,6.944 -0.003,6.673 -0.003,6.352C-0.003,6.205 0.015,6.099 0.049,6.034C0.084,5.969 0.121,5.898 0.16,5.819L2.461,0.717C2.552,0.526 2.699,0.359 2.903,0.216C3.111,0.073 3.334,0.002 3.572,0.002C3.906,0.002 4.19,0.123 4.424,0.366C4.662,0.609 4.781,0.901 4.781,1.243L4.781,8.595C4.781,8.855 4.688,9.078 4.502,9.264C4.315,9.451 4.094,9.544 3.839,9.544Z"
                )
            }

        override val width = 5.74f
        override val height = 9.54f
    }

    data object Five : BatteryGlyph, SinglePathGlyph {
        override val path =
            Path().apply {
                addSvg(
                    "M2.442,9.473C1.935,9.473 1.51,9.388 1.168,9.219C0.826,9.05 0.555,8.818 0.356,8.524C0.156,8.225 0.042,7.926 0.011,7.627C-0.015,7.328 0.039,7.089 0.174,6.912C0.312,6.734 0.477,6.625 0.668,6.587C0.863,6.548 1.036,6.563 1.188,6.632C1.344,6.701 1.456,6.792 1.526,6.905C1.595,7.013 1.658,7.141 1.714,7.289C1.775,7.432 1.853,7.553 1.948,7.653C2.043,7.748 2.176,7.796 2.345,7.796C2.561,7.796 2.73,7.72 2.852,7.568C2.977,7.416 3.04,7.159 3.04,6.795L3.04,5.716C3.04,5.382 2.982,5.146 2.865,5.007C2.752,4.864 2.598,4.793 2.403,4.793C2.265,4.793 2.152,4.825 2.065,4.89C1.983,4.951 1.907,5.031 1.838,5.131C1.755,5.235 1.63,5.315 1.461,5.371C1.296,5.427 1.103,5.425 0.882,5.365C0.657,5.299 0.477,5.165 0.343,4.962C0.208,4.754 0.15,4.533 0.167,4.299L0.369,1.062C0.39,0.776 0.514,0.529 0.739,0.321C0.965,0.108 1.222,0.002 1.513,0.002L3.82,0.002C4.058,0.002 4.262,0.087 4.431,0.256C4.6,0.425 4.685,0.626 4.685,0.86C4.685,1.098 4.6,1.302 4.431,1.471C4.262,1.64 4.058,1.724 3.82,1.724L1.896,1.724L1.753,3.85L1.805,3.863C1.944,3.703 2.119,3.575 2.332,3.48C2.548,3.384 2.795,3.337 3.073,3.337C3.662,3.337 4.13,3.551 4.477,3.98C4.823,4.409 4.997,5.029 4.997,5.839L4.997,6.717C4.997,7.618 4.771,8.303 4.321,8.771C3.87,9.239 3.244,9.473 2.442,9.473Z"
                )
            }

        override val width = 4.99f
        override val height = 9.47f
    }

    data object Six : BatteryGlyph, SinglePathGlyph {
        override val path =
            Path().apply {
                addSvg(
                    "M2.628,9.519C1.795,9.519 1.15,9.268 0.69,8.765C0.231,8.262 0.001,7.56 0.001,6.659L0.001,5.99C0.001,5.383 0.09,4.83 0.268,4.332C0.45,3.829 0.742,3.231 1.145,2.538L2.289,0.445C2.411,0.233 2.591,0.094 2.829,0.029C3.067,-0.036 3.293,-0.006 3.505,0.12C3.722,0.246 3.858,0.432 3.915,0.679C3.975,0.922 3.936,1.151 3.798,1.368L3.161,2.59C2.944,2.967 2.662,3.318 2.316,3.643C1.969,3.964 1.73,4.726 1.6,5.931L0.339,6.016C0.374,5.231 0.643,4.603 1.145,4.13C1.648,3.658 2.289,3.422 3.069,3.422C3.702,3.422 4.222,3.647 4.629,4.098C5.037,4.544 5.24,5.188 5.24,6.029L5.24,6.711C5.24,7.569 5.009,8.251 4.545,8.758C4.081,9.265 3.442,9.519 2.628,9.519ZM2.621,7.907C2.855,7.907 3.033,7.822 3.154,7.654C3.275,7.48 3.336,7.207 3.336,6.834L3.336,5.976C3.336,5.621 3.275,5.352 3.154,5.17C3.033,4.989 2.855,4.897 2.621,4.897C2.391,4.897 2.216,4.989 2.095,5.17C1.973,5.352 1.913,5.623 1.913,5.983L1.913,6.834C1.913,7.207 1.973,7.48 2.095,7.654C2.216,7.822 2.391,7.907 2.621,7.907Z"
                )
            }

        override val width = 5.24f
        override val height = 9.52f
    }

    data object Seven : BatteryGlyph, SinglePathGlyph {
        override val path =
            Path().apply {
                addSvg(
                    "M1.481,9.278C1.234,9.187 1.056,9.02 0.948,8.777C0.839,8.53 0.835,8.283 0.935,8.036L3.112,1.77L3.093,1.724L0.87,1.724C0.632,1.724 0.426,1.64 0.252,1.471C0.083,1.298 -0.001,1.094 -0.001,0.86C-0.001,0.626 0.083,0.425 0.252,0.256C0.426,0.087 0.632,0.002 0.87,0.002L4.191,0.002C4.503,0.002 4.763,0.104 4.971,0.308C5.184,0.511 5.29,0.763 5.29,1.062C5.29,1.205 5.277,1.315 5.251,1.393C5.225,1.467 5.19,1.582 5.147,1.737L2.709,8.712C2.618,8.963 2.452,9.145 2.209,9.258C1.971,9.366 1.728,9.373 1.481,9.278Z"
                )
            }

        override val width = 5.29f
        override val height = 9.34f
    }

    data object Eight : BatteryGlyph, SinglePathGlyph {
        override val path =
            Path().apply {
                addSvg(
                    "M2.611,9.633C1.788,9.633 1.147,9.411 0.687,8.97C0.228,8.528 -0.002,7.945 -0.002,7.221L-0.002,6.538C-0.002,6.019 0.117,5.592 0.356,5.258C0.594,4.92 0.913,4.679 1.311,4.536L1.311,4.484C0.973,4.342 0.7,4.123 0.492,3.828C0.289,3.533 0.187,3.158 0.187,2.704L0.187,2.19C0.187,1.527 0.406,0.996 0.843,0.598C1.285,0.199 1.875,-0.001 2.611,-0.001C3.344,-0.001 3.929,0.194 4.366,0.584C4.808,0.974 5.029,1.51 5.029,2.19L5.029,2.704C5.029,3.167 4.921,3.546 4.704,3.841C4.492,4.136 4.223,4.35 3.898,4.484L3.898,4.536C4.301,4.679 4.622,4.918 4.86,5.252C5.099,5.581 5.218,6.01 5.218,6.538L5.218,7.215C5.218,7.947 4.99,8.534 4.535,8.976C4.085,9.414 3.443,9.633 2.611,9.633ZM2.611,8.008C2.837,8.008 3.01,7.93 3.131,7.774C3.257,7.618 3.32,7.386 3.32,7.078L3.32,6.181C3.32,5.873 3.257,5.646 3.131,5.498C3.01,5.347 2.837,5.271 2.611,5.271C2.377,5.271 2.2,5.347 2.078,5.498C1.957,5.646 1.896,5.873 1.896,6.181L1.896,7.078C1.896,7.386 1.957,7.618 2.078,7.774C2.204,7.93 2.382,8.008 2.611,8.008ZM2.605,3.958C2.813,3.958 2.969,3.889 3.073,3.75C3.181,3.607 3.235,3.405 3.235,3.145L3.235,2.405C3.235,2.136 3.181,1.934 3.073,1.8C2.964,1.666 2.811,1.598 2.611,1.598C2.408,1.598 2.252,1.666 2.143,1.8C2.035,1.934 1.981,2.136 1.981,2.405L1.981,3.145C1.981,3.405 2.033,3.607 2.137,3.75C2.245,3.889 2.401,3.958 2.605,3.958Z"
                )
            }

        override val width = 5.22f
        override val height = 9.63f
    }

    data object Nine : BatteryGlyph, SinglePathGlyph {
        override val path =
            Path().apply {
                addSvg(
                    "M2.612,0.003C3.444,0.003 4.089,0.254 4.549,0.757C5.008,1.26 5.238,1.962 5.238,2.863L5.238,3.532C5.238,4.139 5.147,4.694 4.965,5.196C4.787,5.695 4.495,6.291 4.087,6.984L2.95,9.077C2.828,9.289 2.648,9.428 2.41,9.493C2.172,9.558 1.947,9.528 1.734,9.402C1.518,9.276 1.379,9.092 1.318,8.85C1.262,8.603 1.303,8.371 1.442,8.154L2.079,6.932C2.295,6.555 2.577,6.206 2.924,5.885C3.27,5.561 3.509,4.796 3.639,3.591L4.9,3.507C4.861,4.291 4.59,4.919 4.087,5.391C3.589,5.864 2.95,6.1 2.17,6.1C1.537,6.1 1.017,5.877 0.61,5.431C0.202,4.98 -0.001,4.334 -0.001,3.493L-0.001,2.811C-0.001,1.953 0.231,1.27 0.694,0.763C1.158,0.256 1.797,0.003 2.612,0.003ZM2.618,1.615C2.384,1.615 2.207,1.702 2.085,1.875C1.964,2.044 1.903,2.315 1.903,2.687L1.903,3.545C1.903,3.901 1.964,4.169 2.085,4.352C2.207,4.534 2.384,4.624 2.618,4.624C2.848,4.624 3.023,4.534 3.145,4.352C3.266,4.169 3.327,3.899 3.327,3.539L3.327,2.687C3.327,2.315 3.266,2.044 3.145,1.875C3.023,1.702 2.848,1.615 2.618,1.615Z"
                )
            }

        override val width = 5.24f
        override val height = 9.52f
    }

    data object Zero : BatteryGlyph, SinglePathGlyph {
        override val path =
            Path().apply {
                addSvg(
                    "M2.728,9.633C1.87,9.633 1.201,9.318 0.72,8.69C0.239,8.062 -0.002,7.071 -0.002,5.719L-0.002,3.906C-0.002,2.554 0.239,1.566 0.72,0.942C1.201,0.314 1.87,-0.001 2.728,-0.001C3.587,-0.001 4.256,0.314 4.737,0.942C5.218,1.566 5.458,2.552 5.458,3.9L5.458,5.719C5.458,7.067 5.214,8.057 4.724,8.69C4.239,9.318 3.574,9.633 2.728,9.633ZM2.728,7.878C2.98,7.878 3.168,7.758 3.294,7.52C3.424,7.277 3.489,6.848 3.489,6.233L3.489,3.399C3.489,2.784 3.426,2.357 3.3,2.118C3.175,1.876 2.984,1.754 2.728,1.754C2.473,1.754 2.282,1.876 2.156,2.118C2.035,2.357 1.975,2.784 1.975,3.399L1.975,6.233C1.975,6.848 2.037,7.277 2.163,7.52C2.293,7.758 2.482,7.878 2.728,7.878Z"
                )
            }

        override val width = 5.46f
        override val height = 9.63f
    }
}
