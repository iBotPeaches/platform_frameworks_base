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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.R.string.duration_hours_medium
import com.android.internal.R.string.duration_minutes_medium
import com.android.internal.R.string.now_string_shortest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class TimeRemainingStateTest : SysuiTestCase() {

    private var fakeTimeSource: MutableTimeSource = MutableTimeSource()
    // We need a non-zero start time to advance to. This is needed to ensure `TimeRemainingState` is
    // updated at least once.
    private val startTime = 1.seconds.inWholeMilliseconds

    @Test
    fun timeRemainingState_pastTime() = runTest {
        val state = TimeRemainingState(fakeTimeSource, startTime - 62.seconds.inWholeMilliseconds)
        val job = launch { state.run() }

        fakeTimeSource.time = startTime
        advanceTimeBy(startTime)
        assertThat(state.timeRemainingData).isNull()
        job.cancelAndJoin()
    }

    @Test
    fun timeRemainingState_lessThanOneMinute() = runTest {
        val state = TimeRemainingState(fakeTimeSource, startTime + 59.seconds.inWholeMilliseconds)
        val job = launch { state.run() }

        fakeTimeSource.time = startTime
        advanceTimeBy(startTime)
        assertThat(state.timeRemainingData!!.first).isEqualTo(now_string_shortest)
        job.cancelAndJoin()
    }

    @Test
    fun timeRemainingState_lessThanOneMinuteInThePast() = runTest {
        val state = TimeRemainingState(fakeTimeSource, startTime - 59.seconds.inWholeMilliseconds)
        val job = launch { state.run() }

        fakeTimeSource.time = startTime
        advanceTimeBy(startTime)
        assertThat(state.timeRemainingData!!.first).isEqualTo(now_string_shortest)
        job.cancelAndJoin()
    }

    @Test
    fun timeRemainingState_oneMinute() = runTest {
        val state = TimeRemainingState(fakeTimeSource, startTime + 60.seconds.inWholeMilliseconds)
        val job = launch { state.run() }

        fakeTimeSource.time = startTime
        advanceTimeBy(startTime)
        assertThat(state.timeRemainingData!!.first).isEqualTo(duration_minutes_medium)
        assertThat(state.timeRemainingData!!.second).isEqualTo(1)
        job.cancelAndJoin()
    }

    @Test
    fun timeRemainingState_lessThanOneHour() = runTest {
        val state = TimeRemainingState(fakeTimeSource, startTime + 59.minutes.inWholeMilliseconds)
        val job = launch { state.run() }

        fakeTimeSource.time = startTime
        advanceTimeBy(startTime)
        assertThat(state.timeRemainingData!!.first).isEqualTo(duration_minutes_medium)
        assertThat(state.timeRemainingData!!.second).isEqualTo(59)
        job.cancelAndJoin()
    }

    @Test
    fun timeRemainingState_oneHour() = runTest {
        val state = TimeRemainingState(fakeTimeSource, startTime + 60.minutes.inWholeMilliseconds)
        val job = launch { state.run() }

        fakeTimeSource.time = startTime
        advanceTimeBy(startTime)
        assertThat(state.timeRemainingData!!.first).isEqualTo(duration_hours_medium)
        assertThat(state.timeRemainingData!!.second).isEqualTo(1)
        job.cancelAndJoin()
    }

    @Test
    fun timeRemainingState_betweenOneAndTwoHours() = runTest {
        val state = TimeRemainingState(fakeTimeSource, startTime + 119.minutes.inWholeMilliseconds)
        val job = launch { state.run() }

        fakeTimeSource.time = startTime
        advanceTimeBy(startTime)

        assertThat(state.timeRemainingData).isNotNull()
        assertThat(state.timeRemainingData!!.first).isEqualTo(duration_hours_medium)
        assertThat(state.timeRemainingData!!.second).isEqualTo(1)
        job.cancelAndJoin()
    }

    @Test
    fun timeRemainingState_betweenFiveAndSixHours() = runTest {
        val state = TimeRemainingState(fakeTimeSource, startTime + 320.minutes.inWholeMilliseconds)
        val job = launch { state.run() }

        fakeTimeSource.time = startTime
        advanceTimeBy(startTime)
        assertThat(state.timeRemainingData!!.first).isEqualTo(duration_hours_medium)
        assertThat(state.timeRemainingData!!.second).isEqualTo(5)
        job.cancelAndJoin()
    }

    fun timeRemainingState_moreThan24Hours() = runTest {
        val state =
            TimeRemainingState(fakeTimeSource, startTime + (25 * 60.minutes.inWholeMilliseconds))
        val job = launch { state.run() }

        fakeTimeSource.time = startTime
        advanceTimeBy(startTime)
        assertThat(state.timeRemainingData).isNull()

        job.cancelAndJoin()
    }

    @Test
    fun timeRemainingState_updateFromMinuteToNow() = runTest {
        fakeTimeSource.time = startTime
        val state = TimeRemainingState(fakeTimeSource, startTime + 119.seconds.inWholeMilliseconds)
        val job = launch { state.run() }

        advanceTimeBy(startTime)
        assertThat(state.timeRemainingData!!.first).isEqualTo(duration_minutes_medium)
        assertThat(state.timeRemainingData!!.second).isEqualTo(1)

        fakeTimeSource.time += 59.seconds.inWholeMilliseconds
        advanceTimeBy(59.seconds.inWholeMilliseconds)
        assertThat(state.timeRemainingData!!.first).isEqualTo(duration_minutes_medium)
        assertThat(state.timeRemainingData!!.second).isEqualTo(1)

        fakeTimeSource.time += 1.seconds.inWholeMilliseconds
        advanceTimeBy(1.seconds.inWholeMilliseconds)
        assertThat(state.timeRemainingData!!.first).isEqualTo(now_string_shortest)

        job.cancelAndJoin()
    }

    fun timeRemainingState_updateFromNowToEmpty() = runTest {
        fakeTimeSource.time = startTime
        val state = TimeRemainingState(fakeTimeSource, startTime)
        val job = launch { state.run() }

        advanceTimeBy(startTime)
        assertThat(state.timeRemainingData!!.first).isEqualTo(now_string_shortest)

        fakeTimeSource.time += 62.seconds.inWholeMilliseconds
        advanceTimeBy(62.seconds.inWholeMilliseconds)
        assertThat(state.timeRemainingData).isNull()

        job.cancelAndJoin()
    }

    @Test
    fun timeRemainingState_updateFromHourToMinutes() = runTest {
        fakeTimeSource.time = startTime
        val state = TimeRemainingState(fakeTimeSource, startTime + 119.minutes.inWholeMilliseconds)
        val job = launch { state.run() }

        advanceTimeBy(startTime)
        assertThat(state.timeRemainingData!!.first).isEqualTo(duration_hours_medium)
        assertThat(state.timeRemainingData!!.second).isEqualTo(1)

        fakeTimeSource.time += 59.minutes.inWholeMilliseconds
        advanceTimeBy(59.minutes.inWholeMilliseconds)
        assertThat(state.timeRemainingData!!.first).isEqualTo(duration_hours_medium)
        assertThat(state.timeRemainingData!!.second).isEqualTo(1)

        fakeTimeSource.time += 1.seconds.inWholeMilliseconds
        advanceTimeBy(1.seconds.inWholeMilliseconds)
        assertThat(state.timeRemainingData!!.first).isEqualTo(duration_minutes_medium)
        assertThat(state.timeRemainingData!!.second).isEqualTo(59)

        job.cancelAndJoin()
    }

    @Test
    fun timeRemainingState_showAfterLessThan24Hours() = runTest {
        fakeTimeSource.time = startTime
        val state = TimeRemainingState(fakeTimeSource, startTime + 25.hours.inWholeMilliseconds)
        val job = launch { state.run() }

        advanceTimeBy(startTime)
        assertThat(state.timeRemainingData).isNull()

        fakeTimeSource.time += 1.hours.inWholeMilliseconds + 1.seconds.inWholeMilliseconds
        advanceTimeBy(1.hours.inWholeMilliseconds + 1.seconds.inWholeMilliseconds)
        assertThat(state.timeRemainingData!!.first).isEqualTo(duration_hours_medium)
        assertThat(state.timeRemainingData!!.second).isEqualTo(23)

        job.cancelAndJoin()
    }

    /** A fake implementation of [TimeSource] that allows the caller to set the current time */
    private class MutableTimeSource(var time: Long = 0L) : TimeSource {
        override fun getCurrentTime(): Long {
            return time
        }
    }
}
