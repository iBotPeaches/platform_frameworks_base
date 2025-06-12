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

package com.android.systemui.communal.util

import android.testing.AndroidTestingRunner
import android.view.MotionEvent
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.power.data.repository.fakePowerRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith

@RunWith(AndroidTestingRunner::class)
@SmallTest
class UserTouchActivityNotifierTest : SysuiTestCase() {
    private val kosmos: Kosmos = testKosmos().useUnconfinedTestDispatcher()

    @Test
    fun firstEventTriggersNotify() =
        kosmos.runTest { sendEventAndVerify(0, MotionEvent.ACTION_MOVE, true) }

    @Test
    fun secondEventTriggersRateLimited() =
        kosmos.runTest {
            var eventTime = 0L

            sendEventAndVerify(eventTime, MotionEvent.ACTION_MOVE, true)
            eventTime += 50
            sendEventAndVerify(eventTime, MotionEvent.ACTION_MOVE, false)
            eventTime += USER_TOUCH_ACTIVITY_RATE_LIMIT
            sendEventAndVerify(eventTime, MotionEvent.ACTION_MOVE, true)
        }

    @Test
    fun overridingActionNotifies() =
        kosmos.runTest {
            var eventTime = 0L
            sendEventAndVerify(eventTime, MotionEvent.ACTION_MOVE, true)
            sendEventAndVerify(eventTime, MotionEvent.ACTION_DOWN, true)
            sendEventAndVerify(eventTime, MotionEvent.ACTION_UP, true)
            sendEventAndVerify(eventTime, MotionEvent.ACTION_CANCEL, true)
        }

    private fun sendEventAndVerify(eventTime: Long, action: Int, shouldBeHandled: Boolean) {
        kosmos.fakePowerRepository.userTouchRegistered = false
        val motionEvent = MotionEvent.obtain(0, eventTime, action, 0f, 0f, 0)
        kosmos.userTouchActivityNotifier.notifyActivity(motionEvent)

        if (shouldBeHandled) {
            assertThat(kosmos.fakePowerRepository.userTouchRegistered).isTrue()
        } else {
            assertThat(kosmos.fakePowerRepository.userTouchRegistered).isFalse()
        }
    }
}
