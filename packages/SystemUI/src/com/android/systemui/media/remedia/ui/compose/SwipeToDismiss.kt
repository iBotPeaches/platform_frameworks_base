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

package com.android.systemui.media.remedia.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.overscroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.android.compose.gesture.NestedDraggable
import com.android.compose.gesture.effect.rememberOffsetOverscrollEffect
import com.android.compose.gesture.nestedDraggable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Swipe to dismiss that supports nested scrolling. */
@Composable
fun SwipeToDismiss(
    content: @Composable () -> Unit,
    isSwipingEnabled: Boolean,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overscrollEffect = rememberOffsetOverscrollEffect()

    // This is the width of the content UI box. It's not a state because it's not observed in any
    // composition and is an object with a value to avoid the extra cost associated with boxing and
    // unboxing an int.
    val contentBoxWidth = remember {
        object {
            var value = 0
        }
    }

    // In order to support the drag to dismiss, infrastructure has to be put in place where a
    // NestedDraggable helps by consuming the unconsumed drags and flings and applying the offset.
    //
    // This is the NestedDraggalbe controller.
    val dragController =
        rememberDismissibleContentDragController(
            maxBound = { contentBoxWidth.value.toFloat() },
            onDismissed = onDismissed,
        )

    Box(
        modifier =
            modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    contentBoxWidth.value = placeable.measuredWidth
                    layout(placeable.measuredWidth, placeable.measuredHeight) {
                        placeable.place(0, 0)
                    }
                }
                .nestedDraggable(
                    enabled = isSwipingEnabled,
                    draggable =
                        remember {
                            object : NestedDraggable {
                                override fun onDragStarted(
                                    position: Offset,
                                    sign: Float,
                                    pointersDown: Int,
                                    pointerType: PointerType?,
                                ): NestedDraggable.Controller {
                                    return dragController
                                }

                                override fun shouldConsumeNestedPostScroll(sign: Float): Boolean {
                                    return dragController.shouldConsumePostScrolls(sign)
                                }

                                override fun shouldConsumeNestedPreScroll(sign: Float): Boolean {
                                    return dragController.shouldConsumePreScrolls(sign)
                                }
                            }
                        },
                    orientation = Orientation.Horizontal,
                )
                .overscroll(overscrollEffect)
                .absoluteOffset { IntOffset(dragController.offset.fastRoundToInt(), y = 0) }
    ) {
        content()
    }
}

@Composable
private fun rememberDismissibleContentDragController(
    maxBound: () -> Float,
    onDismissed: () -> Unit,
): DismissibleContentDragController {
    val scope = rememberCoroutineScope()
    return remember {
        DismissibleContentDragController(
            scope = scope,
            maxBound = maxBound,
            onDismissed = onDismissed,
        )
    }
}

private class DismissibleContentDragController(
    private val scope: CoroutineScope,
    private val maxBound: () -> Float,
    private val onDismissed: () -> Unit,
) : NestedDraggable.Controller {
    private val offsetAnimatable = Animatable(0f)
    private var lastTarget = 0f
    private var range = 0f..1f
    private var shouldConsumePreScrolls by mutableStateOf(false)

    override val autoStopNestedDrags: Boolean
        get() = true

    val offset: Float
        get() = offsetAnimatable.value

    fun shouldConsumePreScrolls(sign: Float): Boolean {
        if (!shouldConsumePreScrolls) return false

        if (lastTarget > 0f && sign < 0f) {
            range = 0f..maxBound()
            return true
        }

        if (lastTarget < 0f && sign > 0f) {
            range = -maxBound()..0f
            return true
        }

        return false
    }

    fun shouldConsumePostScrolls(sign: Float): Boolean {
        val max = maxBound()
        if (sign > 0f && lastTarget < max) {
            range = 0f..maxBound()
            return true
        }

        if (sign < 0f && lastTarget > -max) {
            range = -maxBound()..0f
            return true
        }

        return false
    }

    override fun onDrag(delta: Float): Float {
        val previousTarget = lastTarget
        lastTarget = (lastTarget + delta).fastCoerceIn(range.start, range.endInclusive)
        val newTarget = lastTarget
        scope.launch { offsetAnimatable.snapTo(newTarget) }
        return lastTarget - previousTarget
    }

    override suspend fun onDragStopped(velocity: Float, awaitFling: suspend () -> Unit): Float {
        val rangeMiddle = range.start + (range.endInclusive - range.start) / 2f
        lastTarget =
            when {
                lastTarget >= rangeMiddle -> range.endInclusive
                else -> range.start
            }

        shouldConsumePreScrolls = lastTarget != 0f
        val newTarget = lastTarget

        scope.launch {
            offsetAnimatable.animateTo(newTarget)
            if (newTarget != 0f) {
                onDismissed()
            }
        }
        return velocity
    }
}
