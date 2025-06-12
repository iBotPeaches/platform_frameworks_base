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

package com.android.systemui.notifications.ui.composable.row

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxOfOrDefault
import androidx.compose.ui.util.fastSumBy
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.theme.PlatformTheme
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.statusbar.notification.row.ui.viewmodel.BundleHeaderViewModel

object BundleHeader {
    object Scenes {
        val Collapsed = SceneKey("Collapsed")
        val Expanded = SceneKey("Expanded")
    }

    object Elements {
        val PreviewIcon1 = ElementKey("PreviewIcon1")
        val PreviewIcon2 = ElementKey("PreviewIcon2")
        val PreviewIcon3 = ElementKey("PreviewIcon3")
        val TitleText = ElementKey("TitleText")
    }
}

fun createComposeView(viewModel: BundleHeaderViewModel, context: Context): ComposeView {
    // TODO(b/399588047): Check if we can init PlatformTheme once instead of once per ComposeView
    return ComposeView(context).apply { setContent { PlatformTheme { BundleHeader(viewModel) } } }
}

@Composable
fun BundleHeader(viewModel: BundleHeaderViewModel, modifier: Modifier = Modifier) {
    Box(modifier) {
        Background(background = viewModel.backgroundDrawable, modifier = Modifier.matchParentSize())
        val scope = rememberCoroutineScope()
        SceneTransitionLayout(
            state = viewModel.state,
            modifier =
                Modifier.clickable(
                    onClick = { viewModel.onHeaderClicked(scope) },
                    interactionSource = null,
                    indication = null,
                ),
        ) {
            scene(BundleHeader.Scenes.Collapsed) {
                BundleHeaderContent(viewModel, collapsed = true)
            }
            scene(BundleHeader.Scenes.Expanded) {
                BundleHeaderContent(viewModel, collapsed = false)
            }
        }
    }
}

@Composable
private fun Background(background: Drawable?, modifier: Modifier = Modifier) {
    if (background != null) {
        val painter = rememberDrawablePainter(drawable = background)
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContentScope.BundleHeaderContent(
    viewModel: BundleHeaderViewModel,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 16.dp),
    ) {
        BundleIcon(viewModel.bundleIcon, modifier = Modifier.padding(horizontal = 16.dp))
        Text(
            text = viewModel.titleText,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier.element(BundleHeader.Elements.TitleText).weight(1f),
        )

        if (collapsed && viewModel.previewIcons.isNotEmpty()) {
            BundlePreviewIcons(
                previewDrawables = viewModel.previewIcons,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        ExpansionControl(
            collapsed = collapsed,
            hasUnread = viewModel.hasUnreadMessages,
            numberToShow = viewModel.numberOfChildren,
            modifier = Modifier.padding(start = 8.dp, end = 16.dp),
        )
    }
}

@Composable
private fun ContentScope.BundlePreviewIcons(
    previewDrawables: List<Drawable>,
    modifier: Modifier = Modifier,
) {
    check(previewDrawables.isNotEmpty())
    val iconSize = 32.dp
    HalfOverlappingReversedRow(modifier = modifier) {
        PreviewIcon(
            drawable = previewDrawables[0],
            modifier = Modifier.element(BundleHeader.Elements.PreviewIcon1).size(iconSize),
        )
        if (previewDrawables.size < 2) return@HalfOverlappingReversedRow
        PreviewIcon(
            drawable = previewDrawables[1],
            modifier = Modifier.element(BundleHeader.Elements.PreviewIcon2).size(iconSize),
        )
        if (previewDrawables.size < 3) return@HalfOverlappingReversedRow
        PreviewIcon(
            drawable = previewDrawables[2],
            modifier = Modifier.element(BundleHeader.Elements.PreviewIcon3).size(iconSize),
        )
    }
}

@Composable
private fun HalfOverlappingReversedRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val placeables = measurables.fastMap { measurable -> measurable.measure(constraints) }

        if (placeables.isEmpty())
            return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        val width = placeables.fastSumBy { it.width / 2 } + placeables.first().width / 2
        val childHeight = placeables.fastMaxOfOrDefault(0) { it.height }

        layout(constraints.constrainWidth(width), constraints.constrainHeight(childHeight)) {
            // Start in the middle of the right-most placeable
            var currentXPosition = placeables.fastSumBy { it.width / 2 }
            placeables.fastForEach { placeable ->
                currentXPosition -= placeable.width / 2
                placeable.placeRelative(x = currentXPosition, y = 0)
            }
        }
    }
}
