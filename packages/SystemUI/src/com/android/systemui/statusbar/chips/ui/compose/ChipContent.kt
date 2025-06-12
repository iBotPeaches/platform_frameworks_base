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

package com.android.systemui.statusbar.chips.ui.compose

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.dp
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.formatTimeRemainingData
import com.android.systemui.statusbar.chips.ui.viewmodel.rememberChronometerState
import com.android.systemui.statusbar.chips.ui.viewmodel.rememberTimeRemainingState
import kotlin.math.min

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChipContent(viewModel: OngoingActivityChipModel.Active, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textStyle = MaterialTheme.typography.labelLargeEmphasized
    val textColor = Color(viewModel.colors.text(context))
    val maxTextWidth = dimensionResource(id = R.dimen.ongoing_activity_chip_max_text_width)
    val icon = viewModel.icon
    val startPadding =
        if (icon != null && !icon.hasEmbeddedPadding) {
            // Add padding only if this text is next to an icon that doesn't embed its own padding
            dimensionResource(id = R.dimen.ongoing_activity_chip_icon_text_padding)
        } else {
            0.dp
        }
    // Include endPadding in the Text instead of the outer OngoingActivityChip so that if the text
    // is hidden because it's too large, then the remaining icon is still centered
    val endPadding =
        if (icon?.hasEmbeddedPadding == true) {
            dimensionResource(
                id = R.dimen.ongoing_activity_chip_text_end_padding_for_embedded_padding_icon
            )
        } else {
            0.dp
        }
    val textMeasurer = rememberTextMeasurer()
    when (viewModel) {
        is OngoingActivityChipModel.Active.Timer -> {
            val timerState =
                rememberChronometerState(
                    eventTimeMillis = viewModel.startTimeMs,
                    isCountDown = viewModel.isEventInFuture,
                    timeSource = viewModel.timeSource,
                )
            timerState.currentTimeText?.let { text ->
                Text(
                    text = text,
                    style = textStyle,
                    color = textColor,
                    softWrap = false,
                    modifier =
                        modifier
                            .hideTextIfDoesNotFit(
                                text = text,
                                textStyle = textStyle,
                                textMeasurer = textMeasurer,
                                maxTextWidth = maxTextWidth,
                                startPadding = startPadding,
                                endPadding = endPadding,
                            )
                            .neverDecreaseWidth(density),
                )
            }
        }

        is OngoingActivityChipModel.Active.Countdown -> {
            val text = viewModel.secondsUntilStarted.toString()
            Text(
                text = text,
                style = textStyle,
                color = textColor,
                softWrap = false,
                modifier = modifier.neverDecreaseWidth(density),
            )
        }

        is OngoingActivityChipModel.Active.Text -> {
            val text = viewModel.text
            Text(
                text = text,
                color = textColor,
                style = textStyle,
                softWrap = false,
                modifier =
                    modifier.hideTextIfDoesNotFit(
                        text = text,
                        textStyle = textStyle,
                        textMeasurer = textMeasurer,
                        maxTextWidth = maxTextWidth,
                        startPadding = startPadding,
                        endPadding = endPadding,
                    ),
            )
        }

        is OngoingActivityChipModel.Active.ShortTimeDelta -> {
            val timeRemainingState =
                rememberTimeRemainingState(
                    futureTimeMillis = viewModel.time,
                    timeSource = viewModel.timeSource,
                )

            timeRemainingState.timeRemainingData?.let {
                val text = formatTimeRemainingData(it)
                Text(
                    text = text,
                    style = textStyle,
                    color = textColor,
                    softWrap = false,
                    modifier =
                        modifier.hideTextIfDoesNotFit(
                            text = text,
                            textStyle = textStyle,
                            textMeasurer = textMeasurer,
                            maxTextWidth = maxTextWidth,
                            startPadding = startPadding,
                            endPadding = endPadding,
                        ),
                )
            }
        }

        is OngoingActivityChipModel.Active.IconOnly -> {
            throw IllegalStateException("ChipContent should only be used if the chip shows text")
        }
    }
}

/** A modifier that ensures the width of the content only increases and never decreases. */
private fun Modifier.neverDecreaseWidth(density: Density): Modifier {
    return this.then(NeverDecreaseWidthElement(density))
}

private data class NeverDecreaseWidthElement(val density: Density) :
    ModifierNodeElement<NeverDecreaseWidthNode>() {
    override fun create(): NeverDecreaseWidthNode {
        return NeverDecreaseWidthNode()
    }

    override fun update(node: NeverDecreaseWidthNode) {
        node.onDensityUpdated()
    }
}

private class NeverDecreaseWidthNode : Modifier.Node(), LayoutModifierNode {
    private var minWidth = 0

    fun onDensityUpdated() {
        // When the font or display size changes, we should re-determine what our minWidth is from
        // scratch (e.g. if the font size decreased, we may be able to take *less* room).
        // See b/395607413.
        minWidth = 0
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(Constraints(minWidth = minWidth).constrain(constraints))
        val width = placeable.width
        val height = placeable.height

        minWidth = maxOf(minWidth, width)

        return layout(width, height) { placeable.place(0, 0) }
    }
}

/**
 * A custom layout modifier for text that ensures the text is only visible if it completely fits
 * within the constrained bounds. Imposes a provided [maxTextWidthPx]. Also, accounts for provided
 * padding values if provided and ensures its text is placed with the provided padding included
 * around it.
 */
private fun Modifier.hideTextIfDoesNotFit(
    text: String,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    maxTextWidth: Dp,
    startPadding: Dp = 0.dp,
    endPadding: Dp = 0.dp,
): Modifier {
    return this.then(
        HideTextIfDoesNotFitElement(
            text,
            textStyle,
            textMeasurer,
            maxTextWidth,
            startPadding,
            endPadding,
        )
    )
}

private data class HideTextIfDoesNotFitElement(
    val text: String,
    val textStyle: TextStyle,
    val textMeasurer: TextMeasurer,
    val maxTextWidth: Dp,
    val startPadding: Dp,
    val endPadding: Dp,
) : ModifierNodeElement<HideTextIfDoesNotFitNode>() {
    override fun create(): HideTextIfDoesNotFitNode {
        return HideTextIfDoesNotFitNode(
            text,
            textStyle,
            textMeasurer,
            maxTextWidth,
            startPadding,
            endPadding,
        )
    }

    override fun update(node: HideTextIfDoesNotFitNode) {
        node.text = text
        node.textStyle = textStyle
        node.textMeasurer = textMeasurer
        node.maxTextWidth = maxTextWidth
        node.startPadding = startPadding
        node.endPadding = endPadding
    }
}

private class HideTextIfDoesNotFitNode(
    var text: String,
    var textStyle: TextStyle,
    var textMeasurer: TextMeasurer,
    var maxTextWidth: Dp,
    var startPadding: Dp,
    var endPadding: Dp,
) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val horizontalPadding = startPadding + endPadding
        val maxWidth =
            min(maxTextWidth.roundToPx(), (constraints.maxWidth - horizontalPadding.roundToPx()))
                .coerceAtLeast(constraints.minWidth)
        val placeable = measurable.measure(constraints.copy(maxWidth = maxWidth))

        val intrinsicWidth = textMeasurer.measure(text, textStyle, softWrap = false).size.width
        return if (intrinsicWidth <= maxWidth) {
            val height = placeable.height
            val width = placeable.width
            layout(width + horizontalPadding.roundToPx(), height) {
                placeable.place(startPadding.roundToPx(), 0)
            }
        } else {
            layout(0, 0) {}
        }
    }
}
