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

package com.android.systemui.communal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.keyguard.keyguardUpdateMonitor
import com.android.systemui.SysuiTestCase
import com.android.systemui.condition.testStart
import com.android.systemui.keyguard.WakefulnessLifecycle
import com.android.systemui.keyguard.WakefulnessLifecycle.WAKEFULNESS_ASLEEP
import com.android.systemui.keyguard.WakefulnessLifecycle.WAKEFULNESS_AWAKE
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.DozeTransitionModel
import com.android.systemui.keyguard.wakefulnessLifecycle
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.policy.keyguardStateController
import com.android.systemui.testKosmos
import com.android.systemui.util.kotlin.JavaAdapter
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceInactiveConditionTest : SysuiTestCase() {
    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().also {
            whenever(it.wakefulnessLifecycle.wakefulness) doReturn WAKEFULNESS_AWAKE
        }

    private val Kosmos.underTest by
        Kosmos.Fixture {
            DeviceInactiveCondition(
                applicationCoroutineScope,
                applicationCoroutineScope,
                keyguardStateController,
                wakefulnessLifecycle,
                keyguardUpdateMonitor,
                keyguardInteractor,
                JavaAdapter(applicationCoroutineScope),
            )
        }

    @Test
    fun asleep_conditionTrue() =
        kosmos.runTest {
            // Condition is false to start.
            testStart(underTest)
            assertThat(underTest.isConditionMet).isFalse()

            // Condition is true when device goes to sleep.
            sleep()
            assertThat(underTest.isConditionMet).isTrue()
        }

    @Test
    fun dozingAndAsleep_conditionFalse() =
        kosmos.runTest {
            // Condition is true when device is asleep.
            testStart(underTest)
            sleep()
            assertThat(underTest.isConditionMet).isTrue()

            // Condition turns false after doze starts.
            fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.UNINITIALIZED, to = DozeStateModel.DOZE)
            )
            assertThat(underTest.isConditionMet).isFalse()
        }

    fun Kosmos.sleep() {
        whenever(wakefulnessLifecycle.wakefulness) doReturn WAKEFULNESS_ASLEEP
        argumentCaptor<WakefulnessLifecycle.Observer>().apply {
            verify(wakefulnessLifecycle).addObserver(capture())
            firstValue.onStartedGoingToSleep()
        }
    }
}
