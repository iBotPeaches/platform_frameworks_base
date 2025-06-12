package com.android.systemui.shared.condition

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.condition.testStart
import com.android.systemui.condition.testStop
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ConditionExtensionsTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    @Test
    fun flowInitiallyTrue() =
        kosmos.runTest {
            val flow = flowOf(true)
            val condition = flow.toCondition(scope = testScope, Condition.START_EAGERLY)

            assertThat(condition.isConditionSet).isFalse()

            testStart(condition)
            assertThat(condition.isConditionSet).isTrue()
            assertThat(condition.isConditionMet).isTrue()
        }

    @Test
    fun flowInitiallyFalse() =
        kosmos.runTest {
            val flow = flowOf(false)
            val condition = flow.toCondition(scope = testScope, Condition.START_EAGERLY)

            assertThat(condition.isConditionSet).isFalse()

            testStart(condition)
            assertThat(condition.isConditionSet).isTrue()
            assertThat(condition.isConditionMet).isFalse()
        }

    @Test
    fun emptyFlowWithNoInitialValue() =
        kosmos.runTest {
            val flow = emptyFlow<Boolean>()
            val condition = flow.toCondition(scope = testScope, Condition.START_EAGERLY)
            testStop(condition)

            assertThat(condition.isConditionSet).isFalse()
            assertThat(condition.isConditionMet).isFalse()
        }

    @Test
    fun emptyFlowWithInitialValueOfTrue() =
        kosmos.runTest {
            val flow = emptyFlow<Boolean>()
            val condition =
                flow.toCondition(
                    scope = testScope,
                    strategy = Condition.START_EAGERLY,
                    initialValue = true,
                )
            testStart(condition)

            assertThat(condition.isConditionSet).isTrue()
            assertThat(condition.isConditionMet).isTrue()
        }

    @Test
    fun emptyFlowWithInitialValueOfFalse() =
        kosmos.runTest {
            val flow = emptyFlow<Boolean>()
            val condition =
                flow.toCondition(
                    scope = testScope,
                    strategy = Condition.START_EAGERLY,
                    initialValue = false,
                )
            testStart(condition)

            assertThat(condition.isConditionSet).isTrue()
            assertThat(condition.isConditionMet).isFalse()
        }

    @Test
    fun conditionUpdatesWhenFlowEmitsNewValue() =
        kosmos.runTest {
            val flow = MutableStateFlow(false)
            val condition = flow.toCondition(scope = testScope, strategy = Condition.START_EAGERLY)
            testStart(condition)

            assertThat(condition.isConditionSet).isTrue()
            assertThat(condition.isConditionMet).isFalse()

            flow.value = true
            runCurrent()
            assertThat(condition.isConditionMet).isTrue()

            flow.value = false
            runCurrent()
            assertThat(condition.isConditionMet).isFalse()

            testStop(condition)
        }

    @Test
    fun stoppingConditionUnsubscribesFromFlow() =
        kosmos.runTest {
            val flow = MutableSharedFlow<Boolean>()
            val condition = flow.toCondition(scope = testScope, strategy = Condition.START_EAGERLY)
            assertThat(flow.subscriptionCount.value).isEqualTo(0)

            testStart(condition)
            assertThat(flow.subscriptionCount.value).isEqualTo(1)

            testStop(condition)
            assertThat(flow.subscriptionCount.value).isEqualTo(0)
        }
}
