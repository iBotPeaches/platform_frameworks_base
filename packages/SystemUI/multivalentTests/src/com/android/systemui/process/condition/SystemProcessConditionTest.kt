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
package com.android.systemui.process.condition

import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.process.ProcessWrapper
import com.android.systemui.shared.condition.Condition
import com.android.systemui.shared.condition.Monitor
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.time.FakeSystemClock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@RunWithLooper
@SmallTest
class SystemProcessConditionTest : SysuiTestCase() {
    private val kosmos = Kosmos()

    @Mock private lateinit var processWrapper: ProcessWrapper

    @Mock private lateinit var callback: Monitor.Callback

    private val executor = FakeExecutor(FakeSystemClock())

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
    }

    /**
     * Verifies condition reports false when tracker reports the process is being ran by the system
     * user.
     */
    @Test
    fun testConditionFailsWithNonSystemProcess() =
        kosmos.runTest {
            val condition: Condition = SystemProcessCondition(kosmos.testScope, processWrapper)
            whenever(processWrapper.isSystemUser).thenReturn(false)

            val monitor = Monitor(executor)

            monitor.addSubscription(
                Monitor.Subscription.Builder(callback).addCondition(condition).build()
            )

            executor.runAllReady()
            runCurrent()
            executor.runAllReady()

            Mockito.verify(callback).onConditionsChanged(false)
        }

    /**
     * Verifies condition reports true when tracker reports the process is being ran by the system
     * user.
     */
    @Test
    fun testConditionSucceedsWithSystemProcess() =
        kosmos.runTest {
            val condition: Condition = SystemProcessCondition(testScope, processWrapper)
            whenever(processWrapper.isSystemUser).thenReturn(true)

            val monitor = Monitor(executor)

            monitor.addSubscription(
                Monitor.Subscription.Builder(callback).addCondition(condition).build()
            )

            executor.runAllReady()
            runCurrent()
            executor.runAllReady()

            Mockito.verify(callback).onConditionsChanged(true)
        }
}
