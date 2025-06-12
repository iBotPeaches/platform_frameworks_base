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
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.IntSize
import com.android.compose.animation.scene.DataPointTypes.scale
import com.android.compose.animation.scene.testing.lastAlphaForTesting
import com.android.compose.animation.scene.testing.lastOffsetForTesting
import com.android.compose.animation.scene.testing.lastScaleForTesting
import com.android.compose.animation.scene.testing.lastSizeForTesting
import platform.test.motion.compose.DataPointTypes.intSize
import platform.test.motion.compose.DataPointTypes.offset
import platform.test.motion.golden.DataPoint
import platform.test.motion.golden.DataPointTypes
import platform.test.motion.golden.FeatureCapture

/**
 * [FeatureCapture] implementations to record animated state of [SceneTransitionLayout] [Element].
 */
object FeatureCaptures {

    val elementAlpha =
        FeatureCapture<SemanticsNode, Float>("alpha") {
            DataPoint.of(it.lastAlphaForTesting, DataPointTypes.float)
        }

    val elementScale =
        FeatureCapture<SemanticsNode, Scale>("scale") {
            DataPoint.of(it.lastScaleForTesting, scale)
        }

    val elementOffset =
        FeatureCapture<SemanticsNode, Offset>("offset") {
            DataPoint.of(it.lastOffsetForTesting, offset)
        }

    val elementSize =
        FeatureCapture<SemanticsNode, IntSize>("size") {
            DataPoint.of(it.lastSizeForTesting, intSize)
        }
}
