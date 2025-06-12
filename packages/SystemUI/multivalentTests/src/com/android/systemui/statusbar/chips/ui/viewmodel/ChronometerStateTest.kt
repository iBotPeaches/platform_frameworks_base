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

package com.android.systemui.statusbar.chips.ui.viewmodel

import android.text.format.DateUtils.formatElapsedTime
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ChronometerStateTest : SysuiTestCase() {

    private lateinit var fakeTimeSource: MutableTimeSource

    @Before
    fun setup() {
        fakeTimeSource = MutableTimeSource()
    }

    @Test
    fun initialText_isEventInFutureFalse_timeIsNow() = runTest {
        fakeTimeSource.time = 3_000
        val state =
            ChronometerState(fakeTimeSource, eventTimeMillis = 3_000, isEventInFuture = false)
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 0))
    }

    @Test
    fun initialText_isEventInFutureFalse_timeInPast() = runTest {
        fakeTimeSource.time = 3_000
        val state =
            ChronometerState(fakeTimeSource, eventTimeMillis = 1_000, isEventInFuture = false)
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 2))
    }

    @Test
    fun initialText_isEventInFutureFalse_timeInFuture() = runTest {
        fakeTimeSource.time = 3_000
        val state =
            ChronometerState(fakeTimeSource, eventTimeMillis = 5_000, isEventInFuture = false)
        // When isEventInFuture=false, eventTimeMillis needs to be in the past if we want text to
        // show
        assertThat(state.currentTimeText).isNull()
    }

    @Test
    fun initialText_isEventInFutureTrue_timeIsNow() = runTest {
        fakeTimeSource.time = 3_000
        val state =
            ChronometerState(fakeTimeSource, eventTimeMillis = 3_000, isEventInFuture = true)
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 0))
    }

    @Test
    fun initialText_isEventInFutureTrue_timeInFuture() = runTest {
        fakeTimeSource.time = 3_000
        val state =
            ChronometerState(fakeTimeSource, eventTimeMillis = 5_000, isEventInFuture = true)
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 2))
    }

    @Test
    fun initialText_isEventInFutureTrue_timeInPast() = runTest {
        fakeTimeSource.time = 3_000
        val state =
            ChronometerState(fakeTimeSource, eventTimeMillis = 1_000, isEventInFuture = true)
        // When isEventInFuture=true, eventTimeMillis needs to be in the future if we want text to
        // show
        assertThat(state.currentTimeText).isNull()
    }

    @Test
    fun textUpdates_isEventInFutureFalse_timeInPast() = runTest {
        val eventTime = 1000L
        val state = ChronometerState(fakeTimeSource, eventTime, isEventInFuture = false)
        val job = launch { state.run() }

        val elapsedTime = 5000L
        fakeTimeSource.time = eventTime + elapsedTime
        advanceTimeBy(elapsedTime)
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(elapsedTime / 1000))

        val additionalTime = 6000L
        fakeTimeSource.time += additionalTime
        advanceTimeBy(additionalTime)
        assertThat(state.currentTimeText)
            .isEqualTo(formatElapsedTime((elapsedTime + additionalTime) / 1000))

        job.cancelAndJoin()
    }

    @Test
    fun textUpdates_isEventInFutureFalse_timeChangesFromFutureToPast() = runTest {
        val eventTime = 15_000L
        val state = ChronometerState(fakeTimeSource, eventTime, isEventInFuture = false)
        val job = launch { state.run() }

        // WHEN the time is 5 but the eventTime is 15
        fakeTimeSource.time = 5_000L
        advanceTimeBy(5_000L)
        // THEN no text is shown
        assertThat(state.currentTimeText).isNull()

        // WHEN the time advances to 40
        fakeTimeSource.time = 40_000L
        advanceTimeBy(35_000)
        // THEN text is shown as 25 seconds (40 - 15)
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 25))

        job.cancelAndJoin()
    }

    @Test
    fun textUpdates_isEventInFutureTrue_timeInFuture() = runTest {
        val eventTime = 15_000L
        val state = ChronometerState(fakeTimeSource, eventTime, isEventInFuture = true)
        val job = launch { state.run() }

        fakeTimeSource.time = 5_000L
        advanceTimeBy(5_000L)
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 10))

        val additionalTime = 6000L
        fakeTimeSource.time += additionalTime
        advanceTimeBy(additionalTime)
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 4))

        job.cancelAndJoin()
    }

    @Test
    fun textUpdates_isEventInFutureTrue_timeChangesFromFutureToPast() = runTest {
        val eventTime = 15_000L
        val state = ChronometerState(fakeTimeSource, eventTime, isEventInFuture = true)
        val job = launch { state.run() }

        // WHEN the time is 5 and the eventTime is 15
        fakeTimeSource.time = 5_000L
        advanceTimeBy(5_000L)
        // THEN 10 seconds is shown
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 10))

        // WHEN the time advances to 40 (past the event time)
        fakeTimeSource.time = 40_000L
        advanceTimeBy(35_000)
        // THEN no text is shown
        assertThat(state.currentTimeText).isNull()

        job.cancelAndJoin()
    }

    @Test
    fun textUpdates_afterResettingBase_isEventInFutureFalse() = runTest {
        val initialElapsedTime = 30000L
        val startTime = 50000L
        val state = ChronometerState(fakeTimeSource, startTime, isEventInFuture = false)
        val job = launch { state.run() }

        fakeTimeSource.time = startTime + initialElapsedTime
        advanceTimeBy(initialElapsedTime)
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(initialElapsedTime / 1000))

        job.cancelAndJoin()

        val newElapsedTime = 5000L
        val newStartTime = 100000L
        val newState = ChronometerState(fakeTimeSource, newStartTime, isEventInFuture = false)
        val newJob = launch { newState.run() }

        fakeTimeSource.time = newStartTime + newElapsedTime
        advanceTimeBy(newElapsedTime)
        assertThat(newState.currentTimeText).isEqualTo(formatElapsedTime(newElapsedTime / 1000))

        newJob.cancelAndJoin()
    }

    @Test
    fun textUpdates_afterResettingBase_isEventInFutureTrue() = runTest {
        val initialElapsedTime = 40_000L
        val eventTime = 50_000L
        val state = ChronometerState(fakeTimeSource, eventTime, isEventInFuture = true)
        val job = launch { state.run() }

        fakeTimeSource.time = initialElapsedTime
        advanceTimeBy(initialElapsedTime)
        // Time should be 50 - 40 = 10
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 10))

        job.cancelAndJoin()

        val newElapsedTime = 75_000L
        val newEventTime = 100_000L
        val newState = ChronometerState(fakeTimeSource, newEventTime, isEventInFuture = true)
        val newJob = launch { newState.run() }

        fakeTimeSource.time = newElapsedTime
        advanceTimeBy(newElapsedTime - initialElapsedTime)
        // Time should be 100 - 75 = 25
        assertThat(newState.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 25))

        newJob.cancelAndJoin()
    }

    @Test
    fun textUpdates_afterResettingisEventInFuture() = runTest {
        val initialElapsedTime = 40_000L
        val eventTime = 50_000L
        val state = ChronometerState(fakeTimeSource, eventTime, isEventInFuture = true)
        val job = launch { state.run() }

        fakeTimeSource.time = initialElapsedTime
        advanceTimeBy(initialElapsedTime)
        // Time should be 50 - 40 = 10
        assertThat(state.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 10))

        job.cancelAndJoin()

        val newElapsedTime = 70_000L
        val newState = ChronometerState(fakeTimeSource, eventTime, isEventInFuture = false)
        val newJob = launch { newState.run() }

        fakeTimeSource.time = newElapsedTime
        advanceTimeBy(newElapsedTime - initialElapsedTime)
        // Time should be 70 - 50 = 20
        assertThat(newState.currentTimeText).isEqualTo(formatElapsedTime(/* elapsedSeconds= */ 20))

        newJob.cancelAndJoin()
    }
}

/** A fake implementation of [TimeSource] that allows the caller to set the current time */
class MutableTimeSource(var time: Long = 0L) : TimeSource {
    override fun getCurrentTime(): Long {
        return time
    }
}
