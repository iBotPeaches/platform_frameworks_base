/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.shade.domain.interactor

import android.content.Intent
import android.content.IntentFilter
import android.os.UserHandle
import android.provider.AlarmClock
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.shade.data.repository.ShadeHeaderClockRepository
import com.android.systemui.util.kotlin.emitOnStart
import com.android.systemui.util.time.SystemClock
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@SysUISingleton
class ShadeHeaderClockInteractor
@Inject
constructor(
    private val repository: ShadeHeaderClockRepository,
    private val activityStarter: ActivityStarter,
    private val broadcastDispatcher: BroadcastDispatcher,
    private val systemClock: SystemClock,
) {
    /** [Flow] that emits `Unit` whenever the timezone or locale has changed. */
    val onTimezoneOrLocaleChanged: Flow<Unit> =
        broadcastFlowForActions(Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED)
            .emitOnStart()

    /** [Flow] that emits the current `Date` every minute, or when the system time has changed. */
    val currentTime: Flow<Date> =
        broadcastFlowForActions(Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED)
            .emitOnStart()
            .map { Date(systemClock.currentTimeMillis()) }

    /** Launch the clock activity. */
    fun launchClockActivity() {
        val nextAlarmIntent = repository.nextAlarmIntent
        if (nextAlarmIntent != null) {
            activityStarter.postStartActivityDismissingKeyguard(nextAlarmIntent)
        } else {
            activityStarter.postStartActivityDismissingKeyguard(
                Intent(AlarmClock.ACTION_SHOW_ALARMS),
                0,
            )
        }
    }

    /**
     * Returns a `Flow` that, when collected, emits `Unit` whenever a broadcast matching one of the
     * given [actionsToFilter] is received.
     */
    private fun broadcastFlowForActions(
        vararg actionsToFilter: String,
        user: UserHandle = UserHandle.SYSTEM,
    ): Flow<Unit> {
        return broadcastDispatcher.broadcastFlow(
            filter = IntentFilter().apply { actionsToFilter.forEach(::addAction) },
            user = user,
        )
    }
}
