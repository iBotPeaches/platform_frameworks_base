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
package com.android.systemui.lowlightclock

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.internal.logging.UiEventLogger
import com.android.systemui.SysuiTestCase
import com.android.systemui.condition.testStart
import com.android.systemui.condition.testStop
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor

@SmallTest
@RunWith(AndroidTestingRunner::class)
class LowLightConditionTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    @Mock private lateinit var ambientLightModeMonitor: AmbientLightModeMonitor

    @Mock private lateinit var uiEventLogger: UiEventLogger

    private lateinit var condition: LowLightCondition

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        condition = LowLightCondition(kosmos.testScope, ambientLightModeMonitor, uiEventLogger)
    }

    @Test
    fun testLowLightFalse() =
        kosmos.runTest {
            testStart(condition)
            changeLowLightMode(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT)
            Truth.assertThat(condition.isConditionMet).isFalse()
        }

    @Test
    fun testLowLightTrue() =
        kosmos.runTest {
            testStart(condition)
            changeLowLightMode(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)
            Truth.assertThat(condition.isConditionMet).isTrue()
        }

    @Test
    fun testUndecidedLowLightStateIgnored() =
        kosmos.runTest {
            testStart(condition)
            changeLowLightMode(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)
            Truth.assertThat(condition.isConditionMet).isTrue()
            changeLowLightMode(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_UNDECIDED)
            Truth.assertThat(condition.isConditionMet).isTrue()
        }

    @Test
    fun testLowLightChange() =
        kosmos.runTest {
            testStart(condition)
            changeLowLightMode(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT)
            Truth.assertThat(condition.isConditionMet).isFalse()
            changeLowLightMode(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)
            Truth.assertThat(condition.isConditionMet).isTrue()
        }

    @Test
    fun testResetIsConditionMetUponStop() =
        kosmos.runTest {
            testStart(condition)
            runCurrent()
            changeLowLightMode(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)
            Truth.assertThat(condition.isConditionMet).isTrue()

            testStop(condition)
            Truth.assertThat(condition.isConditionMet).isFalse()
        }

    @Test
    fun testLoggingAmbientLightNotLowToLow() =
        kosmos.runTest {
            testStart(condition)
            changeLowLightMode(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)
            // Only logged once.
            Mockito.verify(uiEventLogger, Mockito.times(1)).log(ArgumentMatchers.any())
            // Logged with the correct state.
            Mockito.verify(uiEventLogger).log(LowLightDockEvent.AMBIENT_LIGHT_TO_DARK)
        }

    @Test
    fun testLoggingAmbientLightLowToLow() =
        kosmos.runTest {
            testStart(condition)
            changeLowLightMode(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)
            Mockito.reset(uiEventLogger)

            changeLowLightMode(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)
            // Doesn't log.
            Mockito.verify(uiEventLogger, Mockito.never()).log(ArgumentMatchers.any())
        }

    @Test
    fun testLoggingAmbientLightNotLowToNotLow() =
        kosmos.runTest {
            testStart(condition)
            changeLowLightMode(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT)
            // Doesn't log.
            Mockito.verify(uiEventLogger, Mockito.never()).log(ArgumentMatchers.any())
        }

    @Test
    fun testLoggingAmbientLightLowToNotLow() =
        kosmos.runTest {
            testStart(condition)
            runCurrent()
            changeLowLightMode(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)
            Mockito.reset(uiEventLogger)

            changeLowLightMode(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT)
            // Only logged once.
            Mockito.verify(uiEventLogger).log(ArgumentMatchers.any())
            // Logged with the correct state.
            Mockito.verify(uiEventLogger).log(LowLightDockEvent.AMBIENT_LIGHT_TO_LIGHT)
        }

    private fun changeLowLightMode(mode: Int) {
        val ambientLightCallbackCaptor = argumentCaptor<AmbientLightModeMonitor.Callback>()

        Mockito.verify(ambientLightModeMonitor).start(ambientLightCallbackCaptor.capture())
        ambientLightCallbackCaptor.lastValue.onChange(mode)
    }
}
