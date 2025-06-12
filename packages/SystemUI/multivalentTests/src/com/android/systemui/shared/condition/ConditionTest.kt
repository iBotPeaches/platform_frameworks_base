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
package com.android.systemui.shared.condition

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class ConditionTest : SysuiTestCase() {
    private val kosmos = Kosmos()
    private lateinit var underTest: FakeCondition

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        underTest = Mockito.spy(FakeCondition(kosmos.testScope))
    }

    @Test
    fun addCallback_addFirstCallback_triggerStart() =
        kosmos.runTest {
            val callback = mock<Condition.Callback>()
            underTest.addCallback(callback)
            runCurrent()
            Mockito.verify(underTest).start()
        }

    @Test
    fun addCallback_addMultipleCallbacks_triggerStartOnlyOnce() =
        kosmos.runTest {
            val callback1 = mock<Condition.Callback>()
            val callback2 = mock<Condition.Callback>()
            val callback3 = mock<Condition.Callback>()

            underTest.addCallback(callback1)
            underTest.addCallback(callback2)
            underTest.addCallback(callback3)

            runCurrent()
            Mockito.verify(underTest).start()
        }

    @Test
    fun addCallback_alreadyStarted_triggerUpdate() =
        kosmos.runTest {
            val callback1 = mock<Condition.Callback>()
            underTest.addCallback(callback1)

            underTest.fakeUpdateCondition(true)

            val callback2 = mock<Condition.Callback>()
            underTest.addCallback(callback2)
            Mockito.verify(callback2).onConditionChanged(underTest)
            Truth.assertThat(underTest.isConditionMet).isTrue()
        }

    @Test
    fun removeCallback_removeLastCallback_triggerStop() =
        kosmos.runTest {
            val callback = mock<Condition.Callback>()
            underTest.addCallback(callback)
            Mockito.verify(underTest, Mockito.never()).stop()

            underTest.removeCallback(callback)
            Mockito.verify(underTest).stop()
        }

    @Test
    fun updateCondition_falseToTrue_reportTrue() =
        kosmos.runTest {
            underTest.fakeUpdateCondition(false)

            val callback = mock<Condition.Callback>()
            underTest.addCallback(callback)

            underTest.fakeUpdateCondition(true)
            Mockito.verify(callback).onConditionChanged(eq(underTest))
            Truth.assertThat(underTest.isConditionMet).isTrue()
        }

    @Test
    fun updateCondition_trueToFalse_reportFalse() =
        kosmos.runTest {
            underTest.fakeUpdateCondition(true)

            val callback = mock<Condition.Callback>()
            underTest.addCallback(callback)

            underTest.fakeUpdateCondition(false)
            Mockito.verify(callback).onConditionChanged(eq(underTest))
            Truth.assertThat(underTest.isConditionMet).isFalse()
        }

    @Test
    fun updateCondition_trueToTrue_reportNothing() =
        kosmos.runTest {
            underTest.fakeUpdateCondition(true)

            val callback = mock<Condition.Callback>()
            underTest.addCallback(callback)

            underTest.fakeUpdateCondition(true)
            Mockito.verify(callback, Mockito.never()).onConditionChanged(eq(underTest))
        }

    @Test
    fun updateCondition_falseToFalse_reportNothing() =
        kosmos.runTest {
            underTest.fakeUpdateCondition(false)

            val callback = mock<Condition.Callback>()
            underTest.addCallback(callback)

            underTest.fakeUpdateCondition(false)
            Mockito.verify(callback, Mockito.never()).onConditionChanged(eq(underTest))
        }

    @Test
    fun clearCondition_reportsNotSet() =
        kosmos.runTest {
            underTest.fakeUpdateCondition(false)
            Truth.assertThat(underTest.isConditionSet).isTrue()
            underTest.clearCondition()
            Truth.assertThat(underTest.isConditionSet).isFalse()
        }
}
