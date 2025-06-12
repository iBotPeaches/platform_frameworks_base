/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.communal.ui.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import com.android.systemui.Flags.communalWidgetResizing
import com.android.systemui.Flags.glanceableHubV2
import com.android.systemui.communal.domain.model.CommunalContentModel
import com.android.systemui.communal.shared.model.CommunalContentSize
import com.android.systemui.communal.ui.compose.extensions.firstItemAtOffset
import com.android.systemui.communal.ui.compose.extensions.plus
import com.android.systemui.communal.ui.viewmodel.BaseCommunalViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

private fun Float.directional(origin: LayoutDirection, current: LayoutDirection): Float =
    if (origin == current) this else -this

@Composable
fun rememberGridDragDropState(
    gridState: LazyGridState,
    contentListState: ContentListState,
    updateDragPositionForRemove: (boundingBox: IntRect) -> Boolean,
): GridDragDropState {
    val coroutineScope = rememberCoroutineScope()
    val autoScrollThreshold = with(LocalDensity.current) { 60.dp.toPx() }

    val state =
        remember(gridState, contentListState, updateDragPositionForRemove) {
            GridDragDropState(
                gridState = gridState,
                contentListState = contentListState,
                coroutineScope = coroutineScope,
                autoScrollThreshold = autoScrollThreshold,
                updateDragPositionForRemove = updateDragPositionForRemove,
            )
        }

    LaunchedEffect(state) { state.processScrollRequests(coroutineScope) }

    return state
}

/**
 * Handles drag and drop cards in the glanceable hub. While dragging to move, other items that are
 * affected will dynamically get positioned and the state is tracked by [ContentListState]. When
 * dragging to remove, affected cards will be moved and [updateDragPositionForRemove] is called to
 * check whether the dragged item can be removed. On dragging ends, call [ContentListState.onRemove]
 * to remove the dragged item if condition met and call [ContentListState.onSaveList] to persist any
 * change in ordering.
 */
class GridDragDropState(
    val gridState: LazyGridState,
    contentListState: ContentListState,
    coroutineScope: CoroutineScope,
    autoScrollThreshold: Float,
    private val updateDragPositionForRemove: (draggingBoundingBox: IntRect) -> Boolean,
) {
    private val dragDropState: GridDragDropStateInternal =
        if (glanceableHubV2()) {
            GridDragDropStateV2(
                gridState = gridState,
                contentListState = contentListState,
                scope = coroutineScope,
                autoScrollThreshold = autoScrollThreshold,
                updateDragPositionForRemove = updateDragPositionForRemove,
            )
        } else {
            GridDragDropStateV1(
                gridState = gridState,
                contentListState = contentListState,
                scope = coroutineScope,
                updateDragPositionForRemove = updateDragPositionForRemove,
            )
        }

    val draggingItemKey: String?
        get() = dragDropState.draggingItemKey

    val isDraggingToRemove: Boolean
        get() = dragDropState.isDraggingToRemove

    val draggingItemOffset: Offset
        get() = dragDropState.draggingItemOffset

    /**
     * Called when dragging is initiated.
     *
     * @return {@code True} if dragging a grid item, {@code False} otherwise.
     */
    fun onDragStart(
        offset: Offset,
        screenWidth: Int,
        layoutDirection: LayoutDirection,
        contentOffset: Offset,
    ): Boolean = dragDropState.onDragStart(offset, screenWidth, layoutDirection, contentOffset)

    fun onDragInterrupted() = dragDropState.onDragInterrupted()

    fun onDrag(offset: Offset, layoutDirection: LayoutDirection) =
        dragDropState.onDrag(offset, layoutDirection)

    suspend fun processScrollRequests(coroutineScope: CoroutineScope) =
        dragDropState.processScrollRequests(coroutineScope)
}

/**
 * A private base class defining the API for handling drag-and-drop operations. There will be two
 * implementations of this class: V1 for devices that do not have the glanceable_hub_v2 flag
 * enabled, and V2 for devices that do have that flag enabled.
 *
 * TODO(b/400789179): Remove this class and the V1 implementation once glanceable_hub_v2 has
 *   shipped.
 */
private open class GridDragDropStateInternal(protected val state: LazyGridState) {
    var draggingItemKey by mutableStateOf<String?>(null)
        protected set

    var isDraggingToRemove by mutableStateOf(false)
        protected set

    var draggingItemDraggedDelta by mutableStateOf(Offset.Zero)
    var draggingItemInitialOffset by mutableStateOf(Offset.Zero)

    val draggingItemOffset: Offset
        get() =
            draggingItemLayoutInfo?.let { item ->
                draggingItemInitialOffset + draggingItemDraggedDelta - item.offset.toOffset()
            } ?: Offset.Zero

    val draggingItemLayoutInfo: LazyGridItemInfo?
        get() = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingItemKey }

    /**
     * Called when dragging is initiated.
     *
     * @return {@code True} if dragging a grid item, {@code False} otherwise.
     */
    open fun onDragStart(
        offset: Offset,
        screenWidth: Int,
        layoutDirection: LayoutDirection,
        contentOffset: Offset,
    ): Boolean = false

    open fun onDragInterrupted() = Unit

    open fun onDrag(offset: Offset, layoutDirection: LayoutDirection) = Unit

    open suspend fun processScrollRequests(coroutineScope: CoroutineScope) = Unit
}

/**
 * The V1 implementation of GridDragDropStateInternal to be used when the glanceable_hub_v2 flag is
 * disabled.
 */
private class GridDragDropStateV1(
    val gridState: LazyGridState,
    private val contentListState: ContentListState,
    private val scope: CoroutineScope,
    private val updateDragPositionForRemove: (draggingBoundingBox: IntRect) -> Boolean,
) : GridDragDropStateInternal(gridState) {
    private val scrollChannel = Channel<Float>()

    private val spacer = CommunalContentModel.Spacer(CommunalContentSize.Responsive(1))
    private var spacerIndex: Int? = null

    private var previousTargetItemKey: Any? = null

    override suspend fun processScrollRequests(coroutineScope: CoroutineScope) {
        while (true) {
            val diff = scrollChannel.receive()
            state.scrollBy(diff)
        }
    }

    override fun onDragStart(
        offset: Offset,
        screenWidth: Int,
        layoutDirection: LayoutDirection,
        contentOffset: Offset,
    ): Boolean {
        val normalizedOffset =
            Offset(
                if (layoutDirection == LayoutDirection.Ltr) offset.x else screenWidth - offset.x,
                offset.y,
            )
        state.layoutInfo.visibleItemsInfo
            .filter { item -> contentListState.isItemEditable(item.index) }
            // grid item offset is based off grid content container so we need to deduct
            // before content padding from the initial pointer position
            .firstItemAtOffset(normalizedOffset - contentOffset)
            ?.apply {
                draggingItemKey = key as String
                draggingItemInitialOffset = this.offset.toOffset()
                // Add a spacer after the last widget if it is larger than the dragging widget.
                // This allows overscrolling, enabling the dragging widget to be placed beyond it.
                val lastWidget = contentListState.list.lastOrNull { it.isWidgetContent() }
                if (
                    lastWidget != null &&
                        draggingItemLayoutInfo != null &&
                        lastWidget.size.span > draggingItemLayoutInfo!!.span
                ) {
                    contentListState.list.add(spacer)
                    spacerIndex = contentListState.list.size - 1
                }
                return true
            }

        return false
    }

    override fun onDragInterrupted() {
        draggingItemKey?.let {
            if (isDraggingToRemove) {
                contentListState.onRemove(
                    contentListState.list.indexOfFirst { it.key == draggingItemKey }
                )
                isDraggingToRemove = false
                updateDragPositionForRemove(IntRect.Zero)
            }
            // persist list editing changes on dragging ends
            contentListState.onSaveList()
            draggingItemKey = null
        }
        previousTargetItemKey = null
        draggingItemDraggedDelta = Offset.Zero
        draggingItemInitialOffset = Offset.Zero
        // Remove spacer, if any, when a drag gesture finishes.
        spacerIndex?.let {
            contentListState.list.removeAt(it)
            spacerIndex = null
        }
    }

    override fun onDrag(offset: Offset, layoutDirection: LayoutDirection) {
        // Adjust offset to match the layout direction
        draggingItemDraggedDelta +=
            Offset(offset.x.directional(LayoutDirection.Ltr, layoutDirection), offset.y)

        val draggingItem = draggingItemLayoutInfo ?: return
        val startOffset = draggingItem.offset.toOffset() + draggingItemOffset
        val endOffset = startOffset + draggingItem.size.toSize()
        val middleOffset = startOffset + (endOffset - startOffset) / 2f
        val draggingBoundingBox =
            IntRect(draggingItem.offset + draggingItemOffset.round(), draggingItem.size)

        val targetItem =
            if (communalWidgetResizing()) {
                state.layoutInfo.visibleItemsInfo.findLast { item ->
                    val lastVisibleItemIndex = state.layoutInfo.visibleItemsInfo.last().index
                    val itemBoundingBox = IntRect(item.offset, item.size)
                    draggingItemKey != item.key &&
                        contentListState.isItemEditable(item.index) &&
                        (draggingBoundingBox.contains(itemBoundingBox.center) ||
                            itemBoundingBox.contains(draggingBoundingBox.center)) &&
                        // If we swap with the last visible item, and that item doesn't fit
                        // in the gap created by moving the current item, then the current item
                        // will get placed after the last visible item. In this case, it gets
                        // placed outside of the viewport. We avoid this here, so the user
                        // has to scroll first before the swap can happen.
                        (item.index != lastVisibleItemIndex || item.span <= draggingItem.span)
                }
            } else {
                state.layoutInfo.visibleItemsInfo
                    .asSequence()
                    .filter { item -> contentListState.isItemEditable(item.index) }
                    .filter { item -> draggingItem.index != item.index }
                    .firstItemAtOffset(middleOffset)
            }

        if (
            targetItem != null &&
                (!communalWidgetResizing() || targetItem.key != previousTargetItemKey)
        ) {
            val scrollToIndex =
                if (targetItem.index == state.firstVisibleItemIndex) {
                    draggingItem.index
                } else if (draggingItem.index == state.firstVisibleItemIndex) {
                    targetItem.index
                } else {
                    null
                }
            if (communalWidgetResizing()) {
                // Keep track of the previous target item, to avoid rapidly oscillating between
                // items if the target item doesn't visually move as a result of the index change.
                // In this case, even after the index changes, we'd still be colliding with the
                // element, so it would be selected as the target item the next time this function
                // runs again, which would trigger us to revert the index change we recently made.
                previousTargetItemKey = targetItem.key
            }
            if (scrollToIndex != null) {
                scope.launch {
                    // this is needed to neutralize automatic keeping the first item first.
                    state.scrollToItem(scrollToIndex, state.firstVisibleItemScrollOffset)
                    contentListState.onMove(draggingItem.index, targetItem.index)
                }
            } else {
                contentListState.onMove(draggingItem.index, targetItem.index)
            }
            isDraggingToRemove = false
        } else if (targetItem == null) {
            val overscroll = checkForOverscroll(startOffset, endOffset)
            if (overscroll != 0f) {
                scrollChannel.trySend(overscroll)
            }
            isDraggingToRemove = checkForRemove(draggingBoundingBox)
            previousTargetItemKey = null
        }
    }

    /** Calculate the amount dragged out of bound on both sides. Returns 0f if not overscrolled */
    private fun checkForOverscroll(startOffset: Offset, endOffset: Offset): Float {
        return when {
            draggingItemDraggedDelta.x > 0 ->
                (endOffset.x - state.layoutInfo.viewportEndOffset).coerceAtLeast(0f)
            draggingItemDraggedDelta.x < 0 ->
                (startOffset.x - state.layoutInfo.viewportStartOffset).coerceAtMost(0f)
            else -> 0f
        }
    }

    /** Calls the callback with the updated drag position and returns whether to remove the item. */
    private fun checkForRemove(draggingItemBoundingBox: IntRect): Boolean {
        return if (draggingItemDraggedDelta.y < 0) {
            updateDragPositionForRemove(draggingItemBoundingBox)
        } else {
            false
        }
    }
}

/**
 * The V2 implementation of GridDragDropStateInternal to be used when the glanceable_hub_v2 flag is
 * enabled.
 */
private class GridDragDropStateV2(
    val gridState: LazyGridState,
    private val contentListState: ContentListState,
    private val scope: CoroutineScope,
    private val autoScrollThreshold: Float,
    private val updateDragPositionForRemove: (draggingBoundingBox: IntRect) -> Boolean,
) : GridDragDropStateInternal(gridState) {

    private val scrollChannel = Channel<Float>(Channel.UNLIMITED)

    // Used to keep track of the dragging item during scrolling (because it might be off screen
    // and no longer in the list of visible items).
    private var draggingItemWhileScrolling: LazyGridItemInfo? by mutableStateOf(null)

    private val spacer = CommunalContentModel.Spacer(CommunalContentSize.Responsive(1))
    private var spacerIndex: Int? = null

    private var previousTargetItemKey: Any? = null

    // Basically, the location of the user's finger on the screen.
    private var currentDragPositionOnScreen by mutableStateOf(Offset.Zero)
    // The offset of the grid from the top of the screen.
    private var contentOffset = Offset.Zero

    // The width of one column in the grid (needed in order to auto-scroll one column at a time).
    private var columnWidth = 0

    override suspend fun processScrollRequests(coroutineScope: CoroutineScope) {
        while (true) {
            val amount = scrollChannel.receive()

            if (state.isScrollInProgress) {
                // Ignore overscrolling if a scroll is already in progress (but we still want to
                // consume the scroll event so that we don't end up processing a bunch of old
                // events after scrolling has finished).
                continue
            }

            // We perform the rest of the drag action after scrolling has finished (or immediately
            // if there will be no scrolling).
            if (amount != 0f) {
                coroutineScope.launch {
                    state.animateScrollBy(amount, tween(delayMillis = 250, durationMillis = 1000))
                    performDragAction()
                }
            } else {
                performDragAction()
            }
        }
    }

    override fun onDragStart(
        offset: Offset,
        screenWidth: Int,
        layoutDirection: LayoutDirection,
        contentOffset: Offset,
    ): Boolean {
        val normalizedOffset =
            Offset(
                if (layoutDirection == LayoutDirection.Ltr) offset.x else screenWidth - offset.x,
                offset.y,
            )

        currentDragPositionOnScreen = normalizedOffset
        this.contentOffset = contentOffset

        state.layoutInfo.visibleItemsInfo
            .filter { item -> contentListState.isItemEditable(item.index) }
            // grid item offset is based off grid content container so we need to deduct
            // before content padding from the initial pointer position
            .firstItemAtOffset(normalizedOffset - contentOffset)
            ?.apply {
                draggingItemKey = key as String
                draggingItemWhileScrolling = this
                draggingItemInitialOffset = this.offset.toOffset()
                columnWidth =
                    this.size.width +
                        state.layoutInfo.beforeContentPadding +
                        state.layoutInfo.afterContentPadding
                // Add a spacer after the last widget if it is larger than the dragging widget.
                // This allows overscrolling, enabling the dragging widget to be placed beyond it.
                val lastWidget = contentListState.list.lastOrNull { it.isWidgetContent() }
                if (
                    lastWidget != null &&
                        draggingItemLayoutInfo != null &&
                        lastWidget.size.span > draggingItemLayoutInfo!!.span
                ) {
                    contentListState.list.add(spacer)
                    spacerIndex = contentListState.list.size - 1
                }
                return true
            }

        return false
    }

    override fun onDragInterrupted() {
        draggingItemKey?.let {
            if (isDraggingToRemove) {
                contentListState.onRemove(
                    contentListState.list.indexOfFirst { it.key == draggingItemKey }
                )
                isDraggingToRemove = false
                updateDragPositionForRemove(IntRect.Zero)
            }
            // persist list editing changes on dragging ends
            contentListState.onSaveList()
            draggingItemKey = null
        }
        previousTargetItemKey = null
        draggingItemDraggedDelta = Offset.Zero
        draggingItemInitialOffset = Offset.Zero
        currentDragPositionOnScreen = Offset.Zero
        draggingItemWhileScrolling = null
        // Remove spacer, if any, when a drag gesture finishes.
        spacerIndex?.let {
            contentListState.list.removeAt(it)
            spacerIndex = null
        }
    }

    override fun onDrag(offset: Offset, layoutDirection: LayoutDirection) {
        // Adjust offset to match the layout direction
        val delta = Offset(offset.x.directional(LayoutDirection.Ltr, layoutDirection), offset.y)
        draggingItemDraggedDelta += delta
        currentDragPositionOnScreen += delta

        scrollChannel.trySend(computeAutoscroll(currentDragPositionOnScreen))
    }

    fun performDragAction() {
        val draggingItem = draggingItemLayoutInfo ?: draggingItemWhileScrolling
        if (draggingItem == null) {
            return
        }

        val draggingBoundingBox =
            IntRect(draggingItem.offset + draggingItemOffset.round(), draggingItem.size)
        val curDragPositionInGrid = (currentDragPositionOnScreen - contentOffset)

        val targetItem =
            if (communalWidgetResizing()) {
                val lastVisibleItemIndex = state.layoutInfo.visibleItemsInfo.last().index
                state.layoutInfo.visibleItemsInfo.findLast(
                    fun(item): Boolean {
                        val itemBoundingBox = IntRect(item.offset, item.size)
                        return draggingItemKey != item.key &&
                            contentListState.isItemEditable(item.index) &&
                            itemBoundingBox.contains(curDragPositionInGrid.round()) &&
                            // If we swap with the last visible item, and that item doesn't fit
                            // in the gap created by moving the current item, then the current item
                            // will get placed after the last visible item. In this case, it gets
                            // placed outside of the viewport. We avoid this here, so the user
                            // has to scroll first before the swap can happen.
                            (item.index != lastVisibleItemIndex || item.span <= draggingItem.span)
                    }
                )
            } else {
                state.layoutInfo.visibleItemsInfo
                    .asSequence()
                    .filter { item -> contentListState.isItemEditable(item.index) }
                    .filter { item -> draggingItem.index != item.index }
                    .firstItemAtOffset(curDragPositionInGrid)
            }

        if (
            targetItem != null &&
                (!communalWidgetResizing() || targetItem.key != previousTargetItemKey)
        ) {
            val scrollToIndex =
                if (targetItem.index == state.firstVisibleItemIndex) {
                    draggingItem.index
                } else if (draggingItem.index == state.firstVisibleItemIndex) {
                    targetItem.index
                } else {
                    null
                }
            if (communalWidgetResizing()) {
                // Keep track of the previous target item, to avoid rapidly oscillating between
                // items if the target item doesn't visually move as a result of the index change.
                // In this case, even after the index changes, we'd still be colliding with the
                // element, so it would be selected as the target item the next time this function
                // runs again, which would trigger us to revert the index change we recently made.
                previousTargetItemKey = targetItem.key
            }
            if (scrollToIndex != null) {
                scope.launch {
                    // this is needed to neutralize automatic keeping the first item first.
                    state.scrollToItem(scrollToIndex, state.firstVisibleItemScrollOffset)
                    contentListState.swapItems(draggingItem.index, targetItem.index)
                }
            } else {
                contentListState.swapItems(draggingItem.index, targetItem.index)
            }
            draggingItemWhileScrolling = targetItem
            isDraggingToRemove = false
        } else if (targetItem == null) {
            isDraggingToRemove = checkForRemove(draggingBoundingBox)
            previousTargetItemKey = null
        }
    }

    /** Calculate the amount dragged out of bound on both sides. Returns 0f if not overscrolled. */
    private fun computeAutoscroll(dragOffset: Offset): Float {
        val orientation = state.layoutInfo.orientation
        val distanceFromStart =
            if (orientation == Orientation.Horizontal) {
                dragOffset.x
            } else {
                dragOffset.y
            }
        val distanceFromEnd =
            if (orientation == Orientation.Horizontal) {
                state.layoutInfo.viewportEndOffset - dragOffset.x
            } else {
                state.layoutInfo.viewportEndOffset - dragOffset.y
            }

        return when {
            distanceFromEnd < autoScrollThreshold -> {
                (columnWidth - state.layoutInfo.beforeContentPadding).toFloat()
            }
            distanceFromStart < autoScrollThreshold -> {
                -(columnWidth - state.layoutInfo.afterContentPadding).toFloat()
            }
            else -> 0f
        }
    }

    /** Calls the callback with the updated drag position and returns whether to remove the item. */
    private fun checkForRemove(draggingItemBoundingBox: IntRect): Boolean {
        return if (draggingItemDraggedDelta.y < 0) {
            updateDragPositionForRemove(draggingItemBoundingBox)
        } else {
            false
        }
    }
}

fun Modifier.dragContainer(
    dragDropState: GridDragDropState,
    layoutDirection: LayoutDirection,
    screenWidth: Int,
    contentOffset: Offset,
    viewModel: BaseCommunalViewModel,
): Modifier {
    return this.then(
        Modifier.pointerInput(dragDropState, contentOffset) {
            detectDragGesturesAfterLongPress(
                onDrag = { change, offset ->
                    change.consume()
                    dragDropState.onDrag(offset, layoutDirection)
                },
                onDragStart = { offset ->
                    if (
                        dragDropState.onDragStart(
                            offset,
                            screenWidth,
                            layoutDirection,
                            contentOffset,
                        )
                    ) {
                        // draggingItemKey is guaranteed to be non-null here because it is set in
                        // onDragStart()
                        viewModel.onReorderWidgetStart(dragDropState.draggingItemKey!!)
                    }
                },
                onDragEnd = {
                    dragDropState.onDragInterrupted()
                    viewModel.onReorderWidgetEnd()
                },
                onDragCancel = {
                    dragDropState.onDragInterrupted()
                    viewModel.onReorderWidgetCancel()
                },
            )
        }
    )
}

/** Wrap LazyGrid item with additional modifier needed for drag and drop. */
@ExperimentalFoundationApi
@Composable
fun LazyGridItemScope.DraggableItem(
    dragDropState: GridDragDropState,
    key: Any,
    enabled: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    if (!enabled) {
        return content(false)
    }

    val dragging = key == dragDropState.draggingItemKey
    val itemAlpha: Float by
        animateFloatAsState(
            targetValue = if (dragDropState.isDraggingToRemove) 0.5f else 1f,
            label = "DraggableItemAlpha",
        )
    val direction = LocalLayoutDirection.current
    val draggingModifier =
        if (dragging) {
            Modifier.graphicsLayer {
                translationX =
                    dragDropState.draggingItemOffset.x.directional(LayoutDirection.Ltr, direction)
                translationY = dragDropState.draggingItemOffset.y
                alpha = itemAlpha
            }
        } else {
            Modifier.animateItem()
        }

    // Animate the highlight alpha manually as alpha modifier (and AnimatedVisibility) clips the
    // widget to bounds, which cuts off the highlight as we are drawing outside the widget bounds.
    val highlightSelected = !communalWidgetResizing() && selected
    val alpha by
        animateFloatAsState(
            targetValue =
                if ((dragging || highlightSelected) && !dragDropState.isDraggingToRemove) {
                    1f
                } else {
                    0f
                },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "Widget outline alpha",
        )

    Box(modifier) {
        HighlightedItem(Modifier.matchParentSize(), alpha = alpha)
        Box(draggingModifier) { content(dragging) }
    }
}
