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

package com.android.systemui.communal.posturing.domain.interactor

import android.annotation.SuppressLint
import android.hardware.Sensor
import com.android.systemui.communal.posturing.data.model.PositionState
import com.android.systemui.communal.posturing.data.repository.PosturingRepository
import com.android.systemui.communal.posturing.shared.model.PosturedState
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.log.dagger.CommunalLog
import com.android.systemui.util.kotlin.BooleanFlowOperators.allOf
import com.android.systemui.util.kotlin.observeTriggerSensor
import com.android.systemui.util.kotlin.slidingWindow
import com.android.systemui.util.sensors.AsyncSensorManager
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class PosturingInteractor
@Inject
constructor(
    repository: PosturingRepository,
    private val asyncSensorManager: AsyncSensorManager,
    @Application private val applicationScope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
    @CommunalLog private val logBuffer: LogBuffer,
    clock: SystemClock,
) {
    private val logger = Logger(logBuffer, TAG)

    private val debugPostured = MutableStateFlow<PosturedState>(PosturedState.Unknown)

    fun setValueForDebug(value: PosturedState) {
        debugPostured.value = value
    }

    /**
     * Detects whether or not the device is stationary, applying a sliding window smoothing
     * algorithm.
     */
    private val stationarySmoothed: Flow<Boolean> =
        merge(
                observeTriggerSensor(Sensor.TYPE_PICK_UP_GESTURE)
                    // If pickup detected, avoid triggering posturing at all within the sliding
                    // window by emitting a negative infinity value.
                    .map { Float.NEGATIVE_INFINITY }
                    .onEach { logger.i("pickup gesture detected") },
                observeTriggerSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
                    // If motion detected, avoid triggering posturing at all within the sliding
                    // window by emitting a negative infinity value.
                    .map { Float.NEGATIVE_INFINITY }
                    .onEach { logger.i("significant motion detected") },
                repository.positionState
                    .map { it.stationary }
                    .filterNot { it is PositionState.StationaryState.Unknown }
                    .map { stationaryState ->
                        if (stationaryState is PositionState.StationaryState.Stationary) {
                            stationaryState.confidence
                        } else {
                            // If not stationary, then we should effectively disable posturing by
                            // emitting the lowest possible confidence.
                            Float.NEGATIVE_INFINITY
                        }
                    },
            )
            .slidingWindow(SLIDING_WINDOW_DURATION, clock)
            .filterNot { it.isEmpty() }
            .map { window ->
                val avgStationaryConfidence = window.average()
                logger.i({ "stationary confidence: $double1 | window: $str1" }) {
                    str1 = window.formatWindowForDebugging()
                    double1 = avgStationaryConfidence
                }
                avgStationaryConfidence > CONFIDENCE_THRESHOLD
            }

    /**
     * Detects whether or not the device is in an upright orientation, applying a sliding window
     * smoothing algorithm.
     */
    private val orientationSmoothed: Flow<Boolean> =
        repository.positionState
            .map { it.orientation }
            .filterNot { it is PositionState.OrientationState.Unknown }
            .map { orientationState ->
                if (orientationState is PositionState.OrientationState.Postured) {
                    orientationState.confidence
                } else {
                    // If not postured, then we should effectively disable posturing by
                    // emitting the lowest possible confidence.
                    Float.NEGATIVE_INFINITY
                }
            }
            .slidingWindow(SLIDING_WINDOW_DURATION, clock)
            .filterNot { it.isEmpty() }
            .map { window ->
                val avgOrientationConfidence = window.average()
                logger.i({ "orientation confidence: $double1 | window: $str1" }) {
                    str1 = window.formatWindowForDebugging()
                    double1 = avgOrientationConfidence
                }
                avgOrientationConfidence > CONFIDENCE_THRESHOLD
            }

    /**
     * Posturing is composed of the device being stationary and in the correct orientation. If both
     * conditions are met, then consider it postured.
     */
    private val posturedSmoothed: Flow<PosturedState> =
        allOf(stationarySmoothed, orientationSmoothed)
            .map { postured ->
                if (postured) {
                    PosturedState.Postured
                } else {
                    PosturedState.NotPostured
                }
            }
            .flowOn(bgDispatcher)
            .stateIn(
                scope = applicationScope,
                // Avoid losing the smoothing history if the user plug/unplugs rapidly.
                started =
                    SharingStarted.WhileSubscribed(
                        stopTimeoutMillis = STOP_TIMEOUT_AFTER_UNSUBSCRIBE.inWholeMilliseconds,
                        replayExpirationMillis = 0,
                    ),
                initialValue = PosturedState.Unknown,
            )

    /**
     * Whether the device is postured.
     *
     * NOTE: Due to smoothing, this signal may be delayed to ensure we have a stable reading before
     * being considered postured.
     */
    val postured: Flow<Boolean> =
        combine(posturedSmoothed, debugPostured) { postured, debugValue ->
            debugValue.asBoolean() ?: postured.asBoolean() ?: false
        }

    /**
     * Helper for observing a trigger sensor, which automatically unregisters itself after it
     * executes once.
     */
    private fun observeTriggerSensor(type: Int): Flow<Unit> {
        val sensor = asyncSensorManager.getDefaultSensor(type) ?: return emptyFlow()
        return asyncSensorManager.observeTriggerSensor(sensor)
    }

    companion object {
        const val TAG = "PosturingInteractor"
        val SLIDING_WINDOW_DURATION = 10.seconds
        const val CONFIDENCE_THRESHOLD = 0.8f
        val STOP_TIMEOUT_AFTER_UNSUBSCRIBE = 5.seconds
    }
}

fun PosturedState.asBoolean(): Boolean? {
    return when (this) {
        is PosturedState.Postured -> true
        PosturedState.NotPostured -> false
        PosturedState.Unknown -> null
    }
}

@SuppressLint("DefaultLocale")
fun List<Float>.formatWindowForDebugging(): String {
    return joinToString(prefix = "[", postfix = "]") { String.format("%.2f", it) }
}
