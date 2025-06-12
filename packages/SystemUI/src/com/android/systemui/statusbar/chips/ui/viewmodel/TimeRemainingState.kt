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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.delay

/**
 * Manages state and updates for the duration remaining between now and a given time in the future.
 */
class TimeRemainingState(private val timeSource: TimeSource, private val futureTimeMillis: Long) {
    // Start with the right duration from the outset so we don't use "now" as an initial value.
    private var durationRemaining by
        mutableStateOf(
            calculateDurationRemaining(
                currentTimeMillis = timeSource.getCurrentTime(),
                futureTimeMillis = futureTimeMillis,
            )
        )
    private var startTimeMillis: Long = 0

    /**
     * [Pair] representing the time unit and its value.
     *
     * @property first the string resource ID corresponding to the time unit (e.g., minutes, hours).
     * @property second the time value of the duration unit. Null if time is less than a minute or
     *   past.
     */
    val timeRemainingData by derivedStateOf { getTimeRemainingData(durationRemaining) }

    suspend fun run() {
        startTimeMillis = timeSource.getCurrentTime()
        while (true) {
            val currentTime = timeSource.getCurrentTime()
            durationRemaining =
                calculateDurationRemaining(
                    currentTimeMillis = currentTime,
                    futureTimeMillis = futureTimeMillis,
                )

            // No need to update if duration is more than 1 minute in the past. Because, we will
            // stop displaying anything.
            if (durationRemaining.inWholeMilliseconds < -1.minutes.inWholeMilliseconds) {
                break
            }
            val delaySkewMillis = (currentTime - startTimeMillis) % 1000L
            delay(calculateNextUpdateDelay(durationRemaining) - delaySkewMillis)
        }
    }

    private fun calculateDurationRemaining(
        currentTimeMillis: Long,
        futureTimeMillis: Long,
    ): Duration {
        return (futureTimeMillis - currentTimeMillis).toDuration(DurationUnit.MILLISECONDS)
    }

    private fun calculateNextUpdateDelay(duration: Duration): Long {
        val durationAbsolute = duration.absoluteValue
        return when {
            durationAbsolute.inWholeHours < 1 -> {
                1000 + ((durationAbsolute.inWholeMilliseconds % 1.minutes.inWholeMilliseconds))
            }
            durationAbsolute.inWholeHours < 24 -> {
                1000 + (durationAbsolute.inWholeMilliseconds % 1.hours.inWholeMilliseconds)
            }
            else -> 1000 + (durationAbsolute.inWholeMilliseconds % 24.hours.inWholeMilliseconds)
        }
    }
}

/** Remember and manage the TimeRemainingState */
@Composable
fun rememberTimeRemainingState(futureTimeMillis: Long, timeSource: TimeSource): TimeRemainingState {

    val state =
        remember(timeSource, futureTimeMillis) { TimeRemainingState(timeSource, futureTimeMillis) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, timeSource, futureTimeMillis) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { state.run() }
    }

    return state
}

private fun getTimeRemainingData(duration: Duration): Pair<Int, Long?>? {
    return when {
        duration.inWholeMinutes <= -1 -> null
        duration.inWholeMinutes < 1 -> Pair(com.android.internal.R.string.now_string_shortest, null)
        duration.inWholeHours < 1 ->
            Pair(com.android.internal.R.string.duration_minutes_medium, duration.inWholeMinutes)
        duration.inWholeDays < 1 ->
            Pair(com.android.internal.R.string.duration_hours_medium, duration.inWholeHours)
        else -> null
    }
}

/** Formats the time remaining data into a user-readable string. */
@Composable
fun formatTimeRemainingData(resourcePair: Pair<Int, Long?>): String {
    return resourcePair.let { (resourceId, time) ->
        when (time) {
            null -> stringResource(resourceId)
            else -> stringResource(resourceId, time.toInt())
        }
    }
}
