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

package com.android.systemui.statusbar.featurepods.media.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.statusbar.featurepods.media.domain.interactor.MediaControlChipInteractor
import com.android.systemui.statusbar.featurepods.media.shared.model.MediaControlChipModel
import com.android.systemui.statusbar.featurepods.popups.shared.model.HoverBehavior
import com.android.systemui.statusbar.featurepods.popups.shared.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.shared.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.popups.ui.viewmodel.StatusBarPopupChipViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/**
 * [StatusBarPopupChipViewModel] for a media control chip in the status bar. This view model is
 * responsible for converting the [MediaControlChipModel] to a [PopupChipModel] that can be used to
 * display a media control chip.
 */
class MediaControlChipViewModel
@AssistedInject
constructor(
    @Application private val applicationContext: Context,
    mediaControlChipInteractor: MediaControlChipInteractor,
) : StatusBarPopupChipViewModel, ExclusiveActivatable() {
    private val hydrator: Hydrator = Hydrator("MediaControlChipViewModel.hydrator")
    /**
     * A snapshot [State] of the current [PopupChipModel]. This emits a new [PopupChipModel]
     * whenever the underlying [MediaControlChipModel] changes.
     */
    override val chip: PopupChipModel by
        hydrator.hydratedStateOf(
            traceName = "chip",
            initialValue = PopupChipModel.Hidden(PopupChipId.MediaControl),
            source =
                mediaControlChipInteractor.mediaControlChipModel.map { model ->
                    toPopupChipModel(model)
                },
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun toPopupChipModel(model: MediaControlChipModel?): PopupChipModel {
        if (model == null || model.songName.isNullOrEmpty()) {
            return PopupChipModel.Hidden(PopupChipId.MediaControl)
        }

        val contentDescription = model.appName?.let { ContentDescription.Loaded(description = it) }

        val defaultIcon =
            model.appIcon?.loadDrawable(applicationContext)?.let {
                Icon.Loaded(drawable = it, contentDescription = contentDescription)
            }
                ?: Icon.Resource(
                    res = com.android.internal.R.drawable.ic_audio_media,
                    contentDescription = contentDescription,
                )
        return PopupChipModel.Shown(
            chipId = PopupChipId.MediaControl,
            icon = defaultIcon,
            chipText = model.songName.toString(),
            hoverBehavior = createHoverBehavior(model),
        )
    }

    private fun createHoverBehavior(model: MediaControlChipModel): HoverBehavior {
        val playOrPause = model.playOrPause ?: return HoverBehavior.None
        val icon = playOrPause.icon ?: return HoverBehavior.None
        val action = playOrPause.action ?: return HoverBehavior.None

        val contentDescription =
            ContentDescription.Loaded(description = playOrPause.contentDescription.toString())

        return HoverBehavior.Button(
            icon = Icon.Loaded(drawable = icon, contentDescription = contentDescription),
            onIconPressed = { action.run() },
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(): MediaControlChipViewModel
    }
}
