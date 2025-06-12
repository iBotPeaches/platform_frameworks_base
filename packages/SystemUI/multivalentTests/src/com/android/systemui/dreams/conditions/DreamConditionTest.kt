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
package com.android.systemui.dreams.conditions

import android.app.DreamManager
import android.platform.test.annotations.EnabledOnRavenwood
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.shared.condition.Condition
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnabledOnRavenwood
class DreamConditionTest : SysuiTestCase() {
    private val kosmos = Kosmos()

    @Mock private lateinit var callback: Condition.Callback

    @Mock private lateinit var dreamManager: DreamManager

    @Mock private lateinit var keyguardUpdateMonitor: KeyguardUpdateMonitor

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
    }

    /** Ensure a dreaming state immediately triggers the condition. */
    @Test
    fun testInitialDreamingState() =
        kosmos.runTest {
            whenever(dreamManager.isDreaming).thenReturn(true)
            val condition = DreamCondition(testScope, dreamManager, keyguardUpdateMonitor)
            condition.addCallback(callback)
            runCurrent()

            Mockito.verify(callback).onConditionChanged(eq(condition))
            Truth.assertThat(condition.isConditionMet).isTrue()
        }

    /** Ensure a non-dreaming state does not trigger the condition. */
    @Test
    fun testInitialNonDreamingState() =
        kosmos.runTest {
            whenever(dreamManager.isDreaming).thenReturn(false)
            val condition = DreamCondition(testScope, dreamManager, keyguardUpdateMonitor)
            condition.addCallback(callback)

            Mockito.verify(callback, Mockito.never()).onConditionChanged(eq(condition))
            Truth.assertThat(condition.isConditionMet).isFalse()
        }

    /** Ensure that changing dream state triggers condition. */
    @Test
    fun testChange() =
        kosmos.runTest {
            val callbackCaptor = argumentCaptor<KeyguardUpdateMonitorCallback>()
            whenever(dreamManager.isDreaming).thenReturn(true)
            val condition = DreamCondition(testScope, dreamManager, keyguardUpdateMonitor)
            condition.addCallback(callback)
            runCurrent()
            Mockito.verify(keyguardUpdateMonitor).registerCallback(callbackCaptor.capture())

            Mockito.clearInvocations(callback)
            callbackCaptor.lastValue.onDreamingStateChanged(false)
            runCurrent()
            Mockito.verify(callback).onConditionChanged(eq(condition))
            Truth.assertThat(condition.isConditionMet).isFalse()

            Mockito.clearInvocations(callback)
            callbackCaptor.lastValue.onDreamingStateChanged(true)
            runCurrent()
            Mockito.verify(callback).onConditionChanged(eq(condition))
            Truth.assertThat(condition.isConditionMet).isTrue()
        }
}
