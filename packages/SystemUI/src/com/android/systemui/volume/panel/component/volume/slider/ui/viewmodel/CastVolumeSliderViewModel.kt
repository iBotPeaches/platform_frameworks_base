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

package com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel

import android.content.Context
import android.media.session.MediaController.PlaybackInfo
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.Flags
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.UiBackground
import com.android.systemui.haptics.slider.SliderHapticFeedbackFilter
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.res.R
import com.android.systemui.volume.panel.component.mediaoutput.domain.interactor.MediaDeviceSessionInteractor
import com.android.systemui.volume.panel.component.mediaoutput.shared.model.MediaDeviceSession
import com.android.systemui.volume.panel.shared.VolumePanelLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class CastVolumeSliderViewModel
@AssistedInject
constructor(
    @Assisted private val session: MediaDeviceSession,
    @Assisted private val coroutineScope: CoroutineScope,
    @UiBackground private val uiBackgroundContext: CoroutineContext,
    private val context: Context,
    private val mediaDeviceSessionInteractor: MediaDeviceSessionInteractor,
    private val hapticsViewModelFactory: SliderHapticsViewModel.Factory,
    private val volumePanelLogger: VolumePanelLogger,
) : SliderViewModel {

    private val castLabel = context.getString(R.string.media_device_cast)
    private val castIcon =
        Icon.Loaded(
            drawable = context.getDrawable(R.drawable.ic_cast)!!,
            contentDescription = null,
            res = R.drawable.ic_cast,
        )
    override val slider: StateFlow<SliderState> =
        mediaDeviceSessionInteractor
            .playbackInfo(session)
            .mapNotNull {
                volumePanelLogger.onVolumeUpdateReceived(session.sessionToken, it.currentVolume)
                withContext(uiBackgroundContext) { it.getCurrentState() }
            }
            .stateIn(coroutineScope, SharingStarted.Eagerly, SliderState.Empty)

    override fun onValueChanged(state: SliderState, newValue: Float) {
        coroutineScope.launch {
            val volume = newValue.roundToInt()
            volumePanelLogger.onSetVolumeRequested(session.sessionToken, volume)
            mediaDeviceSessionInteractor.setSessionVolume(session, volume)
        }
    }

    override fun onValueChangeFinished() {}

    override fun toggleMuted(state: SliderState) {
        // do nothing because this action isn't supported for Cast sliders.
    }

    override fun getSliderHapticsViewModelFactory(): SliderHapticsViewModel.Factory? =
        if (Flags.hapticsForComposeSliders() && slider.value != SliderState.Empty) {
            hapticsViewModelFactory
        } else {
            null
        }

    private fun PlaybackInfo.getCurrentState(): State {
        val volumeRange = 0..maxVolume
        return State(
            value = currentVolume.toFloat(),
            valueRange = volumeRange.first.toFloat()..volumeRange.last.toFloat(),
            icon = castIcon,
            label = castLabel,
            isEnabled = true,
            step = 1f,
        )
    }

    private data class State(
        override val value: Float,
        override val valueRange: ClosedFloatingPointRange<Float>,
        override val icon: Icon.Loaded?,
        override val label: String,
        override val isEnabled: Boolean,
        override val step: Float,
    ) : SliderState {
        override val hapticFilter: SliderHapticFeedbackFilter
            get() = SliderHapticFeedbackFilter()

        override val disabledMessage: String?
            get() = null

        override val isMutable: Boolean
            get() = false

        override val a11yContentDescription: String
            get() = label

        override val a11yClickDescription: String?
            get() = null

        override val a11yStateDescription: String?
            get() = null
    }

    @AssistedFactory
    interface Factory {

        fun create(
            session: MediaDeviceSession,
            coroutineScope: CoroutineScope,
        ): CastVolumeSliderViewModel
    }
}
