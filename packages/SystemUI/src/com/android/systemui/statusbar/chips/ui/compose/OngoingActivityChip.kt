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

package com.android.systemui.statusbar.chips.ui.compose

import android.annotation.IdRes
import android.content.res.ColorStateList
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.animation.Expandable
import com.android.compose.modifiers.thenIf
import com.android.systemui.animation.Expandable
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.common.ui.compose.load
import com.android.systemui.res.R
import com.android.systemui.statusbar.StatusBarIconView
import com.android.systemui.statusbar.chips.StatusBarChipsReturnAnimations
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.NotificationIconContainerViewBinder

@Composable
fun OngoingActivityChip(
    model: OngoingActivityChipModel.Active,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
    modifier: Modifier = Modifier,
) {
    val contentDescription =
        when (val icon = model.icon) {
            is OngoingActivityChipModel.ChipIcon.StatusBarView -> icon.contentDescription.load()
            is OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon ->
                icon.contentDescription.load()
            is OngoingActivityChipModel.ChipIcon.SingleColorIcon,
            null -> null
        }

    val borderStroke =
        model.colors.outline(LocalContext.current)?.let {
            BorderStroke(dimensionResource(R.dimen.ongoing_activity_chip_outline_width), Color(it))
        }

    val onClick =
        when (val clickBehavior = model.clickBehavior) {
            is OngoingActivityChipModel.ClickBehavior.ExpandAction -> { expandable: Expandable ->
                    clickBehavior.onClick(expandable)
                }
            is OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification -> { _ ->
                    clickBehavior.onClick()
                }
            is OngoingActivityChipModel.ClickBehavior.None -> null
        }
    val isClickable = onClick != null

    val chipSidePaddingTotal = 20.dp
    val minWidth =
        if (isClickable) {
            dimensionResource(id = R.dimen.min_clickable_item_size)
        } else if (model.icon != null) {
            dimensionResource(id = R.dimen.ongoing_activity_chip_icon_size) + chipSidePaddingTotal
        } else {
            dimensionResource(id = R.dimen.ongoing_activity_chip_min_text_width) +
                chipSidePaddingTotal
        }

    Expandable(
        color = Color(model.colors.background(LocalContext.current).defaultColor),
        shape =
            RoundedCornerShape(dimensionResource(id = R.dimen.ongoing_activity_chip_corner_radius)),
        modifier =
            modifier
                .height(dimensionResource(R.dimen.ongoing_appops_chip_height))
                .semantics {
                    if (contentDescription != null) {
                        this.contentDescription = contentDescription
                    }
                }
                .widthIn(min = minWidth)
                // For non-privacy-related chips, only show the chip if there's enough space for at
                // least the minimum width.
                .thenIf(!model.isImportantForPrivacy) {
                    Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            if (constraints.maxWidth >= minWidth.roundToPx()) {
                                placeable.place(0, 0)
                            }
                        }
                    }
                }
                .graphicsLayer(
                    alpha =
                        if (model.transitionManager?.hideChipForTransition == true) {
                            0f
                        } else {
                            1f
                        }
                ),
        borderStroke = borderStroke,
        onClick = onClick,
        useModifierBasedImplementation = StatusBarChipsReturnAnimations.isEnabled,
        // Some chips like the 3-2-1 countdown chip should be very small, smaller than a
        // reasonable minimum size.
        defaultMinSize = false,
        transitionControllerFactory = model.transitionManager?.controllerFactory,
    ) {
        ChipBody(model, iconViewStore, minWidth = minWidth)
    }
}

@Composable
private fun ChipBody(
    model: OngoingActivityChipModel.Active,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
    minWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxHeight()
                // Set the minWidth here as well as on the Expandable so that the content within
                // this row is still centered correctly horizontally
                .widthIn(min = minWidth)
                .padding(
                    // Always keep start & end padding the same so that if the text has to hide for
                    // some reason, the content is still centered
                    horizontal =
                        if (model.icon?.hasEmbeddedPadding == true) {
                            dimensionResource(
                                R.dimen.ongoing_activity_chip_side_padding_for_embedded_padding_icon
                            )
                        } else {
                            6.dp
                        }
                ),
    ) {
        model.icon?.let {
            ChipIcon(viewModel = it, iconViewStore = iconViewStore, colors = model.colors)
        }

        val isIconOnly = model is OngoingActivityChipModel.Active.IconOnly
        if (!isIconOnly) {
            ChipContent(viewModel = model)
        }
    }
}

@Composable
private fun ChipIcon(
    viewModel: OngoingActivityChipModel.ChipIcon,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
    colors: ColorsModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    when (viewModel) {
        is OngoingActivityChipModel.ChipIcon.StatusBarView -> {
            StatusBarConnectedDisplays.assertInLegacyMode()
            StatusBarIcon(colors, viewModel.impl.notification?.key, modifier) { viewModel.impl }
        }
        is OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon -> {
            StatusBarConnectedDisplays.unsafeAssertInNewMode()
            check(iconViewStore != null)

            StatusBarIcon(colors, viewModel.notificationKey, modifier) {
                iconViewStore.iconView(viewModel.notificationKey)
            }
        }

        is OngoingActivityChipModel.ChipIcon.SingleColorIcon -> {
            Icon(
                icon = viewModel.impl,
                tint = Color(colors.text(context)),
                modifier =
                    modifier.size(dimensionResource(id = R.dimen.ongoing_activity_chip_icon_size)),
            )
        }
    }
}

/** A Compose wrapper around [StatusBarIconView]. */
@Composable
private fun StatusBarIcon(
    colors: ColorsModel,
    notificationKey: String?,
    modifier: Modifier = Modifier,
    iconFactory: () -> StatusBarIconView?,
) {
    val context = LocalContext.current
    val colorTintList = ColorStateList.valueOf(colors.text(context))

    val iconSizePx =
        context.resources.getDimensionPixelSize(
            R.dimen.ongoing_activity_chip_embedded_padding_icon_size
        )
    AndroidView(
        modifier = modifier,
        factory = { _ ->
            // Use a wrapper frame layout so that we still return a view even if the icon is null
            val wrapperFrameLayout = FrameLayout(context)

            val icon = iconFactory.invoke()
            if (icon == null) {
                Log.e(TAG, "Missing StatusBarIconView for $notificationKey")
            } else {
                icon.apply {
                    id = CUSTOM_ICON_VIEW_ID
                    layoutParams = ViewGroup.LayoutParams(iconSizePx, iconSizePx)
                }
                // If needed, remove the icon from its old parent (views can only be attached
                // to 1 parent at a time)
                (icon.parent as? ViewGroup)?.apply {
                    this.removeView(icon)
                    this.removeTransientView(icon)
                }
                wrapperFrameLayout.addView(icon)
            }

            wrapperFrameLayout
        },
        update = { frameLayout ->
            frameLayout.findViewById<StatusBarIconView>(CUSTOM_ICON_VIEW_ID)?.apply {
                this.imageTintList = colorTintList
            }
        },
    )
}

private const val TAG = "OngoingActivityChip"
@IdRes private val CUSTOM_ICON_VIEW_ID = R.id.ongoing_activity_chip_custom_icon
