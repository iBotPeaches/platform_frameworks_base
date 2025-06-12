/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.app.AlarmManager
import android.content.Intent
import android.provider.AlarmClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.coroutines.collectValues
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.activityStarter
import com.android.systemui.statusbar.policy.NextAlarmController.NextAlarmChangeCallback
import com.android.systemui.statusbar.policy.nextAlarmController
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.argThat
import com.android.systemui.util.mockito.mock
import com.android.systemui.util.mockito.withArgCaptor
import com.google.common.truth.Truth.assertThat
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatcher
import org.mockito.Mockito.verify

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ShadeHeaderClockInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val activityStarter = kosmos.activityStarter
    private val nextAlarmController = kosmos.nextAlarmController

    private val underTest = kosmos.shadeHeaderClockInteractor

    @Test
    fun launchClockActivity_default() =
        testScope.runTest {
            underTest.launchClockActivity()
            verify(activityStarter)
                .postStartActivityDismissingKeyguard(
                    argThat(IntentMatcherAction(AlarmClock.ACTION_SHOW_ALARMS)),
                    any(),
                )
        }

    @Test
    fun launchClockActivity_nextAlarmIntent() =
        testScope.runTest {
            val callback =
                withArgCaptor<NextAlarmChangeCallback> {
                    verify(nextAlarmController).addCallback(capture())
                }
            callback.onNextAlarmChanged(AlarmManager.AlarmClockInfo(1L, mock()))

            underTest.launchClockActivity()
            verify(activityStarter).postStartActivityDismissingKeyguard(any())
        }

    @Test
    fun onTimezoneOrLocaleChanged_localeAndTimezoneChanged_emitsForEach() =
        testScope.runTest {
            val timeZoneOrLocaleChanges by collectValues(underTest.onTimezoneOrLocaleChanged)

            sendIntentActionBroadcast(Intent.ACTION_TIMEZONE_CHANGED)
            sendIntentActionBroadcast(Intent.ACTION_LOCALE_CHANGED)
            sendIntentActionBroadcast(Intent.ACTION_LOCALE_CHANGED)
            sendIntentActionBroadcast(Intent.ACTION_TIMEZONE_CHANGED)

            assertThat(timeZoneOrLocaleChanges).hasSize(4)
        }

    @Test
    fun onTimezoneOrLocaleChanged_timeChanged_doesNotEmit() =
        testScope.runTest {
            val timeZoneOrLocaleChanges by collectValues(underTest.onTimezoneOrLocaleChanged)
            assertThat(timeZoneOrLocaleChanges).hasSize(1)

            sendIntentActionBroadcast(Intent.ACTION_TIME_CHANGED)
            sendIntentActionBroadcast(Intent.ACTION_TIME_TICK)

            // Expect only 1 event to have been emitted onStart, but no more.
            assertThat(timeZoneOrLocaleChanges).hasSize(1)
        }

    @Test
    fun currentTime_timeChanged() =
        testScope.runTest {
            val currentTime by collectLastValue(underTest.currentTime)

            sendIntentActionBroadcast(Intent.ACTION_TIME_CHANGED)
            val earlierTime = checkNotNull(currentTime)

            advanceTimeBy(3.seconds)
            runCurrent()

            sendIntentActionBroadcast(Intent.ACTION_TIME_CHANGED)
            val laterTime = checkNotNull(currentTime)

            assertThat(differenceBetween(laterTime, earlierTime)).isEqualTo(3.seconds)
        }

    @Test
    fun currentTime_timeTicked() =
        testScope.runTest {
            val currentTime by collectLastValue(underTest.currentTime)

            sendIntentActionBroadcast(Intent.ACTION_TIME_TICK)
            val earlierTime = checkNotNull(currentTime)

            advanceTimeBy(7.seconds)
            runCurrent()

            sendIntentActionBroadcast(Intent.ACTION_TIME_TICK)
            val laterTime = checkNotNull(currentTime)

            assertThat(differenceBetween(laterTime, earlierTime)).isEqualTo(7.seconds)
        }

    private fun differenceBetween(date1: Date, date2: Date): Duration {
        return (date1.time - date2.time).milliseconds
    }

    private fun TestScope.sendIntentActionBroadcast(intentAction: String) {
        kosmos.broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, Intent(intentAction))
        runCurrent()
    }
}

private class IntentMatcherAction(private val action: String) : ArgumentMatcher<Intent> {
    override fun matches(argument: Intent?): Boolean {
        return argument?.action == action
    }
}
