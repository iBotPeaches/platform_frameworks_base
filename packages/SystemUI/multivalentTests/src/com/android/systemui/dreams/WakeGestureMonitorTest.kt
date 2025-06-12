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

package com.android.systemui.dreams

import android.hardware.Sensor
import android.hardware.TriggerEventListener
import android.hardware.display.ambientDisplayConfiguration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.android.systemui.util.sensors.asyncSensorManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

@SmallTest
@RunWith(AndroidJUnit4::class)
class WakeGestureMonitorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by Kosmos.Fixture { wakeGestureMonitor }

    @Test
    fun testPickupGestureNotEnabled_doesNotSubscribeToSensor() =
        kosmos.runTest {
            ambientDisplayConfiguration.fakePickupGestureEnabled = false
            val triggerSensor = stubSensorManager()

            val wakeUpDetected by collectValues(underTest.wakeUpDetected)
            triggerSensor()
            assertThat(wakeUpDetected).isEmpty()
        }

    @Test
    fun testPickupGestureEnabled_subscribesToSensor() =
        kosmos.runTest {
            ambientDisplayConfiguration.fakePickupGestureEnabled = true
            val triggerSensor = stubSensorManager()

            val wakeUpDetected by collectValues(underTest.wakeUpDetected)
            triggerSensor()
            assertThat(wakeUpDetected).hasSize(1)
            triggerSensor()
            assertThat(wakeUpDetected).hasSize(2)
        }

    private fun Kosmos.stubSensorManager(): () -> Unit {
        val callbacks = mutableListOf<TriggerEventListener>()
        val pickupSensor = mock<Sensor>()

        asyncSensorManager.stub {
            on { getDefaultSensor(Sensor.TYPE_PICK_UP_GESTURE) } doReturn pickupSensor
            on { requestTriggerSensor(any(), eq(pickupSensor)) } doAnswer
                {
                    val callback = it.arguments[0] as TriggerEventListener
                    callbacks.add(callback)
                    true
                }
            on { cancelTriggerSensor(any(), any()) } doAnswer
                {
                    val callback = it.arguments[0] as TriggerEventListener
                    callbacks.remove(callback)
                    true
                }
        }

        return {
            val list = callbacks.toList()
            // Simulate a trigger sensor which unregisters callbacks after triggering.
            while (callbacks.isNotEmpty()) {
                callbacks.removeLast()
            }
            list.forEach { it.onTrigger(mock()) }
        }
    }
}
