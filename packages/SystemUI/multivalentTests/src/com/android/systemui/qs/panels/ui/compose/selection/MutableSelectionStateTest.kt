/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.qs.panels.ui.compose.selection

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.qs.panels.ui.compose.selection.TileState.GreyedOut
import com.android.systemui.qs.panels.ui.compose.selection.TileState.None
import com.android.systemui.qs.panels.ui.compose.selection.TileState.Placeable
import com.android.systemui.qs.panels.ui.compose.selection.TileState.Removable
import com.android.systemui.qs.panels.ui.compose.selection.TileState.Selected
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class MutableSelectionStateTest : SysuiTestCase() {
    private val underTest = MutableSelectionState()

    @Test
    fun selectTile_isCorrectlySelected() {
        assertThat(underTest.selection).isNotEqualTo(TEST_SPEC)

        underTest.select(TEST_SPEC)
        assertThat(underTest.selection).isEqualTo(TEST_SPEC)

        underTest.unSelect()
        assertThat(underTest.selection).isNull()

        val newSpec = TileSpec.create("newSpec")
        underTest.select(TEST_SPEC)
        underTest.select(newSpec)
        assertThat(underTest.selection).isEqualTo(newSpec)
    }

    @Test
    fun placementModeEnabled_tapOnIndex_sendsCorrectPlacementEvent() {
        // Tap while in placement mode
        underTest.enterPlacementMode(TEST_SPEC)
        underTest.onTap(2)

        assertThat(underTest.placementEnabled).isFalse()
        val event = underTest.placementEvent as PlacementEvent.PlaceToIndex
        assertThat(event.movingSpec).isEqualTo(TEST_SPEC)
        assertThat(event.targetIndex).isEqualTo(2)
    }

    @Test
    fun placementModeDisabled_tapOnIndex_doesNotSendPlacementEvent() {
        // Tap while placement mode is disabled
        underTest.onTap(2)

        assertThat(underTest.placementEnabled).isFalse()
        assertThat(underTest.placementEvent).isNull()
    }

    @Test
    fun placementModeEnabled_tapOnSelection_exitPlacementMode() {
        // Tap while in placement mode
        underTest.enterPlacementMode(TEST_SPEC)
        underTest.onTap(TEST_SPEC)

        assertThat(underTest.placementEnabled).isFalse()
        assertThat(underTest.placementEvent).isNull()
    }

    @Test
    fun placementModeEnabled_tapOnTileSpec_sendsCorrectPlacementEvent() {
        // Tap while in placement mode
        underTest.enterPlacementMode(TEST_SPEC)
        underTest.onTap(TEST_SPEC_2)

        assertThat(underTest.placementEnabled).isFalse()
        val event = underTest.placementEvent as PlacementEvent.PlaceToTileSpec
        assertThat(event.movingSpec).isEqualTo(TEST_SPEC)
        assertThat(event.targetSpec).isEqualTo(TEST_SPEC_2)
    }

    @Test
    fun placementModeDisabled_tapOnSelection_unselect() {
        // Select the tile and tap on it
        underTest.select(TEST_SPEC)
        underTest.onTap(TEST_SPEC)

        assertThat(underTest.placementEnabled).isFalse()
        assertThat(underTest.selected).isFalse()
    }

    @Test
    fun placementModeDisabled_tapOnTile_selects() {
        // Select a tile but tap a second one
        underTest.select(TEST_SPEC)
        underTest.onTap(TEST_SPEC_2)

        assertThat(underTest.placementEnabled).isFalse()
        assertThat(underTest.selection).isEqualTo(TEST_SPEC_2)
    }

    @Test
    fun tileStateFor_selectedTile_returnsSingleSelection() = runTest {
        underTest.select(TEST_SPEC)

        assertThat(underTest.tileStateFor(TEST_SPEC, None, canShowRemovalBadge = true))
            .isEqualTo(Selected)
        assertThat(underTest.tileStateFor(TEST_SPEC_2, None, canShowRemovalBadge = true))
            .isEqualTo(Removable)
        assertThat(underTest.tileStateFor(TEST_SPEC_3, None, canShowRemovalBadge = true))
            .isEqualTo(Removable)
    }

    @Test
    fun tileStateFor_placementMode_returnsSinglePlaceable() = runTest {
        underTest.enterPlacementMode(TEST_SPEC)

        assertThat(underTest.tileStateFor(TEST_SPEC, None, canShowRemovalBadge = true))
            .isEqualTo(Placeable)
        assertThat(underTest.tileStateFor(TEST_SPEC_2, None, canShowRemovalBadge = true))
            .isEqualTo(GreyedOut)
        assertThat(underTest.tileStateFor(TEST_SPEC_3, None, canShowRemovalBadge = true))
            .isEqualTo(GreyedOut)
    }

    @Test
    fun tileStateFor_nonRemovableTile_returnsNoneState() = runTest {
        assertThat(underTest.tileStateFor(TEST_SPEC, None, canShowRemovalBadge = true))
            .isEqualTo(Removable)
        assertThat(underTest.tileStateFor(TEST_SPEC_2, None, canShowRemovalBadge = false))
            .isEqualTo(None)
    }

    companion object {
        private val TEST_SPEC = TileSpec.create("testSpec")
        private val TEST_SPEC_2 = TileSpec.create("testSpec2")
        private val TEST_SPEC_3 = TileSpec.create("testSpec3")
    }
}
