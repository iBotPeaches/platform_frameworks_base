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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.statusbar.featurepods.popups.shared.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.shared.model.PopupChipModel

/** Container view that holds all right hand side chips in the status bar. */
@Composable
fun StatusBarPopupChipsContainer(
    chips: List<PopupChipModel.Shown>,
    mediaHost: MediaHost,
    onMediaControlPopupVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!SceneContainerFlag.isEnabled) {
        val isMediaControlPopupShown =
            remember(chips) {
                chips.any { it.chipId == PopupChipId.MediaControl && it.isPopupShown }
            }

        LaunchedEffect(isMediaControlPopupShown) {
            onMediaControlPopupVisibilityChanged(isMediaControlPopupShown)
        }
    }

    //    TODO(b/385353140): Add padding and spacing for this container according to UX specs.
    Box {
        Row(
            modifier = modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chips.forEach { chip ->
                StatusBarPopupChip(chip)
                if (chip.isPopupShown) {
                    StatusBarPopup(viewModel = chip, mediaHost = mediaHost)
                }
            }
        }
    }
}
