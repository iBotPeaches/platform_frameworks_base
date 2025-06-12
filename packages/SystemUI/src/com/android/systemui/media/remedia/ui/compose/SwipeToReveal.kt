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
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.withoutVisualEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.android.compose.gesture.NestedDraggable
import com.android.compose.gesture.effect.OffsetOverscrollEffect
import com.android.compose.gesture.effect.rememberOffsetOverscrollEffect
import com.android.compose.gesture.nestedDraggable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Swipe to reveal that supports nested scrolling and an overscroll effect.
 *
 * @param foregroundContent The content to show above all else; this is the content that can be
 *   swiped sideways to reveal the [revealedContent]. This may contain a horizontally-scrollable
 *   component (for example a `HorizontalPager`).
 * @param revealedContent The content that is shown below the [foregroundContent]; this is the
 *   content that can be revealed by swiping the [foregroundContent] sideways.
 */
@Composable
fun SwipeToReveal(
    foregroundContent: @Composable (overscrollEffect: OverscrollEffect?) -> Unit,
    foregroundContentEffect: OffsetOverscrollEffect,
    revealedContent: @Composable BoxScope.(revealAmount: () -> Float) -> Unit,
    isSwipingEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // This composable supports an overscroll effect, to make it possible for the user to
    // "stretch" the UI when the side is fully revealed but the user keeps trying to reveal it
    // further.
    val revealedContentEffect = rememberOffsetOverscrollEffect()

    // This is the width of the revealed content UI box. It's not a state because it's not
    // observed in any composition and is an object with a value to avoid the extra cost
    // associated with boxing and unboxing an int.
    val revealedContentBoxWidth = remember {
        object {
            var value = 0
        }
    }

    // In order to support the drag to reveal, infrastructure has to be put in place where a
    // NestedDraggable helps by consuming the unconsumed drags and flings and applying the
    // overscroll visual effect.
    //
    // This is the NestedDraggalbe controller.
    val revealedContentDragController = rememberDismissibleContentDragController {
        revealedContentBoxWidth.value.toFloat()
    }

    Box(
        modifier =
            modifier
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
                                    return revealedContentDragController
                                }

                                override fun shouldConsumeNestedPostScroll(sign: Float): Boolean {
                                    return revealedContentDragController.shouldConsumePostScrolls(
                                        sign
                                    )
                                }

                                override fun shouldConsumeNestedPreScroll(sign: Float): Boolean {
                                    return revealedContentDragController.shouldConsumePreScrolls(
                                        sign
                                    )
                                }
                            }
                        },
                    orientation = Orientation.Horizontal,
                    overscrollEffect = revealedContentEffect.withoutVisualEffect(),
                )
                .overscroll(revealedContentEffect)
    ) {
        val density = LocalDensity.current

        /**
         * Returns the amount of visual offset, in pixels, that is comprised of both the offset from
         * dragging and the overscroll effect's additional pixels after applying its animation curve
         * on the raw distance.
         */
        fun offsetWithOverscroll(): Float {
            return revealedContentDragController.offset +
                OffsetOverscrollEffect.computeOffset(
                    density,
                    foregroundContentEffect.overscrollDistance,
                ) +
                OffsetOverscrollEffect.computeOffset(
                    density,
                    revealedContentEffect.overscrollDistance,
                )
        }

        /**
         * Returns the ratio of the amount by which the revealed content is revealed, where:
         * - `0` means none of it is revealed
         * - `+1` means all of it is revealed to the start of the foreground content
         * - `-1` means all of it is revealed to the end of the foreground content
         *
         * The number could be smaller than `-1` or larger than `+1` to model overscrolling.
         */
        fun revealAmount(): Float {
            return (offsetWithOverscroll() / revealedContentBoxWidth.value)
        }

        Layout(
            content = { revealedContent { revealAmount() } },
            modifier = Modifier.matchParentSize(),
        ) { measurables, constraints ->
            check(measurables.size == 1)
            val placeable = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
            // Keep revealedContentBoxWidth up to date with the latest value.
            revealedContentBoxWidth.value = placeable.measuredWidth

            // Place the revealed content on the correct side, depending on the direction of the
            // reveal.
            val alignedToStart = revealAmount() >= 0f
            layout(constraints.maxWidth, constraints.maxHeight) {
                coordinates?.size?.let { size ->
                    placeable.place(
                        x = if (alignedToStart) 0 else size.width - placeable.measuredWidth,
                        y = 0,
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier.absoluteOffset {
                    IntOffset(revealedContentDragController.offset.fastRoundToInt(), y = 0)
                }
        ) {
            foregroundContent(foregroundContentEffect)
        }
    }
}

@Composable
private fun rememberDismissibleContentDragController(
    maxBound: () -> Float
): RevealedContentDragController {
    val scope = rememberCoroutineScope()
    return remember { RevealedContentDragController(scope = scope, maxBound = maxBound) }
}

private class RevealedContentDragController(
    private val scope: CoroutineScope,
    private val maxBound: () -> Float,
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

        scope.launch { offsetAnimatable.animateTo(newTarget) }
        return velocity
    }
}
