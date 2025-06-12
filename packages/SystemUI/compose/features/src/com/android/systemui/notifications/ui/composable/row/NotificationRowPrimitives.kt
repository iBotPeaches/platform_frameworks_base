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

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.LowestZIndexContentPicker
import com.android.compose.animation.scene.ValueKey
import com.android.compose.animation.scene.animateElementColorAsState
import com.android.compose.animation.scene.animateElementFloatAsState
import com.android.compose.ui.graphics.painter.rememberDrawablePainter

object NotificationRowPrimitives {
    object Elements {
        val PillBackground = ElementKey("PillBackground", contentPicker = LowestZIndexContentPicker)
        val NotificationIconBackground = ElementKey("NotificationIconBackground")
        val Chevron = ElementKey("Chevron")
    }

    object Values {
        val ChevronRotation = ValueKey("NotificationChevronRotation")
        val PillBackgroundColor = ValueKey("PillBackgroundColor")
    }
}

/** The Icon displayed at the start of any notification row. */
@Composable
fun ContentScope.BundleIcon(drawable: Drawable?, modifier: Modifier = Modifier) {
    val surfaceColor = notificationElementSurfaceColor()
    Box(
        modifier =
            modifier
                // Has to be a shared element because we may have semi-transparent background color
                .element(NotificationRowPrimitives.Elements.NotificationIconBackground)
                .size(40.dp)
                .background(color = surfaceColor, shape = CircleShape)
    ) {
        if (drawable == null) return@Box
        val painter = rememberDrawablePainter(drawable)
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.padding(10.dp).fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        )
    }
}

/** The Icon used to display a preview of contained child notifications in a Bundle. */
@Composable
fun PreviewIcon(drawable: Drawable, modifier: Modifier = Modifier) {
    val surfaceColor = notificationElementSurfaceColor()
    Box(
        modifier =
            modifier
                .background(color = surfaceColor, shape = CircleShape)
                .border(0.5.dp, surfaceColor, CircleShape)
    ) {
        val painter = rememberDrawablePainter(drawable)
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
            contentScale = ContentScale.Fit,
        )
    }
}

/** The ExpansionControl of any expandable notification row, containing a Chevron. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContentScope.ExpansionControl(
    collapsed: Boolean,
    hasUnread: Boolean,
    numberToShow: Int?,
    modifier: Modifier = Modifier,
) {
    val textColor =
        if (hasUnread) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface
    Box(modifier = modifier) {
        // The background is a shared Element and therefore can't be the parent of a different
        // shared Element (the chevron), otherwise the child can't be animated.
        PillBackground(hasUnread, modifier = Modifier.matchParentSize())
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 2.dp, horizontal = 6.dp),
        ) {
            val iconSizeDp = with(LocalDensity.current) { 16.sp.toDp() }

            if (numberToShow != null) {
                Text(
                    text = numberToShow.toString(),
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    color = textColor,
                    modifier = Modifier.padding(end = 2.dp),
                )
            }
            Chevron(collapsed = collapsed, modifier = Modifier.size(iconSizeDp), color = textColor)
        }
    }
}

@Composable
private fun ContentScope.PillBackground(hasUnread: Boolean, modifier: Modifier = Modifier) {
    ElementWithValues(NotificationRowPrimitives.Elements.PillBackground, modifier) {
        val bgColorNoUnread = notificationElementSurfaceColor()
        val surfaceColor by
            animateElementColorAsState(
                if (hasUnread) MaterialTheme.colorScheme.tertiary else bgColorNoUnread,
                NotificationRowPrimitives.Values.PillBackgroundColor,
            )
        content {
            Box(
                modifier =
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = surfaceColor,
                            cornerRadius = CornerRadius(100.dp.toPx(), 100.dp.toPx()),
                        )
                    }
            )
        }
    }
}

@Composable
@ReadOnlyComposable
private fun notificationElementSurfaceColor(): Color {
    return if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
}

@Composable
private fun ContentScope.Chevron(collapsed: Boolean, color: Color, modifier: Modifier = Modifier) {
    val key = NotificationRowPrimitives.Elements.Chevron
    ElementWithValues(key, modifier) {
        val rotation by
            animateElementFloatAsState(
                if (collapsed) 0f else 180f,
                NotificationRowPrimitives.Values.ChevronRotation,
            )
        content {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
                tint = color,
            )
        }
    }
}
