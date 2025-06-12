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

import android.annotation.ElapsedRealtimeLong
import android.text.format.DateUtils.formatElapsedTime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay

/** Platform-optimized interface for getting current time */
fun interface TimeSource {
    fun getCurrentTime(): Long
}

/**
 * Holds and manages the state for a Chronometer, which shows a timer in a format like "MM:SS" or
 * "H:MM:SS".
 *
 * If [isEventInFuture] is false, then this Chronometer is counting up from an event that started in
 * the past, like a phone call that was answered. [eventTimeMillis] represents the time the event
 * started and the timer will tick up: 04:00, 04:01, ... No timer is shown if [eventTimeMillis] is
 * in the future and [isEventInFuture] is false.
 *
 * If [isEventInFuture] is true, then this Chronometer is counting down to an event that will occur
 * in the future, like a future meeting. [eventTimeMillis] represents the time the event will occur
 * and the timer will tick down: 04:00, 03:59, ... No timer is shown if [eventTimeMillis] is in the
 * past and [isEventInFuture] is true.
 */
class ChronometerState(
    private val timeSource: TimeSource,
    @ElapsedRealtimeLong private val eventTimeMillis: Long,
    private val isEventInFuture: Boolean,
) {
    private var currentTimeMillis by mutableLongStateOf(timeSource.getCurrentTime())
    private val elapsedTimeMillis: Long
        get() =
            if (isEventInFuture) {
                eventTimeMillis - currentTimeMillis
            } else {
                currentTimeMillis - eventTimeMillis
            }

    /**
     * The current timer string in a format like "MM:SS" or "H:MM:SS", or null if we shouldn't show
     * the timer string.
     */
    val currentTimeText: String? by derivedStateOf {
        if (elapsedTimeMillis < 0) {
            null
        } else {
            formatElapsedTime(elapsedTimeMillis / 1000)
        }
    }

    suspend fun run() {
        while (true) {
            currentTimeMillis = timeSource.getCurrentTime()
            val delaySkewMillis = (eventTimeMillis - currentTimeMillis).absoluteValue % 1000L
            delay(1000L - delaySkewMillis)
        }
    }
}

/** Remember and manage the ChronometerState */
@Composable
fun rememberChronometerState(
    eventTimeMillis: Long,
    isCountDown: Boolean,
    timeSource: TimeSource,
): ChronometerState {
    val state =
        remember(timeSource, eventTimeMillis, isCountDown) {
            ChronometerState(timeSource, eventTimeMillis, isCountDown)
        }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, timeSource, eventTimeMillis) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { state.run() }
    }

    return state
}
