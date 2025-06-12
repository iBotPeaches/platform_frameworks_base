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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.android.systemui.common.ui.compose.gestures.detectEagerTapGestures
import com.android.systemui.qs.pipeline.shared.TileSpec
import kotlinx.coroutines.delay

/** Creates the state of the current selected tile that is remembered across compositions. */
@Composable
fun rememberSelectionState(): MutableSelectionState {
    return remember { MutableSelectionState() }
}

/** Holds the state of the current selection. */
class MutableSelectionState {
    /** The [TileSpec] of a tile is selected, null if not. */
    var selection by mutableStateOf<TileSpec?>(null)
        private set

    /**
     * Whether the current selection is in placement mode or not.
     *
     * A tile in placement mode can be positioned by tapping at the desired location in the grid.
     */
    var placementEnabled by mutableStateOf(false)
        private set

    /** Latest event from coming from placement mode. */
    var placementEvent by mutableStateOf<PlacementEvent?>(null)

    val selected: Boolean
        get() = selection != null

    fun select(tileSpec: TileSpec) {
        selection = tileSpec
    }

    fun unSelect() {
        selection = null
        exitPlacementMode()
    }

    /** Selects [tileSpec] and enable placement mode. */
    fun enterPlacementMode(tileSpec: TileSpec) {
        selection = tileSpec
        placementEnabled = true
    }

    /** Disable placement mode but maintains current selection. */
    private fun exitPlacementMode() {
        placementEnabled = false
    }

    fun togglePlacementMode(tileSpec: TileSpec) {
        if (placementEnabled) exitPlacementMode() else enterPlacementMode(tileSpec)
    }

    suspend fun tileStateFor(
        tileSpec: TileSpec,
        previousState: TileState,
        canShowRemovalBadge: Boolean,
    ): TileState {
        return when {
            placementEnabled && selection == tileSpec -> TileState.Placeable
            placementEnabled -> TileState.GreyedOut
            selection == tileSpec -> {
                if (previousState == TileState.None && canShowRemovalBadge) {
                    // The tile decoration is None if a tile is newly composed OR the removal
                    // badge can't be shown.
                    // For newly composed and selected tiles, such as dragged tiles or moved
                    // tiles from resizing, introduce a short delay. This avoids clipping issues
                    // on the border and resizing handle, as well as letting the selection
                    // animation play correctly.
                    delay(250)
                }
                TileState.Selected
            }
            canShowRemovalBadge -> TileState.Removable
            else -> TileState.None
        }
    }

    /**
     * Tap callback on a tile.
     *
     * Tiles can be selected and placed using placement mode.
     */
    fun onTap(tileSpec: TileSpec) {
        when {
            placementEnabled && selection == tileSpec -> {
                exitPlacementMode()
            }
            placementEnabled -> {
                selection?.let { placementEvent = PlacementEvent.PlaceToTileSpec(it, tileSpec) }
                exitPlacementMode()
            }
            selection == tileSpec -> {
                unSelect()
            }
            else -> {
                select(tileSpec)
            }
        }
    }

    /**
     * Tap on a position.
     *
     * Use on grid items not associated with a [TileSpec], such as a spacer. Spacers can't be
     * selected, but selections can be moved to their position.
     */
    fun onTap(index: Int) {
        when {
            placementEnabled -> {
                selection?.let { placementEvent = PlacementEvent.PlaceToIndex(it, index) }
                exitPlacementMode()
            }
            selected -> {
                unSelect()
            }
        }
    }
}

// Not using data classes here as distinct placement events may have the same moving spec and target
@Stable
sealed interface PlacementEvent {
    val movingSpec: TileSpec

    /** Placement event corresponding to [movingSpec] moving to [targetSpec]'s position */
    class PlaceToTileSpec(override val movingSpec: TileSpec, val targetSpec: TileSpec) :
        PlacementEvent

    /** Placement event corresponding to [movingSpec] moving to [targetIndex] */
    class PlaceToIndex(override val movingSpec: TileSpec, val targetIndex: Int) : PlacementEvent
}

/**
 * Listens for click events on selectable tiles.
 *
 * Use this on current tiles as they can be selected.
 *
 * @param tileSpec the [TileSpec] of the tile this modifier is applied to
 * @param selectionState the [MutableSelectionState] representing the grid's selection
 */
@Composable
fun Modifier.selectableTile(tileSpec: TileSpec, selectionState: MutableSelectionState): Modifier {
    return pointerInput(Unit) {
        detectEagerTapGestures(
            doubleTapEnabled = {
                // Double tap enabled if where not in placement mode already
                !selectionState.placementEnabled
            },
            onDoubleTap = { selectionState.enterPlacementMode(tileSpec) },
            onTap = { selectionState.onTap(tileSpec) },
        )
    }
}
