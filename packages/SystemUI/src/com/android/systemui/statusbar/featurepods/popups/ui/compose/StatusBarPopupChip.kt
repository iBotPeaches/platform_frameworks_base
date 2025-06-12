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

package com.android.systemui.statusbar.featurepods.popups.ui.compose

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.compose.modifiers.thenIf
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.res.R
import com.android.systemui.statusbar.featurepods.popups.shared.model.HoverBehavior
import com.android.systemui.statusbar.featurepods.popups.shared.model.PopupChipModel

/**
 * A clickable chip that can show an anchored popup containing relevant system controls. The chip
 * can show an icon that can have its own separate action distinct from its parent chip. Moreover,
 * the chip can show text containing contextual information.
 */
@Composable
fun StatusBarPopupChip(viewModel: PopupChipModel.Shown, modifier: Modifier = Modifier) {
    val hasHoverBehavior = viewModel.hoverBehavior !is HoverBehavior.None
    val interactionSource = remember { MutableInteractionSource() }
    val hoveredState by interactionSource.collectIsHoveredAsState()
    val isHovered = hasHoverBehavior && hoveredState
    val isPopupShown = viewModel.isPopupShown
    val indication = if (hoveredState) null else LocalIndication.current
    val chipShape =
        RoundedCornerShape(dimensionResource(id = R.dimen.ongoing_activity_chip_corner_radius))
    val chipBackgroundColor =
        if (isPopupShown) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceDim
        }

    // Use a Box with `fillMaxHeight` to create a larger click surface for the chip. The visible
    // height of the chip is determined by the height of the background of the Row below. The
    // `indication` for Clicks is applied in the Row below as well.
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier.minimumInteractiveComponentSize().thenIf(!isPopupShown) {
                Modifier.clickable(
                    onClick = { viewModel.showPopup() },
                    indication = null,
                    interactionSource = interactionSource,
                )
            },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.height(dimensionResource(R.dimen.ongoing_appops_chip_height))
                    .clip(chipShape)
                    .background(chipBackgroundColor)
                    .border(
                        width = dimensionResource(id = R.dimen.ongoing_activity_chip_outline_width),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = chipShape,
                    )
                    .indication(interactionSource, indication)
                    .padding(start = 4.dp, end = 8.dp),
        ) {
            val iconColor =
                if (isHovered) chipBackgroundColor else contentColorFor(chipBackgroundColor)
            val hoverBehavior = viewModel.hoverBehavior
            val iconBackgroundColor = contentColorFor(chipBackgroundColor)
            val iconInteractionSource = remember { MutableInteractionSource() }
            Icon(
                icon =
                    when {
                        isHovered && hoverBehavior is HoverBehavior.Button -> hoverBehavior.icon
                        else -> viewModel.icon
                    },
                modifier =
                    Modifier.height(20.dp)
                        .width(20.dp)
                        .thenIf(isHovered) {
                            Modifier.background(color = iconBackgroundColor, shape = CircleShape)
                                .padding(2.dp)
                        }
                        .thenIf(hoverBehavior is HoverBehavior.Button) {
                            Modifier.clickable(
                                role = Role.Button,
                                onClick = (hoverBehavior as HoverBehavior.Button).onIconPressed,
                                indication = ripple(),
                                interactionSource = iconInteractionSource,
                                enabled = isHovered,
                            )
                        },
                tint = iconColor,
            )

            val text = viewModel.chipText
            val textStyle = MaterialTheme.typography.labelLarge
            val textMeasurer = rememberTextMeasurer()
            var textOverflow by remember { mutableStateOf(false) }

            Text(
                text = text,
                style = textStyle,
                softWrap = false,
                modifier =
                    Modifier.widthIn(
                            max =
                                dimensionResource(id = R.dimen.ongoing_activity_chip_max_text_width)
                        )
                        .layout { measurables, constraints ->
                            val placeable = measurables.measure(constraints)
                            val intrinsicWidth =
                                textMeasurer.measure(text, textStyle, softWrap = false).size.width
                            textOverflow = intrinsicWidth > constraints.maxWidth

                            layout(placeable.width, placeable.height) {
                                if (textOverflow) {
                                    placeable.placeWithLayer(0, 0) {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                } else {
                                    placeable.place(0, 0)
                                }
                            }
                        }
                        .overflowFadeOut(
                            hasOverflow = { textOverflow },
                            fadeLength =
                                dimensionResource(
                                    id = R.dimen.ongoing_activity_chip_text_fading_edge_length
                                ),
                        ),
            )
        }
    }
}

private fun Modifier.overflowFadeOut(hasOverflow: () -> Boolean, fadeLength: Dp): Modifier {
    return drawWithCache {
        val width = size.width
        val start = (width - fadeLength.toPx()).coerceAtLeast(0f)
        val gradient =
            Brush.horizontalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startX = start,
                endX = width,
            )
        onDrawWithContent {
            drawContent()
            if (hasOverflow()) drawRect(brush = gradient, blendMode = BlendMode.DstIn)
        }
    }
}
