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
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.log.TableLogBufferBase
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.time.FakeSystemClock
import java.util.Arrays
import java.util.function.Consumer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ConditionMonitorTest : SysuiTestCase() {
    private val kosmos = Kosmos()

    private lateinit var condition1: FakeCondition
    private lateinit var condition2: FakeCondition
    private lateinit var condition3: FakeCondition
    private lateinit var conditions: HashSet<Condition>
    private val executor = FakeExecutor(FakeSystemClock())

    @Mock private lateinit var logBuffer: TableLogBufferBase

    private lateinit var conditionMonitor: Monitor

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        condition1 = Mockito.spy(FakeCondition(kosmos.testScope))
        condition2 = Mockito.spy(FakeCondition(kosmos.testScope))
        condition3 = Mockito.spy(FakeCondition(kosmos.testScope))
        conditions = HashSet(listOf(condition1, condition2, condition3))

        conditionMonitor = Monitor(executor)
    }

    fun getDefaultBuilder(callback: Monitor.Callback): Monitor.Subscription.Builder {
        return Monitor.Subscription.Builder(callback).addConditions(conditions)
    }

    private fun createMockCondition(): Condition {
        val condition: Condition = mock()
        whenever(condition.isConditionSet).thenReturn(true)
        return condition
    }

    @Test
    fun testOverridingCondition() =
        kosmos.runTest {
            val overridingCondition = createMockCondition()
            val regularCondition = createMockCondition()
            val callback: Monitor.Callback = mock()
            val referenceCallback: Monitor.Callback = mock()

            val monitor = Monitor(executor)

            monitor.addSubscription(
                getDefaultBuilder(callback)
                    .addCondition(overridingCondition)
                    .addCondition(regularCondition)
                    .build()
            )

            monitor.addSubscription(
                getDefaultBuilder(referenceCallback).addCondition(regularCondition).build()
            )

            executor.runAllReady()

            whenever(overridingCondition.isOverridingCondition).thenReturn(true)
            whenever(overridingCondition.isConditionMet).thenReturn(true)
            whenever(regularCondition.isConditionMet).thenReturn(false)

            val callbackCaptor = argumentCaptor<Condition.Callback>()

            Mockito.verify(overridingCondition).addCallback(callbackCaptor.capture())

            callbackCaptor.lastValue.onConditionChanged(overridingCondition)
            executor.runAllReady()

            Mockito.verify(callback).onConditionsChanged(eq(true))
            Mockito.verify(referenceCallback).onConditionsChanged(eq(false))
            Mockito.clearInvocations(callback)
            Mockito.clearInvocations(referenceCallback)

            whenever(regularCondition.isConditionMet).thenReturn(true)
            whenever(overridingCondition.isConditionMet).thenReturn(false)

            callbackCaptor.lastValue.onConditionChanged(overridingCondition)
            executor.runAllReady()

            Mockito.verify(callback).onConditionsChanged(eq(false))
            Mockito.verify(referenceCallback, Mockito.never()).onConditionsChanged(anyBoolean())
        }

    /**
     * Ensures that when multiple overriding conditions are present, it is the aggregate of those
     * conditions that are considered.
     */
    @Test
    fun testMultipleOverridingConditions() =
        kosmos.runTest {
            val overridingCondition = createMockCondition()
            val overridingCondition2 = createMockCondition()
            val regularCondition = createMockCondition()
            val callback: Monitor.Callback = mock()

            val monitor = Monitor(executor)

            monitor.addSubscription(
                getDefaultBuilder(callback)
                    .addCondition(overridingCondition)
                    .addCondition(overridingCondition2)
                    .build()
            )

            executor.runAllReady()

            whenever(overridingCondition.isOverridingCondition).thenReturn(true)
            whenever(overridingCondition.isConditionMet).thenReturn(true)
            whenever(overridingCondition2.isOverridingCondition).thenReturn(true)
            whenever(overridingCondition.isConditionMet).thenReturn(false)
            whenever(regularCondition.isConditionMet).thenReturn(true)

            val mCallbackCaptor = argumentCaptor<Condition.Callback>()

            Mockito.verify(overridingCondition).addCallback(mCallbackCaptor.capture())

            mCallbackCaptor.lastValue.onConditionChanged(overridingCondition)
            executor.runAllReady()

            Mockito.verify(callback).onConditionsChanged(eq(false))
            Mockito.clearInvocations(callback)
        }

    // Ensure that updating a callback that is removed doesn't result in an exception due to the
    // absence of the condition.
    @Test
    fun testUpdateRemovedCallback() =
        kosmos.runTest {
            val callback1: Monitor.Callback = mock()
            val subscription1 =
                conditionMonitor.addSubscription(getDefaultBuilder(callback1).build())
            val monitorCallback = argumentCaptor<Condition.Callback>()
            executor.runAllReady()
            Mockito.verify(condition1).addCallback(monitorCallback.capture())
            // This will execute first before the handler for onConditionChanged.
            conditionMonitor.removeSubscription(subscription1)
            monitorCallback.lastValue.onConditionChanged(condition1)
            executor.runAllReady()
        }

    @Test
    fun addCallback_addFirstCallback_addCallbackToAllConditions() {
        val callback1: Monitor.Callback = mock()
        conditionMonitor.addSubscription(getDefaultBuilder(callback1).build())
        executor.runAllReady()
        conditions.forEach(
            Consumer { condition: Condition -> Mockito.verify(condition).addCallback(any()) }
        )

        val callback2: Monitor.Callback = mock()
        conditionMonitor.addSubscription(getDefaultBuilder(callback2).build())
        executor.runAllReady()
        conditions.forEach(
            Consumer { condition: Condition ->
                Mockito.verify(condition, Mockito.times(1)).addCallback(any())
            }
        )
    }

    @Test
    fun addCallback_addFirstCallback_reportWithDefaultValue() =
        kosmos.runTest {
            val callback: Monitor.Callback = mock()
            conditionMonitor.addSubscription(getDefaultBuilder(callback).build())
            executor.runAllReady()
            Mockito.verify(callback).onConditionsChanged(false)
        }

    @Test
    fun addCallback_addSecondCallback_reportWithExistingValue() =
        kosmos.runTest {
            val callback1: Monitor.Callback = mock()
            val condition: Condition = mock()

            whenever(condition.isConditionMet).thenReturn(true)
            val monitor = Monitor(executor)
            monitor.addSubscription(
                Monitor.Subscription.Builder(callback1).addCondition(condition).build()
            )

            val callback2: Monitor.Callback = mock()
            monitor.addSubscription(
                Monitor.Subscription.Builder(callback2).addCondition(condition).build()
            )
            executor.runAllReady()
            Mockito.verify(callback2).onConditionsChanged(eq(true))
        }

    @Test
    fun addCallback_noConditions_reportAllConditionsMet() =
        kosmos.runTest {
            val monitor = Monitor(executor)
            val callback: Monitor.Callback = mock()

            monitor.addSubscription(Monitor.Subscription.Builder(callback).build())
            executor.runAllReady()
            Mockito.verify(callback).onConditionsChanged(true)
        }

    @Test
    fun addCallback_preCondition_noConditions_reportAllConditionsMet() =
        kosmos.runTest {
            val monitor = Monitor(executor, HashSet<Condition?>(Arrays.asList(condition1)))
            val callback: Monitor.Callback = mock()

            monitor.addSubscription(Monitor.Subscription.Builder(callback).build())
            executor.runAllReady()
            Mockito.verify(callback, Mockito.never()).onConditionsChanged(true)
            condition1.fakeUpdateCondition(true)
            executor.runAllReady()
            Mockito.verify(callback).onConditionsChanged(true)
        }

    @Test
    fun removeCallback_noFailureOnDoubleRemove() =
        kosmos.runTest {
            val condition: Condition = mock()
            val monitor = Monitor(executor)
            val callback: Monitor.Callback = mock()
            val token =
                monitor.addSubscription(
                    Monitor.Subscription.Builder(callback).addCondition(condition).build()
                )
            monitor.removeSubscription(token)
            executor.runAllReady()
            // Ensure second removal doesn't cause an exception.
            monitor.removeSubscription(token)
            executor.runAllReady()
        }

    @Test
    fun removeCallback_shouldNoLongerReceiveUpdate() =
        kosmos.runTest {
            val condition: Condition = mock()
            val monitor = Monitor(executor)
            val callback: Monitor.Callback = mock()
            val token =
                monitor.addSubscription(
                    Monitor.Subscription.Builder(callback).addCondition(condition).build()
                )
            monitor.removeSubscription(token)
            executor.runAllReady()
            Mockito.clearInvocations(callback)

            val conditionCallbackCaptor = argumentCaptor<Condition.Callback>()
            Mockito.verify(condition).addCallback(conditionCallbackCaptor.capture())

            val conditionCallback = conditionCallbackCaptor.lastValue
            Mockito.verify(condition).removeCallback(conditionCallback)
        }

    @Test
    fun removeCallback_removeLastCallback_removeCallbackFromAllConditions() =
        kosmos.runTest {
            val callback1: Monitor.Callback = mock()
            val callback2: Monitor.Callback = mock()
            val subscription1 =
                conditionMonitor.addSubscription(getDefaultBuilder(callback1).build())
            val subscription2 =
                conditionMonitor.addSubscription(getDefaultBuilder(callback2).build())

            conditionMonitor.removeSubscription(subscription1)
            executor.runAllReady()
            conditions.forEach(
                Consumer { condition: Condition ->
                    verify(condition, Mockito.never()).removeCallback(any())
                }
            )

            conditionMonitor.removeSubscription(subscription2)
            executor.runAllReady()
            conditions.forEach(
                Consumer { condition: Condition -> Mockito.verify(condition).removeCallback(any()) }
            )
        }

    @Test
    fun updateCallbacks_allConditionsMet_reportTrue() =
        kosmos.runTest {
            val callback: Monitor.Callback = mock()
            conditionMonitor.addSubscription(getDefaultBuilder(callback).build())
            Mockito.clearInvocations(callback)

            condition1.fakeUpdateCondition(true)
            condition2.fakeUpdateCondition(true)
            condition3.fakeUpdateCondition(true)
            executor.runAllReady()

            Mockito.verify(callback).onConditionsChanged(true)
        }

    @Test
    fun updateCallbacks_oneConditionStoppedMeeting_reportFalse() =
        kosmos.runTest {
            val callback: Monitor.Callback = mock()
            conditionMonitor.addSubscription(getDefaultBuilder(callback).build())

            condition1.fakeUpdateCondition(true)
            condition2.fakeUpdateCondition(true)
            condition3.fakeUpdateCondition(true)
            Mockito.clearInvocations(callback)

            condition1.fakeUpdateCondition(false)
            executor.runAllReady()
            Mockito.verify(callback).onConditionsChanged(false)
        }

    @Test
    fun updateCallbacks_shouldOnlyUpdateWhenValueChanges() =
        kosmos.runTest {
            val callback: Monitor.Callback = mock()
            conditionMonitor.addSubscription(getDefaultBuilder(callback).build())
            executor.runAllReady()
            Mockito.verify(callback).onConditionsChanged(false)
            Mockito.clearInvocations(callback)

            condition1.fakeUpdateCondition(true)
            executor.runAllReady()
            Mockito.verify(callback, Mockito.never()).onConditionsChanged(anyBoolean())

            condition2.fakeUpdateCondition(true)
            executor.runAllReady()
            Mockito.verify(callback, Mockito.never()).onConditionsChanged(anyBoolean())

            condition3.fakeUpdateCondition(true)
            executor.runAllReady()
            Mockito.verify(callback).onConditionsChanged(true)
        }

    @Test
    fun clearCondition_shouldUpdateValue() =
        kosmos.runTest {
            condition1.fakeUpdateCondition(false)
            condition2.fakeUpdateCondition(true)
            condition3.fakeUpdateCondition(true)

            val callback: Monitor.Callback = mock()
            conditionMonitor.addSubscription(getDefaultBuilder(callback).build())
            executor.runAllReady()
            Mockito.verify(callback).onConditionsChanged(false)

            condition1.clearCondition()
            executor.runAllReady()
            Mockito.verify(callback).onConditionsChanged(true)
        }

    @Test
    fun unsetCondition_shouldNotAffectValue() =
        kosmos.runTest {
            val settableCondition = FakeCondition(testScope, null, false)
            condition1.fakeUpdateCondition(true)
            condition2.fakeUpdateCondition(true)
            condition3.fakeUpdateCondition(true)

            val callback: Monitor.Callback = mock()

            conditionMonitor.addSubscription(
                getDefaultBuilder(callback).addCondition(settableCondition).build()
            )

            executor.runAllReady()
            Mockito.verify(callback).onConditionsChanged(true)
        }

    @Test
    fun setUnsetCondition_shouldAffectValue() =
        kosmos.runTest {
            val settableCondition = FakeCondition(testScope, null, false)
            condition1.fakeUpdateCondition(true)
            condition2.fakeUpdateCondition(true)
            condition3.fakeUpdateCondition(true)

            val callback: Monitor.Callback = mock()

            conditionMonitor.addSubscription(
                getDefaultBuilder(callback).addCondition(settableCondition).build()
            )

            executor.runAllReady()
            Mockito.verify(callback).onConditionsChanged(true)
            Mockito.clearInvocations(callback)

            settableCondition.fakeUpdateCondition(false)
            executor.runAllReady()
            Mockito.verify(callback).onConditionsChanged(false)
            Mockito.clearInvocations(callback)

            settableCondition.clearCondition()
            executor.runAllReady()
            Mockito.verify(callback).onConditionsChanged(true)
        }

    @Test
    fun clearingOverridingCondition_shouldBeExcluded() =
        kosmos.runTest {
            val overridingCondition = FakeCondition(testScope, true, true)
            condition1.fakeUpdateCondition(false)
            condition2.fakeUpdateCondition(false)
            condition3.fakeUpdateCondition(false)

            val callback: Monitor.Callback = mock()

            conditionMonitor.addSubscription(
                getDefaultBuilder(callback).addCondition(overridingCondition).build()
            )

            executor.runAllReady()
            Mockito.verify(callback).onConditionsChanged(true)
            Mockito.clearInvocations(callback)

            overridingCondition.clearCondition()
            executor.runAllReady()
            Mockito.verify(callback).onConditionsChanged(false)
        }

    @Test
    fun settingUnsetOverridingCondition_shouldBeIncluded() =
        kosmos.runTest {
            val overridingCondition = FakeCondition(testScope, null, true)
            condition1.fakeUpdateCondition(false)
            condition2.fakeUpdateCondition(false)
            condition3.fakeUpdateCondition(false)

            val callback: Monitor.Callback = mock()

            conditionMonitor.addSubscription(
                getDefaultBuilder(callback).addCondition(overridingCondition).build()
            )

            executor.runAllReady()
            Mockito.verify(callback).onConditionsChanged(false)
            Mockito.clearInvocations(callback)

            overridingCondition.fakeUpdateCondition(true)
            executor.runAllReady()
            Mockito.verify(callback).onConditionsChanged(true)
        }

    /**
     * Ensures that the result of a condition being true leads to its nested condition being
     * activated.
     */
    @Test
    fun testNestedCondition() =
        kosmos.runTest {
            condition1.fakeUpdateCondition(false)
            val callback: Monitor.Callback = mock()

            condition2.fakeUpdateCondition(false)

            // Create a nested condition
            conditionMonitor.addSubscription(
                Monitor.Subscription.Builder(
                        Monitor.Subscription.Builder(callback).addCondition(condition2).build()
                    )
                    .addCondition(condition1)
                    .build()
            )

            executor.runAllReady()

            // Ensure the nested condition callback is not called at all.
            Mockito.verify(callback, Mockito.never()).onActiveChanged(anyBoolean())
            Mockito.verify(callback, Mockito.never()).onConditionsChanged(anyBoolean())

            // Update the inner condition to true and ensure that the nested condition is not
            // triggered.
            condition2.fakeUpdateCondition(true)
            Mockito.verify(callback, Mockito.never()).onConditionsChanged(anyBoolean())
            condition2.fakeUpdateCondition(false)

            // Set outer condition and make sure the inner condition becomes active and reports that
            // conditions aren't met
            condition1.fakeUpdateCondition(true)
            executor.runAllReady()

            Mockito.verify(callback).onActiveChanged(eq(true))
            Mockito.verify(callback).onConditionsChanged(eq(false))

            Mockito.clearInvocations(callback)

            // Update the inner condition and make sure the callback is updated.
            condition2.fakeUpdateCondition(true)
            executor.runAllReady()

            Mockito.verify(callback).onConditionsChanged(true)

            Mockito.clearInvocations(callback)
            // Invalidate outer condition and make sure callback is informed, but the last state is
            // not affected.
            condition1.fakeUpdateCondition(false)
            executor.runAllReady()

            Mockito.verify(callback).onActiveChanged(eq(false))
            Mockito.verify(callback, Mockito.never()).onConditionsChanged(anyBoolean())
        }

    /** Ensure preconditions are applied to every subscription added to a monitor. */
    @Test
    fun testPreconditionMonitor() {
        val callback: Monitor.Callback = mock()

        condition2.fakeUpdateCondition(true)
        val monitor = Monitor(executor, HashSet<Condition?>(listOf(condition1)))

        monitor.addSubscription(
            Monitor.Subscription.Builder(callback).addCondition(condition2).build()
        )

        executor.runAllReady()

        Mockito.verify(callback, Mockito.never()).onActiveChanged(anyBoolean())
        Mockito.verify(callback, Mockito.never()).onConditionsChanged(anyBoolean())

        condition1.fakeUpdateCondition(true)
        executor.runAllReady()

        Mockito.verify(callback).onActiveChanged(eq(true))
        Mockito.verify(callback).onConditionsChanged(eq(true))
    }

    @Test
    fun testLoggingCallback() =
        kosmos.runTest {
            val monitor = Monitor(executor, emptySet(), logBuffer)

            val condition = FakeCondition(testScope)
            val overridingCondition =
                FakeCondition(testScope, /* initialValue= */ false, /* overriding= */ true)

            val callback: Monitor.Callback = mock()
            monitor.addSubscription(
                getDefaultBuilder(callback)
                    .addCondition(condition)
                    .addCondition(overridingCondition)
                    .build()
            )
            executor.runAllReady()

            // condition set to true
            condition.fakeUpdateCondition(true)
            executor.runAllReady()
            Mockito.verify(logBuffer).logChange("", "FakeCondition", "True")

            // condition set to false
            condition.fakeUpdateCondition(false)
            executor.runAllReady()
            Mockito.verify(logBuffer).logChange("", "FakeCondition", "False")

            // condition unset
            condition.fakeClearCondition()
            executor.runAllReady()
            Mockito.verify(logBuffer).logChange("", "FakeCondition", "Invalid")

            // overriding condition set to true
            overridingCondition.fakeUpdateCondition(true)
            executor.runAllReady()
            Mockito.verify(logBuffer).logChange("", "FakeCondition[OVRD]", "True")
        }
}
