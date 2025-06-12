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

package com.android.systemui.statusbar.featurepods.media.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.media.controls.data.repository.MediaFilterRepository
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.res.R
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.statusbar.featurepods.media.shared.model.MediaControlChipModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Interactor for managing the state of the media control chip in the status bar.
 *
 * Provides a [StateFlow] of [MediaControlChipModel] representing the current state of the media
 * control chip. Emits a new [MediaControlChipModel] when there is an active media session and the
 * corresponding user preference is found, otherwise emits null.
 *
 * This functionality is only enabled on large screen devices.
 */
@SysUISingleton
class MediaControlChipInteractor
@Inject
constructor(
    @Background private val backgroundScope: CoroutineScope,
    mediaFilterRepository: MediaFilterRepository,
) {
    private val isEnabled = MutableStateFlow(false)

    private val mediaControlChipModelForScene: Flow<MediaControlChipModel?> =
        combine(mediaFilterRepository.currentMedia, mediaFilterRepository.selectedUserEntries) {
            mediaList,
            userEntries ->
            mediaList
                .mapNotNull { userEntries[it.mediaLoadedModel.instanceId] }
                .firstOrNull { it.active }
                ?.toMediaControlChipModel()
        }

    /**
     * A flow of [MediaControlChipModel] representing the current state of the media controls chip.
     * This flow emits null when no active media is playing or when playback information is
     * unavailable. This flow is only active when [SceneContainerFlag] is disabled.
     */
    private val mediaControlChipModelLegacy = MutableStateFlow<MediaControlChipModel?>(null)

    fun updateMediaControlChipModelLegacy(mediaData: MediaData?) {
        if (!SceneContainerFlag.isEnabled) {
            mediaControlChipModelLegacy.value = mediaData?.toMediaControlChipModel()
        }
    }

    private val _mediaControlChipModel: Flow<MediaControlChipModel?> =
        if (SceneContainerFlag.isEnabled) {
            mediaControlChipModelForScene
        } else {
            mediaControlChipModelLegacy
        }

    /** The currently active [MediaControlChipModel] */
    val mediaControlChipModel: StateFlow<MediaControlChipModel?> =
        combine(_mediaControlChipModel, isEnabled) { mediaControlChipModel, isEnabled ->
                if (isEnabled) {
                    mediaControlChipModel
                } else {
                    null
                }
            }
            .stateIn(backgroundScope, SharingStarted.WhileSubscribed(), null)

    /**
     * The media control chip may not be enabled on all form factors, so only the relevant form
     * factors should initialize the interactor. This must be called from a CoreStartable.
     */
    fun initialize() {
        isEnabled.value = true
    }
}

private fun MediaData.toMediaControlChipModel(): MediaControlChipModel {
    return MediaControlChipModel(
        appIcon = this.appIcon,
        appName = this.app,
        songName = this.song,
        playOrPause = this.semanticActions?.getActionById(R.id.actionPlayPause),
    )
}
