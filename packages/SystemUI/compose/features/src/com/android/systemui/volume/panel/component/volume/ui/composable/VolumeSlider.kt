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

package com.android.systemui.volume.panel.component.volume.ui.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformSlider
import com.android.compose.PlatformSliderColors
import com.android.systemui.Flags
import com.android.systemui.common.shared.model.Icon as IconModel
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.haptics.slider.SliderHapticFeedbackFilter
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.volume.dialog.sliders.ui.compose.SliderTrack
import com.android.systemui.volume.haptics.ui.VolumeHapticsConfigsProvider
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.SliderState
import com.android.systemui.volume.ui.compose.slider.AccessibilityParams
import com.android.systemui.volume.ui.compose.slider.Haptics
import com.android.systemui.volume.ui.compose.slider.Slider
import com.android.systemui.volume.ui.compose.slider.SliderIcon
import kotlin.math.round
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VolumeSlider(
    state: SliderState,
    onValueChange: (newValue: Float) -> Unit,
    onIconTapped: () -> Unit,
    sliderColors: PlatformSliderColors,
    modifier: Modifier = Modifier,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory?,
    onValueChangeFinished: (() -> Unit)? = null,
    button: (@Composable RowScope.() -> Unit)? = null,
) {
    if (!Flags.volumeRedesign()) {
        LegacyVolumeSlider(
            state = state,
            onValueChange = onValueChange,
            onIconTapped = onIconTapped,
            sliderColors = sliderColors,
            onValueChangeFinished = onValueChangeFinished,
            modifier = modifier,
            hapticsViewModelFactory = hapticsViewModelFactory,
        )
        return
    }

    Column(modifier = modifier.animateContentSize()) {
        Text(
            text = state.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val materialSliderColors =
                SliderDefaults.colors(
                    activeTickColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledActiveTickColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            Slider(
                value = state.value,
                valueRange = state.valueRange,
                onValueChanged = onValueChange,
                onValueChangeFinished = { onValueChangeFinished?.invoke() },
                colors = materialSliderColors,
                isEnabled = state.isEnabled,
                stepDistance = state.step,
                accessibilityParams =
                    AccessibilityParams(
                        contentDescription = state.a11yContentDescription,
                        stateDescription = state.a11yStateDescription,
                    ),
                track = { sliderState ->
                    SliderTrack(
                        sliderState = sliderState,
                        colors = materialSliderColors,
                        isEnabled = state.isEnabled,
                        activeTrackStartIcon =
                            state.icon?.let { icon ->
                                { iconsState ->
                                    SliderIcon(
                                        icon = {
                                            Icon(icon = icon, modifier = Modifier.size(24.dp))
                                        },
                                        isVisible = iconsState.isActiveTrackStartIconVisible,
                                    )
                                }
                            },
                        inactiveTrackStartIcon =
                            state.icon?.let { icon ->
                                { iconsState ->
                                    SliderIcon(
                                        icon = {
                                            Icon(icon = icon, modifier = Modifier.size(24.dp))
                                        },
                                        isVisible = !iconsState.isActiveTrackStartIconVisible,
                                    )
                                }
                            },
                    )
                },
                thumb = { sliderState, interactionSource ->
                    SliderDefaults.Thumb(
                        sliderState = sliderState,
                        interactionSource = interactionSource,
                        enabled = state.isEnabled,
                        colors = materialSliderColors,
                        thumbSize = DpSize(4.dp, 52.dp),
                    )
                },
                haptics =
                    hapticsViewModelFactory?.let {
                        Haptics.Enabled(
                            hapticsViewModelFactory = it,
                            hapticFilter = state.hapticFilter,
                            orientation = Orientation.Horizontal,
                        )
                    } ?: Haptics.Disabled,
                modifier = Modifier.weight(1f).sysuiResTag(state.label),
            )
            button?.invoke(this)
        }
        state.disabledMessage?.let { disabledMessage ->
            AnimatedVisibility(visible = !state.isEnabled) {
                Row(
                    modifier = Modifier.padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MaterialIcon(
                        painter = painterResource(R.drawable.ic_error_outline),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = disabledMessage,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.basicMarquee().clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}

@Composable
private fun LegacyVolumeSlider(
    state: SliderState,
    onValueChange: (newValue: Float) -> Unit,
    onIconTapped: () -> Unit,
    sliderColors: PlatformSliderColors,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory?,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val value by valueState(state)
    val interactionSource = remember { MutableInteractionSource() }
    val hapticsViewModel: SliderHapticsViewModel? =
        setUpHapticsViewModel(
            value,
            state.valueRange,
            state.hapticFilter,
            interactionSource,
            hapticsViewModelFactory,
        )

    PlatformSlider(
        modifier =
            modifier.sysuiResTag(state.label).clearAndSetSemantics {
                if (state.isEnabled) {
                    contentDescription = state.label
                    state.a11yClickDescription?.let {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(it) {
                                    onIconTapped()
                                    true
                                }
                            )
                    }

                    state.a11yStateDescription?.let { stateDescription = it }
                    progressBarRangeInfo = ProgressBarRangeInfo(state.value, state.valueRange)
                } else {
                    disabled()
                    contentDescription =
                        state.disabledMessage?.let { "${state.label}, $it" } ?: state.label
                }
                setProgress { targetValue ->
                    val targetDirection =
                        when {
                            targetValue > value -> 1
                            targetValue < value -> -1
                            else -> 0
                        }

                    val newValue =
                        (value + targetDirection * state.step).coerceIn(
                            state.valueRange.start,
                            state.valueRange.endInclusive,
                        )
                    onValueChange(newValue)
                    true
                }
            },
        value = value,
        valueRange = state.valueRange,
        onValueChange = { newValue ->
            hapticsViewModel?.addVelocityDataPoint(newValue)
            onValueChange(newValue)
        },
        onValueChangeFinished = {
            hapticsViewModel?.onValueChangeEnded()
            onValueChangeFinished?.invoke()
        },
        enabled = state.isEnabled,
        icon = {
            state.icon?.let {
                LegacySliderIcon(
                    icon = it,
                    onIconTapped = onIconTapped,
                    isTappable = state.isMutable,
                )
            }
        },
        colors = sliderColors,
        label = { isDragging ->
            AnimatedVisibility(
                visible = !isDragging,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
            ) {
                VolumeSliderContent(
                    modifier = Modifier,
                    label = state.label,
                    isEnabled = state.isEnabled,
                    disabledMessage = state.disabledMessage,
                )
            }
        },
        interactionSource = interactionSource,
    )
}

@Composable
private fun valueState(state: SliderState): State<Float> {
    var prevState by remember { mutableStateOf(state) }
    // Don't animate slider value when receive the first value and when changing isEnabled state
    val shouldSkipAnimation =
        prevState is SliderState.Empty || prevState.isEnabled != state.isEnabled
    val value =
        if (shouldSkipAnimation) remember { mutableFloatStateOf(state.value) }
        else animateFloatAsState(targetValue = state.value, label = "VolumeSliderValueAnimation")
    prevState = state
    return value
}

@Composable
private fun LegacySliderIcon(
    icon: IconModel,
    onIconTapped: () -> Unit,
    isTappable: Boolean,
    modifier: Modifier = Modifier,
) {
    val boxModifier =
        if (isTappable) {
                modifier.clickable(
                    onClick = onIconTapped,
                    interactionSource = null,
                    indication = null,
                )
            } else {
                modifier
            }
            .fillMaxSize()
    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center,
        content = { Icon(modifier = Modifier.size(24.dp), icon = icon) },
    )
}

@Composable
private fun setUpHapticsViewModel(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    hapticFilter: SliderHapticFeedbackFilter,
    interactionSource: MutableInteractionSource,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory?,
): SliderHapticsViewModel? {
    return hapticsViewModelFactory?.let {
        rememberViewModel(traceName = "SliderHapticsViewModel") {
                it.create(
                    interactionSource,
                    valueRange,
                    Orientation.Horizontal,
                    VolumeHapticsConfigsProvider.sliderHapticFeedbackConfig(
                        valueRange,
                        hapticFilter,
                    ),
                    VolumeHapticsConfigsProvider.seekableSliderTrackerConfig,
                )
            }
            .also { hapticsViewModel ->
                var lastDiscreteStep by remember { mutableFloatStateOf(round(value)) }
                LaunchedEffect(value) {
                    snapshotFlow { value }
                        .map { round(it) }
                        .filter { it != lastDiscreteStep }
                        .distinctUntilChanged()
                        .collect { discreteStep ->
                            lastDiscreteStep = discreteStep
                            hapticsViewModel.onValueChange(discreteStep)
                        }
                }
            }
    }
}
