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

package com.android.systemui.media.remedia.domain.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.media.remedia.shared.model.MediaCardActionButtonLayout
import com.android.systemui.media.remedia.shared.model.MediaColorScheme
import com.android.systemui.media.remedia.shared.model.MediaSessionState

/** Data model representing a media session. */
@Stable
interface MediaSessionModel {
    /** Unique identifier. */
    val key: Any

    val appName: String

    val appIcon: Icon

    val background: ImageBitmap?

    val colorScheme: MediaColorScheme

    val title: String

    val subtitle: String

    val onClick: () -> Unit

    /**
     * Whether the session is currently active. Under some UIs, only currently active session should
     * be shown.
     */
    val isActive: Boolean

    /** Whether the session can be hidden/dismissed by the user. */
    val canBeHidden: Boolean

    /**
     * Whether the session currently supports scrubbing (e.g. moving to a different position iin the
     * playback.
     */
    val canBeScrubbed: Boolean

    val state: MediaSessionState

    /** The position of the playback within the current track. */
    val positionMs: Long

    /** The total duration of the current track. */
    val durationMs: Long

    val outputDevice: MediaOutputDeviceModel

    /** How to lay out the action buttons. */
    val actionButtonLayout: MediaCardActionButtonLayout
    val playPauseAction: MediaActionModel
    val leftAction: MediaActionModel
    val rightAction: MediaActionModel
    val additionalActions: List<MediaActionModel.Action>
}
