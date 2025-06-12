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

import android.hardware.Sensor
import android.hardware.TriggerEventListener
import android.platform.test.annotations.EnableFlags
import android.service.dreams.Flags.FLAG_ALLOW_DREAM_WHEN_POSTURED
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.posturing.data.model.PositionState
import com.android.systemui.communal.posturing.data.repository.fake
import com.android.systemui.communal.posturing.data.repository.posturingRepository
import com.android.systemui.communal.posturing.shared.model.PosturedState
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.android.systemui.util.sensors.asyncSensorManager
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(FLAG_ALLOW_DREAM_WHEN_POSTURED)
class PosturingInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by Kosmos.Fixture { kosmos.posturingInteractor }

    @Test
    fun testNoDebugOverride() =
        kosmos.runTest {
            val postured by collectLastValue(underTest.postured)
            assertThat(postured).isFalse()

            posturingRepository.fake.emitPositionState(
                PositionState(
                    stationary = PositionState.StationaryState.Stationary(confidence = 1f),
                    orientation = PositionState.OrientationState.Postured(confidence = 1f),
                )
            )

            advanceTimeBySlidingWindowAndRun()
            assertThat(postured).isTrue()
        }

    @Test
    fun testLowConfidenceOrientation() =
        kosmos.runTest {
            val postured by collectLastValue(underTest.postured)
            assertThat(postured).isFalse()

            posturingRepository.fake.emitPositionState(
                PositionState(
                    stationary = PositionState.StationaryState.Stationary(confidence = 1f),
                    orientation = PositionState.OrientationState.Postured(confidence = 0.2f),
                )
            )

            advanceTimeBySlidingWindowAndRun()
            assertThat(postured).isFalse()
        }

    @Test
    fun testLowConfidenceStationary() =
        kosmos.runTest {
            val postured by collectLastValue(underTest.postured)
            assertThat(postured).isFalse()

            posturingRepository.fake.emitPositionState(
                PositionState(
                    stationary = PositionState.StationaryState.Stationary(confidence = 1f),
                    orientation = PositionState.OrientationState.Postured(confidence = 0.2f),
                )
            )

            advanceTimeBySlidingWindowAndRun()
            assertThat(postured).isFalse()
        }

    @Test
    fun testSlidingWindow() =
        kosmos.runTest {
            val postured by collectLastValue(underTest.postured)
            assertThat(postured).isFalse()

            posturingRepository.fake.emitPositionState(
                PositionState(
                    stationary = PositionState.StationaryState.Stationary(confidence = 1f),
                    orientation = PositionState.OrientationState.Postured(confidence = 0.2f),
                )
            )

            advanceTimeBy(PosturingInteractor.SLIDING_WINDOW_DURATION / 2)
            runCurrent()
            assertThat(postured).isFalse()

            posturingRepository.fake.emitPositionState(
                PositionState(
                    stationary = PositionState.StationaryState.Stationary(confidence = 1f),
                    orientation = PositionState.OrientationState.Postured(confidence = 1f),
                )
            )
            assertThat(postured).isFalse()
            advanceTimeBy(PosturingInteractor.SLIDING_WINDOW_DURATION / 2)
            runCurrent()

            // The 0.2 confidence will have fallen out of the sliding window, and we should now flip
            // to true.
            assertThat(postured).isTrue()

            advanceTimeBy(9999.hours)
            // We should remain postured if no other updates are received.
            assertThat(postured).isTrue()
        }

    @Test
    fun testLiftGesture_afterSlidingWindow() =
        kosmos.runTest {
            val triggerSensor = stubSensorManager()
            val sensor = asyncSensorManager.getDefaultSensor(Sensor.TYPE_PICK_UP_GESTURE)!!

            val postured by collectLastValue(underTest.postured)
            assertThat(postured).isFalse()

            posturingRepository.fake.emitPositionState(
                PositionState(
                    stationary = PositionState.StationaryState.Stationary(confidence = 1f),
                    orientation = PositionState.OrientationState.Postured(confidence = 1f),
                )
            )

            advanceTimeBySlidingWindowAndRun()
            assertThat(postured).isTrue()

            // If we detect a lift gesture, we should transition back to not postured.
            triggerSensor(sensor)
            assertThat(postured).isFalse()

            advanceTimeBy(9999.hours)
            assertThat(postured).isFalse()
        }

    @Test
    fun testLiftGesture_overridesSlidingWindow() =
        kosmos.runTest {
            val triggerSensor = stubSensorManager()
            val sensor = asyncSensorManager.getDefaultSensor(Sensor.TYPE_PICK_UP_GESTURE)!!

            val postured by collectLastValue(underTest.postured)
            assertThat(postured).isFalse()

            // Add multiple stationary + postured events to the sliding window.
            repeat(100) {
                advanceTimeBy(1.milliseconds)
                posturingRepository.fake.emitPositionState(
                    PositionState(
                        stationary = PositionState.StationaryState.Stationary(confidence = 1f),
                        orientation = PositionState.OrientationState.Postured(confidence = 1f),
                    )
                )
            }

            assertThat(postured).isTrue()

            // If we detect a lift gesture, we should transition back to not postured immediately.
            triggerSensor(sensor)
            assertThat(postured).isFalse()
        }

    @Test
    fun testSignificantMotion_afterSlidingWindow() =
        kosmos.runTest {
            val triggerSensor = stubSensorManager()
            val sensor = asyncSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)!!

            val postured by collectLastValue(underTest.postured)
            assertThat(postured).isFalse()

            posturingRepository.fake.emitPositionState(
                PositionState(
                    stationary = PositionState.StationaryState.Stationary(confidence = 1f),
                    orientation = PositionState.OrientationState.Postured(confidence = 1f),
                )
            )

            advanceTimeBySlidingWindowAndRun()
            assertThat(postured).isTrue()

            // If we detect motion, we should transition back to not postured.
            triggerSensor(sensor)
            assertThat(postured).isFalse()

            advanceTimeBy(9999.hours)
            assertThat(postured).isFalse()
        }

    @Test
    fun testOverriddenByDebugValue() =
        kosmos.runTest {
            val postured by collectLastValue(underTest.postured)
            assertThat(postured).isFalse()

            underTest.setValueForDebug(PosturedState.NotPostured)
            posturingRepository.fake.emitPositionState(
                PositionState(
                    stationary = PositionState.StationaryState.Stationary(confidence = 1f),
                    orientation = PositionState.OrientationState.Postured(confidence = 1f),
                )
            )

            // Repository value is overridden by debug value
            assertThat(postured).isFalse()

            underTest.setValueForDebug(PosturedState.Unknown)

            advanceTimeBySlidingWindowAndRun()
            assertThat(postured).isTrue()
        }

    private fun Kosmos.stubSensorManager(): (sensor: Sensor) -> Unit {
        val callbacks = mutableMapOf<Sensor, List<TriggerEventListener>>()
        val pickupSensor = mock<Sensor>()
        val motionSensor = mock<Sensor>()

        asyncSensorManager.stub {
            on { getDefaultSensor(Sensor.TYPE_PICK_UP_GESTURE) } doReturn pickupSensor
            on { getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) } doReturn motionSensor
            on { requestTriggerSensor(any(), any()) } doAnswer
                {
                    val callback = it.arguments[0] as TriggerEventListener
                    val sensor = it.arguments[1] as Sensor
                    callbacks[sensor] = callbacks.getOrElse(sensor) { emptyList() } + callback
                    true
                }
            on { cancelTriggerSensor(any(), any()) } doAnswer
                {
                    val callback = it.arguments[0] as TriggerEventListener
                    val sensor = it.arguments[1] as Sensor
                    callbacks[sensor] = callbacks.getOrElse(sensor) { emptyList() } - callback
                    true
                }
        }

        return { sensor: Sensor ->
            val list = callbacks.getOrElse(sensor) { emptyList() }
            // Simulate a trigger sensor which unregisters callbacks after triggering.
            callbacks[sensor] = emptyList()
            list.forEach { it.onTrigger(mock()) }
        }
    }
}
