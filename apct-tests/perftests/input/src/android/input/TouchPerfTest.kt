/*
 * Copyright 2025 The Android Open Source Project
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

package android.input

import android.cts.input.EventVerifier
import android.perftests.utils.PerfStatusReporter
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.UinputTouchScreen
import com.android.cts.input.VirtualDisplayActivityScenario
import com.android.cts.input.inputeventmatchers.withMotionAction
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TouchPerfTest {
    @get:Rule val testName = TestName()
    @get:Rule val perfStatusReporter = PerfStatusReporter()
    @get:Rule
    val virtualDisplayRule = VirtualDisplayActivityScenario.Rule<CaptureEventActivity>(testName)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun testDownMoveUp() {
        UinputTouchScreen(instrumentation, virtualDisplayRule.virtualDisplay.display).use {
            touchScreen ->
            val verifier = EventVerifier(virtualDisplayRule.activity::getInputEvent)
            val state = perfStatusReporter.benchmarkState

            while (state.keepRunning()) {
                val x = 100
                val y = 100

                val pointer = touchScreen.touchDown(x, y)
                verifier.assertReceivedMotion(withMotionAction(ACTION_DOWN))

                pointer.moveTo(x + 1, y + 1)
                verifier.assertReceivedMotion(withMotionAction(ACTION_MOVE))

                pointer.lift()
                verifier.assertReceivedMotion(withMotionAction(ACTION_UP))
            }
        }
    }
}
