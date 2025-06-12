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

package com.android.systemui.media.remedia.ui.viewmodel

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.media.remedia.shared.model.MediaCardActionButtonLayout
import com.android.systemui.media.remedia.shared.model.MediaColorScheme

/** Models UI state for a media card. */
@Stable
interface MediaCardViewModel {
    /**
     * Identifier. Must be unique across all media cards currently shown, to help the horizontal
     * pager in the UI.
     */
    val key: Any

    val icon: Icon

    val background: ImageBitmap?

    val colorScheme: MediaColorScheme

    val title: String

    val subtitle: String

    val actionButtonLayout: MediaCardActionButtonLayout

    val playPauseAction: MediaPlayPauseActionViewModel?

    val navigation: MediaNavigationViewModel

    val additionalActions: List<MediaSecondaryActionViewModel>

    val guts: MediaCardGutsViewModel

    val outputSwitcherChips: List<MediaOutputSwitcherChipViewModel>

    /** Simple icon-only version of the output switcher for use in compact UIs. */
    val outputSwitcherChipButton: MediaSecondaryActionViewModel.Action

    val onClick: () -> Unit

    /** Accessibility string for the click action of the card. */
    val onClickLabel: String?

    val onLongClick: () -> Unit
}
