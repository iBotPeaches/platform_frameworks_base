/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.util.kotlin

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PairwiseFlowTest : SysuiTestCase() {
    @Test
    fun simple() = runBlocking {
        assertThatFlow((1..3).asFlow().pairwise()).emitsExactly(WithPrev(1, 2), WithPrev(2, 3))
    }

    @Test fun notEnough() = runBlocking { assertThatFlow(flowOf(1).pairwise()).emitsNothing() }

    @Test
    fun withInit() = runBlocking {
        assertThatFlow(flowOf(2).pairwise(initialValue = 1)).emitsExactly(WithPrev(1, 2))
    }

    @Test
    fun notEnoughWithInit() = runBlocking {
        assertThatFlow(emptyFlow<Int>().pairwise(initialValue = 1)).emitsNothing()
    }

    @Test
    fun withTransform() = runBlocking {
        assertThatFlow(
                flowOf("val1", "val2", "val3").pairwiseBy { prev: String, next: String ->
                    "$prev|$next"
                }
            )
            .emitsExactly("val1|val2", "val2|val3")
    }

    @Test
    fun withGetInit() = runBlocking {
        var initRun = false
        assertThatFlow(
                flowOf("val1", "val2").pairwiseBy(
                    getInitialValue = {
                        initRun = true
                        "initial"
                    }
                ) { prev: String, next: String ->
                    "$prev|$next"
                }
            )
            .emitsExactly("initial|val1", "val1|val2")
        assertThat(initRun).isTrue()
    }

    @Test
    fun notEnoughWithGetInit() = runBlocking {
        var initRun = false
        assertThatFlow(
                emptyFlow<String>().pairwiseBy(
                    getInitialValue = {
                        initRun = true
                        "initial"
                    }
                ) { prev: String, next: String ->
                    "$prev|$next"
                }
            )
            .emitsNothing()
        // Even though the flow will not emit anything, the initial value function should still get
        // run.
        assertThat(initRun).isTrue()
    }

    @Test
    fun getInitNotRunWhenFlowNotCollected() = runBlocking {
        var initRun = false
        flowOf("val1", "val2").pairwiseBy(
            getInitialValue = {
                initRun = true
                "initial"
            }
        ) { prev: String, next: String ->
            "$prev|$next"
        }

        // Since the flow isn't collected, ensure [initialValueFun] isn't run.
        assertThat(initRun).isFalse()
    }

    @Test
    fun withStateFlow() =
        runBlocking(Dispatchers.Main.immediate) {
            val state = MutableStateFlow(1)
            val stop = MutableSharedFlow<Unit>()

            val stoppable = merge(state, stop).takeWhile { it is Int }.filterIsInstance<Int>()

            val job1 = launch { assertThatFlow(stoppable.pairwise()).emitsExactly(WithPrev(1, 2)) }
            state.value = 2
            val job2 = launch { assertThatFlow(stoppable.pairwise()).emitsNothing() }

            stop.emit(Unit)

            assertThatJob(job1).isCompleted()
            assertThatJob(job2).isCompleted()
        }
}

@SmallTest
@RunWith(AndroidJUnit4::class)
class SetChangesFlowTest : SysuiTestCase() {
    @Test
    fun simple() = runBlocking {
        assertThatFlow(flowOf(setOf(1, 2, 3), setOf(2, 3, 4)).setChanges())
            .emitsExactly(
                SetChanges(added = setOf(1, 2, 3), removed = emptySet()),
                SetChanges(added = setOf(4), removed = setOf(1)),
            )
    }

    @Test
    fun onlyOneEmission() = runBlocking {
        assertThatFlow(flowOf(setOf(1)).setChanges())
            .emitsExactly(SetChanges(added = setOf(1), removed = emptySet()))
    }

    @Test
    fun fromEmptySet() = runBlocking {
        assertThatFlow(flowOf(emptySet(), setOf(1, 2)).setChanges())
            .emitsExactly(SetChanges(removed = emptySet(), added = setOf(1, 2)))
    }

    @Test
    fun dontEmitFirstEvent() = runBlocking {
        assertThatFlow(flowOf(setOf(1, 2), setOf(2, 3)).setChanges(emitFirstEvent = false))
            .emitsExactly(SetChanges(removed = setOf(1), added = setOf(3)))
    }
}

@SmallTest
@RunWith(AndroidJUnit4::class)
class SampleFlowTest : SysuiTestCase() {
    @Test
    fun simple() = runBlocking {
        assertThatFlow(
                flow {
                        yield()
                        emit(1)
                    }
                    .sample(flowOf(2)) { a, b -> a to b }
            )
            .emitsExactly(1 to 2)
    }

    @Test
    fun otherFlowNoValueYet() = runBlocking {
        assertThatFlow(flowOf(1).sample(emptyFlow<Unit>())).emitsNothing()
    }

    @Test
    fun multipleSamples() = runBlocking {
        val samplee = MutableSharedFlow<Int>()
        val sampler = flow {
            emit(1)
            samplee.emit(1)
            emit(2)
            samplee.emit(2)
            samplee.emit(3)
            emit(3)
            emit(4)
        }
        assertThatFlow(sampler.sample(samplee) { a, b -> a to b })
            .emitsExactly(2 to 1, 3 to 3, 4 to 3)
    }
}

@SmallTest
@RunWith(AndroidJUnit4::class)
class ThrottleFlowTest : SysuiTestCase() {

    @Test
    fun doesNotAffectEmissions_whenDelayAtLeastEqualToPeriod() = runTest {
        // Arrange
        val choreographer = createChoreographer(this)
        val output = mutableListOf<Int>()
        val collectJob =
            backgroundScope.launch {
                flow {
                        emit(1)
                        delay(1000)
                        emit(2)
                    }
                    .throttle(1000, choreographer.fakeClock)
                    .toList(output)
            }

        // Act
        choreographer.advanceAndRun(0)

        // Assert
        assertThat(output).containsExactly(1)

        // Act
        choreographer.advanceAndRun(999)

        // Assert
        assertThat(output).containsExactly(1)

        // Act
        choreographer.advanceAndRun(1)

        // Assert
        assertThat(output).containsExactly(1, 2)

        // Cleanup
        collectJob.cancel()
    }

    @Test
    fun delaysEmissions_withShorterThanPeriodDelay_untilPeriodElapses() = runTest {
        // Arrange
        val choreographer = createChoreographer(this)
        val output = mutableListOf<Int>()
        val collectJob =
            backgroundScope.launch {
                flow {
                        emit(1)
                        delay(500)
                        emit(2)
                    }
                    .throttle(1000, choreographer.fakeClock)
                    .toList(output)
            }

        // Act
        choreographer.advanceAndRun(0)

        // Assert
        assertThat(output).containsExactly(1)

        // Act
        choreographer.advanceAndRun(500)
        choreographer.advanceAndRun(499)

        // Assert
        assertThat(output).containsExactly(1)

        // Act
        choreographer.advanceAndRun(1)

        // Assert
        assertThat(output).containsExactly(1, 2)

        // Cleanup
        collectJob.cancel()
    }

    @Test
    fun filtersAllButLastEmission_whenMultipleEmissionsInPeriod() = runTest {
        // Arrange
        val choreographer = createChoreographer(this)
        val output = mutableListOf<Int>()
        val collectJob =
            backgroundScope.launch {
                flow {
                        emit(1)
                        delay(500)
                        emit(2)
                        delay(500)
                        emit(3)
                    }
                    .throttle(1000, choreographer.fakeClock)
                    .toList(output)
            }

        // Act
        choreographer.advanceAndRun(0)

        // Assert
        assertThat(output).containsExactly(1)

        // Act
        choreographer.advanceAndRun(500)
        choreographer.advanceAndRun(499)

        // Assert
        assertThat(output).containsExactly(1)

        // Act
        choreographer.advanceAndRun(1)

        // Assert
        assertThat(output).containsExactly(1, 3)

        // Cleanup
        collectJob.cancel()
    }

    @Test
    fun filtersAllButLastEmission_andDelaysIt_whenMultipleEmissionsInShorterThanPeriod() = runTest {
        // Arrange
        val choreographer = createChoreographer(this)
        val output = mutableListOf<Int>()
        val collectJob =
            backgroundScope.launch {
                flow {
                        emit(1)
                        delay(500)
                        emit(2)
                        delay(250)
                        emit(3)
                    }
                    .throttle(1000, choreographer.fakeClock)
                    .toList(output)
            }

        // Act
        choreographer.advanceAndRun(0)

        // Assert
        assertThat(output).containsExactly(1)

        // Act
        choreographer.advanceAndRun(500)
        choreographer.advanceAndRun(250)
        choreographer.advanceAndRun(249)

        // Assert
        assertThat(output).containsExactly(1)

        // Act
        choreographer.advanceAndRun(1)

        // Assert
        assertThat(output).containsExactly(1, 3)

        // Cleanup
        collectJob.cancel()
    }

    private fun createChoreographer(testScope: TestScope) =
        object {
            val fakeClock = FakeSystemClock()

            fun advanceAndRun(millis: Long) {
                fakeClock.advanceTime(millis)
                testScope.advanceTimeBy(millis)
                testScope.runCurrent()
            }
        }
}

@SmallTest
@RunWith(AndroidJUnit4::class)
class SlidingWindowFlowTest : SysuiTestCase() {

    @Test
    fun basicWindowing() = runTest {
        val choreographer = createChoreographer(this)
        val output = mutableListOf<List<Int>>()
        val collectJob =
            backgroundScope.launch {
                (1..5)
                    .asFlow()
                    .onEach { delay(100) }
                    .slidingWindow(300.milliseconds, choreographer.fakeClock)
                    .toList(output)
            }

        choreographer.advanceAndRun(0)
        assertThat(output).isEmpty()

        choreographer.advanceAndRun(100)
        assertThat(output).containsExactly(listOf(1))

        choreographer.advanceAndRun(1)
        assertThat(output).containsExactly(listOf(1))

        choreographer.advanceAndRun(99)
        assertThat(output).containsExactly(listOf(1), listOf(1, 2))

        choreographer.advanceAndRun(100)
        assertThat(output).containsExactly(listOf(1), listOf(1, 2), listOf(1, 2, 3))

        choreographer.advanceAndRun(100)
        assertThat(output)
            .containsExactly(listOf(1), listOf(1, 2), listOf(1, 2, 3), listOf(2, 3, 4))

        choreographer.advanceAndRun(100)
        assertThat(output)
            .containsExactly(
                listOf(1),
                listOf(1, 2),
                listOf(1, 2, 3),
                listOf(2, 3, 4),
                listOf(3, 4, 5),
            )

        choreographer.advanceAndRun(100)
        assertThat(output)
            .containsExactly(
                listOf(1),
                listOf(1, 2),
                listOf(1, 2, 3),
                listOf(2, 3, 4),
                listOf(3, 4, 5),
                listOf(4, 5),
            )

        choreographer.advanceAndRun(100)
        assertThat(output)
            .containsExactly(
                listOf(1),
                listOf(1, 2),
                listOf(1, 2, 3),
                listOf(2, 3, 4),
                listOf(3, 4, 5),
                listOf(4, 5),
                listOf(5),
            )

        choreographer.advanceAndRun(100)
        assertThat(output)
            .containsExactly(
                listOf(1),
                listOf(1, 2),
                listOf(1, 2, 3),
                listOf(2, 3, 4),
                listOf(3, 4, 5),
                listOf(4, 5),
                listOf(5),
                emptyList<Int>(),
            )

        // Verify no more emissions
        choreographer.advanceAndRun(9999999999)
        assertThat(output)
            .containsExactly(
                listOf(1),
                listOf(1, 2),
                listOf(1, 2, 3),
                listOf(2, 3, 4),
                listOf(3, 4, 5),
                listOf(4, 5),
                listOf(5),
                emptyList<Int>(),
            )

        assertThat(collectJob.isCompleted).isTrue()
    }

    @Test
    fun initialEmptyFlow() = runTest {
        val choreographer = createChoreographer(this)
        val output = mutableListOf<List<Int>>()
        val collectJob =
            backgroundScope.launch {
                flow {
                        delay(200)
                        emit(1)
                    }
                    .slidingWindow(100.milliseconds, choreographer.fakeClock)
                    .toList(output)
            }

        choreographer.advanceAndRun(0)
        assertThat(output).isEmpty()

        choreographer.advanceAndRun(200)
        assertThat(output).containsExactly(listOf(1))

        choreographer.advanceAndRun(100)
        assertThat(output).containsExactly(listOf(1), emptyList<Int>())

        assertThat(collectJob.isCompleted).isTrue()
    }

    @Test
    fun windowLargerThanData() = runTest {
        val choreographer = createChoreographer(this)
        val output = mutableListOf<List<Int>>()
        val collectJob =
            backgroundScope.launch {
                (1..3)
                    .asFlow()
                    .onEach { delay(50) }
                    .slidingWindow(500.milliseconds, choreographer.fakeClock)
                    .toList(output)
            }

        choreographer.advanceAndRun(0)
        assertThat(output).isEmpty()

        choreographer.advanceAndRun(50)
        assertThat(output).containsExactly(listOf(1))

        choreographer.advanceAndRun(50)
        assertThat(output).containsExactly(listOf(1), listOf(1, 2))

        choreographer.advanceAndRun(50)
        assertThat(output).containsExactly(listOf(1), listOf(1, 2), listOf(1, 2, 3))

        // It has been 100ms since the first emission, which means we have 400ms left until the
        // first item is evicted from the window. Ensure that we have no evictions until that time.
        choreographer.advanceAndRun(399)
        assertThat(output).containsExactly(listOf(1), listOf(1, 2), listOf(1, 2, 3))

        choreographer.advanceAndRun(1)
        assertThat(output).containsExactly(listOf(1), listOf(1, 2), listOf(1, 2, 3), listOf(2, 3))

        choreographer.advanceAndRun(50)
        assertThat(output)
            .containsExactly(listOf(1), listOf(1, 2), listOf(1, 2, 3), listOf(2, 3), listOf(3))

        choreographer.advanceAndRun(50)
        assertThat(output)
            .containsExactly(
                listOf(1),
                listOf(1, 2),
                listOf(1, 2, 3),
                listOf(2, 3),
                listOf(3),
                emptyList<Int>(),
            )

        assertThat(collectJob.isCompleted).isTrue()
    }

    @Test
    fun dataGapLargerThanWindow() = runTest {
        val choreographer = createChoreographer(this)
        val output = mutableListOf<List<Int>>()
        val collectJob =
            backgroundScope.launch {
                flow {
                        emit(1)
                        delay(200)
                        emit(2)
                        delay(500) // Gap larger than window
                        emit(3)
                    }
                    .slidingWindow(300.milliseconds, choreographer.fakeClock)
                    .toList(output)
            }

        choreographer.advanceAndRun(0)
        assertThat(output).containsExactly(listOf(1))

        choreographer.advanceAndRun(200)
        assertThat(output).containsExactly(listOf(1), listOf(1, 2))

        choreographer.advanceAndRun(100)
        assertThat(output).containsExactly(listOf(1), listOf(1, 2), listOf(2))

        choreographer.advanceAndRun(200)
        assertThat(output).containsExactly(listOf(1), listOf(1, 2), listOf(2), emptyList<Int>())

        choreographer.advanceAndRun(200)
        assertThat(output)
            .containsExactly(listOf(1), listOf(1, 2), listOf(2), emptyList<Int>(), listOf(3))

        choreographer.advanceAndRun(300)
        assertThat(output)
            .containsExactly(
                listOf(1),
                listOf(1, 2),
                listOf(2),
                emptyList<Int>(),
                listOf(3),
                emptyList<Int>(),
            )

        assertThat(collectJob.isCompleted).isTrue()
    }

    @Test
    fun emptyFlow() = runTest {
        val choreographer = createChoreographer(this)
        val output = mutableListOf<List<Int>>()

        val collectJob =
            backgroundScope.launch {
                emptyFlow<Int>().slidingWindow(100.milliseconds).toList(output)
            }

        choreographer.advanceAndRun(0)
        assertThat(output).isEmpty()

        assertThat(collectJob.isCompleted).isTrue()
    }

    private fun createChoreographer(testScope: TestScope) =
        object {
            val fakeClock = FakeSystemClock()

            fun advanceAndRun(millis: Long) {
                fakeClock.advanceTime(millis)
                testScope.advanceTimeBy(millis)
                testScope.runCurrent()
            }
        }
}

private fun <T> assertThatFlow(flow: Flow<T>) =
    object {
        suspend fun emitsExactly(vararg emissions: T) =
            assertThat(flow.toList()).containsExactly(*emissions).inOrder()

        suspend fun emitsNothing() = assertThat(flow.toList()).isEmpty()
    }

private fun assertThatJob(job: Job) =
    object {
        fun isCompleted() = assertThat(job.isCompleted).isTrue()
    }
