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

package com.android.systemui.communal.posturing.domain.interactor

import com.android.systemui.communal.posturing.data.repository.posturingRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.util.sensors.asyncSensorManager
import com.android.systemui.util.time.systemClock

val Kosmos.posturingInteractor by
    Kosmos.Fixture<PosturingInteractor> {
        PosturingInteractor(
            repository = posturingRepository,
            asyncSensorManager = asyncSensorManager,
            applicationScope = applicationCoroutineScope,
            bgDispatcher = testDispatcher,
            logBuffer = logcatLogBuffer("PosturingInteractor"),
            clock = systemClock,
        )
    }

fun Kosmos.advanceTimeBySlidingWindowAndRun() {
    advanceTimeBy(PosturingInteractor.SLIDING_WINDOW_DURATION)
    runCurrent()
}
