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

package com.android.systemui.qs.panels.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.geometry.Offset
import com.android.systemui.qs.panels.shared.model.SizedTile
import com.android.systemui.qs.panels.ui.compose.selection.PlacementEvent
import com.android.systemui.qs.panels.ui.model.GridCell
import com.android.systemui.qs.panels.ui.model.TileGridCell
import com.android.systemui.qs.panels.ui.model.toGridCells
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec

/**
 * Creates the edit tile list state that is remembered across compositions.
 *
 * Changes to the tiles or columns will recreate the state.
 */
@Composable
fun rememberEditListState(
    tiles: List<SizedTile<EditTileViewModel>>,
    columns: Int,
    largeTilesSpan: Int,
): EditTileListState {
    return remember(tiles, columns) { EditTileListState(tiles, columns, largeTilesSpan) }
}

/** Holds the temporary state of the tile list during a drag movement where we move tiles around. */
class EditTileListState(
    tiles: List<SizedTile<EditTileViewModel>>,
    private val columns: Int,
    private val largeTilesSpan: Int,
) : DragAndDropState {
    override var draggedCell by mutableStateOf<SizedTile<EditTileViewModel>?>(null)
        private set

    override var draggedPosition by mutableStateOf(Offset.Unspecified)
        private set

    override var dragType by mutableStateOf<DragType?>(null)
        private set

    // A dragged cell can be removed if it was added in the drag movement OR if it's marked as
    // removable
    override val isDraggedCellRemovable: Boolean
        get() = dragType == DragType.Add || draggedCell?.tile?.isRemovable ?: false

    override val dragInProgress: Boolean
        get() = draggedCell != null

    private val _tiles: SnapshotStateList<GridCell> =
        tiles.toGridCells(columns).toMutableStateList()
    val tiles: List<GridCell>
        get() = _tiles.toList()

    fun tileSpecs(): List<TileSpec> {
        return _tiles.filterIsInstance<TileGridCell>().map { it.tile.tileSpec }
    }

    private fun indexOf(tileSpec: TileSpec): Int {
        return _tiles.indexOfFirst { it is TileGridCell && it.tile.tileSpec == tileSpec }
    }

    fun isRemovable(tileSpec: TileSpec): Boolean {
        return _tiles.find {
            it is TileGridCell && it.tile.tileSpec == tileSpec && it.tile.isRemovable
        } != null
    }

    /** Resize the tile corresponding to the [TileSpec] to [toIcon] */
    fun resizeTile(tileSpec: TileSpec, toIcon: Boolean) {
        val fromIndex = indexOf(tileSpec)
        if (fromIndex != INVALID_INDEX) {
            val cell = _tiles[fromIndex] as TileGridCell

            if (cell.isIcon == toIcon) return

            _tiles.removeAt(fromIndex)
            _tiles.add(fromIndex, cell.copy(width = if (toIcon) 1 else largeTilesSpan))
            regenerateGrid(fromIndex)
        }
    }

    override fun isMoving(tileSpec: TileSpec): Boolean {
        return draggedCell?.let { it.tile.tileSpec == tileSpec } ?: false
    }

    override fun onStarted(cell: SizedTile<EditTileViewModel>, dragType: DragType) {
        draggedCell = cell
        this.dragType = dragType
    }

    override fun onTargeting(target: Int, insertAfter: Boolean) {
        val draggedTile = draggedCell ?: return

        val fromIndex = indexOf(draggedTile.tile.tileSpec)
        if (fromIndex == target) {
            return
        }

        val insertionIndex = if (insertAfter) target + 1 else target
        if (fromIndex != INVALID_INDEX) {
            val cell = _tiles.removeAt(fromIndex)
            regenerateGrid()
            _tiles.add(insertionIndex.coerceIn(0, _tiles.size), cell)
        } else {
            // Add the tile with a temporary row/col which will get reassigned when
            // regenerating spacers
            _tiles.add(insertionIndex.coerceIn(0, _tiles.size), TileGridCell(draggedTile, 0, 0))
        }

        regenerateGrid()
    }

    override fun onMoved(offset: Offset) {
        draggedPosition = offset
    }

    override fun movedOutOfBounds() {
        val draggedTile = draggedCell ?: return

        _tiles.removeIf { cell ->
            cell is TileGridCell && cell.tile.tileSpec == draggedTile.tile.tileSpec
        }
        draggedPosition = Offset.Unspecified

        // Regenerate spacers without the dragged tile
        regenerateGrid()
    }

    override fun onDrop() {
        draggedCell = null
        draggedPosition = Offset.Unspecified
        dragType = null

        // Remove the spacers
        regenerateGrid()
    }

    /**
     * Return the appropriate index to move the tile to for the placement [event]
     *
     * The grid includes spacers. As a result, indexes from the grid need to be translated to the
     * corresponding index from [currentTileSpecs].
     */
    fun targetIndexForPlacement(event: PlacementEvent): Int {
        val currentTileSpecs = tileSpecs()
        return when (event) {
            is PlacementEvent.PlaceToTileSpec -> {
                currentTileSpecs.indexOf(event.targetSpec)
            }
            is PlacementEvent.PlaceToIndex -> {
                if (event.targetIndex >= _tiles.size) {
                    currentTileSpecs.size
                } else if (event.targetIndex <= 0) {
                    0
                } else {
                    // The index may point to a spacer, so first find the first tile located
                    // after index, then use its position as a target
                    val targetTile =
                        _tiles.subList(event.targetIndex, _tiles.size).firstOrNull {
                            it is TileGridCell
                        } as? TileGridCell

                    if (targetTile == null) {
                        currentTileSpecs.size
                    } else {
                        val targetIndex = currentTileSpecs.indexOf(targetTile.tile.tileSpec)
                        val fromIndex = currentTileSpecs.indexOf(event.movingSpec)
                        if (fromIndex < targetIndex) targetIndex - 1 else targetIndex
                    }
                }
            }
        }
    }

    /** Regenerate the list of [GridCell] with their new potential rows */
    private fun regenerateGrid() {
        _tiles.filterIsInstance<TileGridCell>().toGridCells(columns).let {
            _tiles.clear()
            _tiles.addAll(it)
        }
    }

    /**
     * Regenerate the list of [GridCell] with their new potential rows from [fromIndex], leaving
     * cells before that untouched.
     */
    private fun regenerateGrid(fromIndex: Int) {
        val fromRow = _tiles[fromIndex].row
        val (pre, post) = _tiles.partition { it.row < fromRow }
        post.filterIsInstance<TileGridCell>().toGridCells(columns, startingRow = fromRow).let {
            _tiles.clear()
            _tiles.addAll(pre)
            _tiles.addAll(it)
        }
    }

    companion object {
        const val INVALID_INDEX = -1
    }
}
