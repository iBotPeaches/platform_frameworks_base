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
package com.android.systemui.lowlightclock

import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.shared.condition.Condition
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** Condition for monitoring when the device enters and exits lowlight mode. */
class LowLightCondition
@Inject
constructor(
    @Background scope: CoroutineScope,
    private val ambientLightModeMonitor: AmbientLightModeMonitor,
    private val uiEventLogger: UiEventLogger,
) : Condition(scope) {
    override suspend fun start() {
        ambientLightModeMonitor.start { lowLightMode: Int -> onLowLightChanged(lowLightMode) }
    }

    override fun stop() {
        ambientLightModeMonitor.stop()

        // Reset condition met to false.
        updateCondition(false)
    }

    override val startStrategy: Int
        get() = // As this condition keeps the lowlight sensor active, it should only run when
            // needed.
            START_WHEN_NEEDED

    private fun onLowLightChanged(lowLightMode: Int) {
        if (lowLightMode == AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_UNDECIDED) {
            // Ignore undecided mode changes.
            return
        }

        val isLowLight = lowLightMode == AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK
        if (isLowLight == isConditionMet) {
            // No change in condition, don't do anything.
            return
        }
        uiEventLogger.log(
            if (isLowLight) LowLightDockEvent.AMBIENT_LIGHT_TO_DARK
            else LowLightDockEvent.AMBIENT_LIGHT_TO_LIGHT
        )
        updateCondition(isLowLight)
    }
}
