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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.volume.ui.compose.slider

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.VerticalSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import com.android.systemui.haptics.slider.SliderHapticFeedbackFilter
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.volume.haptics.ui.VolumeHapticsConfigsProvider
import kotlin.math.round
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
fun Slider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChanged: (Float) -> Unit,
    onValueChangeFinished: ((Float) -> Unit)?,
    isEnabled: Boolean,
    accessibilityParams: AccessibilityParams,
    modifier: Modifier = Modifier,
    stepDistance: Float = 0f,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    haptics: Haptics = Haptics.Disabled,
    isVertical: Boolean = false,
    isReverseDirection: Boolean = false,
    track: (@Composable (SliderState) -> Unit) = { SliderDefaults.Track(it) },
    thumb: (@Composable (SliderState, MutableInteractionSource) -> Unit) = { _, _ ->
        SliderDefaults.Thumb(
            interactionSource = interactionSource,
            colors = colors,
            enabled = isEnabled,
        )
    },
) {
    require(stepDistance >= 0) { "stepDistance must not be negative" }
    val snappedValue by valueState(value, isEnabled)
    val hapticsViewModel = haptics.createViewModel(snappedValue, valueRange, interactionSource)

    val sliderState =
        remember(valueRange) { SliderState(value = snappedValue, valueRange = valueRange) }
    val valueChange: (Float) -> Unit = { newValue ->
        hapticsViewModel?.onValueChange(newValue)
        onValueChanged(newValue)
    }
    val semantics =
        createSemantics(
            accessibilityParams,
            snappedValue,
            valueRange,
            valueChange,
            isEnabled,
            stepDistance,
        )

    sliderState.onValueChangeFinished = {
        hapticsViewModel?.onValueChangeEnded()
        onValueChangeFinished?.invoke(snappedValue)
    }
    sliderState.onValueChange = valueChange
    sliderState.value = snappedValue

    if (isVertical) {
        VerticalSlider(
            state = sliderState,
            enabled = isEnabled,
            reverseDirection = isReverseDirection,
            interactionSource = interactionSource,
            colors = colors,
            track = track,
            thumb = { thumb(it, interactionSource) },
            modifier = modifier.clearAndSetSemantics(semantics),
        )
    } else {
        Slider(
            state = sliderState,
            enabled = isEnabled,
            interactionSource = interactionSource,
            colors = colors,
            track = track,
            thumb = { thumb(it, interactionSource) },
            modifier = modifier.clearAndSetSemantics(semantics),
        )
    }
}

@Composable
private fun valueState(targetValue: Float, isEnabled: Boolean): State<Float> {
    var prevValue by remember { mutableFloatStateOf(targetValue) }
    var prevEnabled by remember { mutableStateOf(isEnabled) }
    // Don't animate slider value when receive the first value and when changing isEnabled state
    val value =
        if (prevEnabled != isEnabled) mutableFloatStateOf(targetValue)
        else
            animateFloatAsState(
                targetValue = targetValue,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                label = "VolumeSliderValueAnimation",
            )
    prevValue = targetValue
    prevEnabled = isEnabled
    return value
}

private fun createSemantics(
    params: AccessibilityParams,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChanged: (Float) -> Unit,
    isEnabled: Boolean,
    stepDistance: Float,
): SemanticsPropertyReceiver.() -> Unit {
    return {
        contentDescription = params.contentDescription
        if (isEnabled) {
            params.stateDescription?.let { stateDescription = it }
            progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
        } else {
            disabled()
        }
        setProgress { targetValue ->
            val targetDirection =
                when {
                    targetValue > value -> 1f
                    targetValue < value -> -1f
                    else -> 0f
                }
            val offset =
                if (stepDistance > 0) {
                    // advance to the next step when stepDistance is > 0
                    targetDirection * stepDistance
                } else {
                    // advance to the desired value otherwise
                    targetValue - value
                }

            val newValue = (value + offset).coerceIn(valueRange.start, valueRange.endInclusive)
            onValueChanged(newValue)
            true
        }
    }
}

@Composable
private fun Haptics.createViewModel(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    interactionSource: MutableInteractionSource,
): SliderHapticsViewModel? {
    return when (this) {
        is Haptics.Disabled -> null
        is Haptics.Enabled -> {
            hapticsViewModelFactory.let {
                rememberViewModel(traceName = "SliderHapticsViewModel") {
                        it.create(
                            interactionSource,
                            valueRange,
                            orientation,
                            VolumeHapticsConfigsProvider.sliderHapticFeedbackConfig(
                                valueRange,
                                hapticFilter,
                            ),
                            VolumeHapticsConfigsProvider.seekableSliderTrackerConfig,
                        )
                    }
                    .also { hapticsViewModel ->
                        var lastDiscreteStep by remember { mutableFloatStateOf(value) }
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
    }
}

data class AccessibilityParams(
    val contentDescription: String,
    val stateDescription: String? = null,
)

sealed interface Haptics {
    data object Disabled : Haptics

    data class Enabled(
        val hapticsViewModelFactory: SliderHapticsViewModel.Factory,
        val hapticFilter: SliderHapticFeedbackFilter,
        val orientation: Orientation,
    ) : Haptics
}
