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

package com.android.compose.animation.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.geometry.isUnspecified
import org.json.JSONObject
import platform.test.motion.golden.DataPointType
import platform.test.motion.golden.UnknownTypeException

fun Scale.asDataPoint() = DataPointTypes.scale.makeDataPoint(this)

object DataPointTypes {
    val scale: DataPointType<Scale> =
        DataPointType(
            "scale",
            jsonToValue = {
                when (it) {
                    "unspecified" -> Scale.Unspecified
                    "default" -> Scale.Default
                    "zero" -> Scale.Zero
                    is JSONObject -> {
                        val pivot = it.get("pivot")
                        Scale(
                            scaleX = it.getDouble("x").toFloat(),
                            scaleY = it.getDouble("y").toFloat(),
                            pivot =
                                when (pivot) {
                                    "unspecified" -> Offset.Unspecified
                                    "infinite" -> Offset.Infinite
                                    is JSONObject ->
                                        Offset(
                                            pivot.getDouble("x").toFloat(),
                                            pivot.getDouble("y").toFloat(),
                                        )
                                    else -> throw UnknownTypeException()
                                },
                        )
                    }
                    else -> throw UnknownTypeException()
                }
            },
            valueToJson = {
                when (it) {
                    Scale.Unspecified -> "unspecified"
                    Scale.Default -> "default"
                    Scale.Zero -> "zero"
                    else -> {
                        JSONObject().apply {
                            put("x", it.scaleX)
                            put("y", it.scaleY)
                            put(
                                "pivot",
                                when {
                                    it.pivot.isUnspecified -> "unspecified"
                                    !it.pivot.isFinite -> "infinite"
                                    else ->
                                        JSONObject().apply {
                                            put("x", it.pivot.x)
                                            put("y", it.pivot.y)
                                        }
                                },
                            )
                        }
                    }
                }
            },
        )
}
