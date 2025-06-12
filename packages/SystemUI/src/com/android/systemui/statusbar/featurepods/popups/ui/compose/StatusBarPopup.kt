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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.res.R
import com.android.systemui.statusbar.featurepods.media.ui.compose.MediaControlPopup
import com.android.systemui.statusbar.featurepods.popups.shared.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.shared.model.PopupChipModel

/**
 * Displays a popup in the status bar area. The offset is calculated to draw the popup below the
 * status bar.
 */
@Composable
fun StatusBarPopup(viewModel: PopupChipModel.Shown, mediaHost: MediaHost) {
    val density = Density(LocalContext.current)
    Popup(
        properties =
            PopupProperties(
                focusable = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        offset =
            IntOffset(
                x = 0,
                y = with(density) { dimensionResource(R.dimen.status_bar_height).roundToPx() },
            ),
        onDismissRequest = { viewModel.hidePopup() },
    ) {
        Box(modifier = Modifier.padding(8.dp).wrapContentSize()) {
            when (viewModel.chipId) {
                is PopupChipId.MediaControl -> {
                    MediaControlPopup(mediaHost = mediaHost)
                }
            }
            // Future popup types will be handled here.
        }
    }
}
