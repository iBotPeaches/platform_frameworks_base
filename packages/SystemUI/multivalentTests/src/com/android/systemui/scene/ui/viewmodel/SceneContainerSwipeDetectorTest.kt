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

package com.android.systemui.scene.ui.viewmodel

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.EndEdge
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.EndHalf
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.Resolved.BottomEdge
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.Resolved.LeftEdge
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.Resolved.LeftHalf
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.Resolved.RightEdge
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.Resolved.RightHalf
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.Resolved.TopEdgeLeftHalf
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.Resolved.TopEdgeRightHalf
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.StartEdge
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea.StartHalf
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class SceneContainerSwipeDetectorTest : SysuiTestCase() {

    private val edgeSize = 40
    private val screenWidth = 800
    private val screenHeight = 600

    private val underTest = SceneContainerSwipeDetector(edgeSize = edgeSize.dp)

    @Test
    fun source_noEdge_detectsLeftHalf() {
        val detectedEdge = swipeVerticallyFrom(x = screenWidth / 2 - 1, y = screenHeight / 2)
        assertThat(detectedEdge).isEqualTo(LeftHalf)
    }

    @Test
    fun source_swipeVerticallyOnTopLeft_detectsTopEdgeLeftHalf() {
        val detectedEdge = swipeVerticallyFrom(x = 1, y = edgeSize - 1)
        assertThat(detectedEdge).isEqualTo(TopEdgeLeftHalf)
    }

    @Test
    fun source_swipeHorizontallyOnTopLeft_detectsLeftEdge() {
        val detectedEdge = swipeHorizontallyFrom(x = 1, y = edgeSize - 1)
        assertThat(detectedEdge).isEqualTo(LeftEdge)
    }

    @Test
    fun source_swipeVerticallyOnTopRight_detectsRightHalf() {
        val detectedEdge = swipeVerticallyFrom(x = screenWidth - 1, y = edgeSize - 1)
        assertThat(detectedEdge).isEqualTo(TopEdgeRightHalf)
    }

    @Test
    fun source_swipeHorizontallyOnTopRight_detectsRightEdge() {
        val detectedEdge = swipeHorizontallyFrom(x = screenWidth - 1, y = edgeSize - 1)
        assertThat(detectedEdge).isEqualTo(RightEdge)
    }

    @Test
    fun source_swipeVerticallyOnTopEdge_ToLeftOfSplit_detectsTopEdgeLeftHalf() {
        val detectedEdge = swipeVerticallyFrom(x = (screenWidth / 2) - 1, y = edgeSize - 1)
        assertThat(detectedEdge).isEqualTo(TopEdgeLeftHalf)
    }

    @Test
    fun source_swipeVerticallyBelowTopEdge_ToLeftOfSplit_detectsLeftHalf() {
        val detectedEdge = swipeVerticallyFrom(x = (screenWidth / 2) - 1, y = edgeSize + 1)
        assertThat(detectedEdge).isEqualTo(LeftHalf)
    }

    @Test
    fun source_swipeVerticallyOnTopEdge_toRightOfSplit_detectsTopEdgeRightHalf() {
        val detectedEdge = swipeVerticallyFrom(x = (screenWidth / 2) + 1, y = edgeSize - 1)
        assertThat(detectedEdge).isEqualTo(TopEdgeRightHalf)
    }

    @Test
    fun source_swipeVerticallyBelowTopEdge_toRightOfSplit_detectsRightHalf() {
        val detectedEdge = swipeVerticallyFrom(x = (screenWidth / 2) + 1, y = edgeSize + 1)
        assertThat(detectedEdge).isEqualTo(RightHalf)
    }

    @Test
    fun source_swipeVerticallyOnBottom_detectsBottomEdge() {
        val detectedEdge =
            swipeVerticallyFrom(x = screenWidth / 3, y = screenHeight - (edgeSize / 2))
        assertThat(detectedEdge).isEqualTo(BottomEdge)
    }

    @Test
    fun source_swipeHorizontallyOnBottom_detectsLeftHalf() {
        val detectedEdge =
            swipeHorizontallyFrom(x = screenWidth / 3, y = screenHeight - (edgeSize - 1))
        assertThat(detectedEdge).isEqualTo(LeftHalf)
    }

    @Test
    fun source_swipeHorizontallyOnLeft_detectsLeftEdge() {
        val detectedEdge = swipeHorizontallyFrom(x = edgeSize - 1, y = screenHeight / 2)
        assertThat(detectedEdge).isEqualTo(LeftEdge)
    }

    @Test
    fun source_swipeVerticallyOnLeft_detectsLeftHalf() {
        val detectedEdge = swipeVerticallyFrom(x = edgeSize - 1, y = screenHeight / 2)
        assertThat(detectedEdge).isEqualTo(LeftHalf)
    }

    @Test
    fun source_swipeHorizontallyOnRight_detectsRightEdge() {
        val detectedEdge =
            swipeHorizontallyFrom(x = screenWidth - edgeSize + 1, y = screenHeight / 2)
        assertThat(detectedEdge).isEqualTo(RightEdge)
    }

    @Test
    fun source_swipeVerticallyOnRight_detectsRightHalf() {
        val detectedEdge = swipeVerticallyFrom(x = screenWidth - edgeSize + 1, y = screenHeight / 2)
        assertThat(detectedEdge).isEqualTo(RightHalf)
    }

    @Test
    fun resolve_startEdgeInLtr_resolvesLeftEdge() {
        val resolvedEdge = StartEdge.resolve(LayoutDirection.Ltr)
        assertThat(resolvedEdge).isEqualTo(LeftEdge)
    }

    @Test
    fun resolve_startEdgeInRtl_resolvesRightEdge() {
        val resolvedEdge = StartEdge.resolve(LayoutDirection.Rtl)
        assertThat(resolvedEdge).isEqualTo(RightEdge)
    }

    @Test
    fun resolve_endEdgeInLtr_resolvesRightEdge() {
        val resolvedEdge = EndEdge.resolve(LayoutDirection.Ltr)
        assertThat(resolvedEdge).isEqualTo(RightEdge)
    }

    @Test
    fun resolve_endEdgeInRtl_resolvesLeftEdge() {
        val resolvedEdge = EndEdge.resolve(LayoutDirection.Rtl)
        assertThat(resolvedEdge).isEqualTo(LeftEdge)
    }

    @Test
    fun resolve_startHalfInLtr_resolvesLeftHalf() {
        val resolvedEdge = StartHalf.resolve(LayoutDirection.Ltr)
        assertThat(resolvedEdge).isEqualTo(LeftHalf)
    }

    @Test
    fun resolve_startHalfInRtl_resolvesRightHalf() {
        val resolvedEdge = StartHalf.resolve(LayoutDirection.Rtl)
        assertThat(resolvedEdge).isEqualTo(RightHalf)
    }

    @Test
    fun resolve_endHalfInLtr_resolvesRightHalf() {
        val resolvedEdge = EndHalf.resolve(LayoutDirection.Ltr)
        assertThat(resolvedEdge).isEqualTo(RightHalf)
    }

    @Test
    fun resolve_endHalfInRtl_resolvesLeftHalf() {
        val resolvedEdge = EndHalf.resolve(LayoutDirection.Rtl)
        assertThat(resolvedEdge).isEqualTo(LeftHalf)
    }

    private fun swipeVerticallyFrom(x: Int, y: Int): SceneContainerArea.Resolved? {
        return swipeFrom(x, y, Orientation.Vertical)
    }

    private fun swipeHorizontallyFrom(x: Int, y: Int): SceneContainerArea.Resolved? {
        return swipeFrom(x, y, Orientation.Horizontal)
    }

    private fun swipeFrom(x: Int, y: Int, orientation: Orientation): SceneContainerArea.Resolved? {
        return underTest.source(
            layoutSize = IntSize(width = screenWidth, height = screenHeight),
            position = IntOffset(x, y),
            density = Density(1f),
            orientation = orientation,
        )
    }
}
