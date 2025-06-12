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
import com.android.internal.logging.UiEventLogger
import com.android.systemui.Flags
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.UiBackground
import com.android.systemui.haptics.slider.SliderHapticFeedbackFilter
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.res.R
import com.android.systemui.volume.domain.interactor.AudioSharingInteractor
import com.android.systemui.volume.panel.shared.VolumePanelLogger
import com.android.systemui.volume.panel.ui.VolumePanelUiEvent
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class AudioSharingStreamSliderViewModel
@AssistedInject
constructor(
    private val context: Context,
    @Assisted private val coroutineScope: CoroutineScope,
    @UiBackground private val uiBackgroundContext: CoroutineContext,
    private val audioSharingInteractor: AudioSharingInteractor,
    private val uiEventLogger: UiEventLogger,
    private val hapticsViewModelFactory: SliderHapticsViewModel.Factory,
    private val volumePanelLogger: VolumePanelLogger,
) : SliderViewModel {
    private val volumeChanges = MutableStateFlow<Int?>(null)

    private val audioSharingIcon =
        Icon.Loaded(
            drawable = context.getDrawable(R.drawable.ic_volume_media_bt)!!,
            contentDescription = null,
            res = R.drawable.ic_volume_media_bt,
        )
    override val slider: StateFlow<SliderState> =
        combine(
                audioSharingInteractor.volume.distinctUntilChanged().onEach {
                    it?.let(volumePanelLogger::onAudioSharingVolumeUpdateReceived)
                },
                audioSharingInteractor.secondaryDevice,
            ) { volume, device ->
                val deviceName = device?.name ?: return@combine SliderState.Empty
                if (volume == null) {
                    SliderState.Empty
                } else {
                    withContext(uiBackgroundContext) {
                        State(
                            value = volume.toFloat(),
                            valueRange =
                                audioSharingInteractor.volumeMin.toFloat()..audioSharingInteractor
                                        .volumeMax
                                        .toFloat(),
                            icon = audioSharingIcon,
                            label = deviceName,
                        )
                    }
                }
            }
            .stateIn(coroutineScope, SharingStarted.Eagerly, SliderState.Empty)

    init {
        volumeChanges
            .filterNotNull()
            .onEach {
                volumePanelLogger.onSetAudioSharingVolumeRequested(it)
                audioSharingInteractor.setStreamVolume(it)
            }
            .launchIn(coroutineScope)
    }

    override fun onValueChanged(state: SliderState, newValue: Float) {
        if (state !is State) return
        volumeChanges.tryEmit(newValue.roundToInt())
    }

    override fun onValueChangeFinished() {
        uiEventLogger.log(VolumePanelUiEvent.VOLUME_PANEL_AUDIO_SHARING_SLIDER_TOUCHED)
    }

    override fun toggleMuted(state: SliderState) {}

    override fun getSliderHapticsViewModelFactory(): SliderHapticsViewModel.Factory? =
        if (Flags.hapticsForComposeSliders() && slider.value != SliderState.Empty) {
            hapticsViewModelFactory
        } else {
            null
        }

    private data class State(
        override val value: Float,
        override val valueRange: ClosedFloatingPointRange<Float>,
        override val icon: Icon.Loaded?,
        override val label: String,
    ) : SliderState {
        override val hapticFilter: SliderHapticFeedbackFilter
            get() = SliderHapticFeedbackFilter()

        override val isEnabled: Boolean
            get() = true

        override val step: Float
            get() = 1f

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
        fun create(coroutineScope: CoroutineScope): AudioSharingStreamSliderViewModel
    }
}
