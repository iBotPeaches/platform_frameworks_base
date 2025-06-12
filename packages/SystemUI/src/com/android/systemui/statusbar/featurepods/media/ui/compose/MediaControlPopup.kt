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

package com.android.systemui.statusbar.featurepods.media.ui.compose

import android.widget.FrameLayout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.res.R

/** Displays a popup containing media controls. Embeds the MediaCarousel within a Compose popup. */
@Composable
fun MediaControlPopup(mediaHost: MediaHost, modifier: Modifier = Modifier) {
    AndroidView(
        modifier =
            modifier
                .width(400.dp)
                .height(200.dp)
                .clip(
                    shape =
                        RoundedCornerShape(dimensionResource(R.dimen.notification_corner_radius))
                ),
        factory = { _ ->
            mediaHost.hostView.apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
            }
            mediaHost.hostView
        },
        onReset = {},
    )
}
