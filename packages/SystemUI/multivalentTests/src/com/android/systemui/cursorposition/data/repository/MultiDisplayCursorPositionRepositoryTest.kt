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

package com.android.systemui.cursorposition.data.repository

import android.os.Handler
import android.testing.TestableLooper
import android.testing.TestableLooper.RunWithLooper
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.TYPE_EXTERNAL
import android.view.Display.TYPE_INTERNAL
import android.view.InputDevice.SOURCE_MOUSE
import android.view.InputDevice.SOURCE_TOUCHPAD
import android.view.MotionEvent
import androidx.test.filters.SmallTest
import com.android.app.displaylib.PerDisplayInstanceRepositoryImpl
import com.android.systemui.SysuiTestCase
import com.android.systemui.cursorposition.data.model.CursorPosition
import com.android.systemui.cursorposition.data.repository.SingleDisplayCursorPositionRepositoryImpl.Companion.defaultInputEventListenerBuilder
import com.android.systemui.cursorposition.domain.data.repository.TestCursorPositionRepositoryInstanceProvider
import com.android.systemui.display.data.repository.display
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.display.data.repository.fakeDisplayInstanceLifecycleManager
import com.android.systemui.display.data.repository.perDisplayDumpHelper
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.shared.system.InputChannelCompat
import com.android.systemui.shared.system.InputMonitorCompat
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@RunWithLooper
@kotlinx.coroutines.ExperimentalCoroutinesApi
class MultiDisplayCursorPositionRepositoryTest(private val cursorEventSource: Int) :
    SysuiTestCase() {

    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()
    private lateinit var underTest: MultiDisplayCursorPositionRepository
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val displayRepository = kosmos.displayRepository
    private val displayLifecycleManager = kosmos.fakeDisplayInstanceLifecycleManager

    private lateinit var listener: InputChannelCompat.InputEventListener
    private var emittedCursorPosition: CursorPosition? = null

    @Mock private lateinit var inputMonitor: InputMonitorCompat
    @Mock private lateinit var inputReceiver: InputChannelCompat.InputEventReceiver

    private val x = 100f
    private val y = 200f

    private lateinit var testableLooper: TestableLooper

    @Before
    fun setup() {
        testableLooper = TestableLooper.get(this)
        val testHandler = Handler(testableLooper.looper)
        whenever(inputMonitor.getInputReceiver(any(), any(), any())).thenReturn(inputReceiver)
        displayLifecycleManager.displayIds.value = setOf(DEFAULT_DISPLAY, DISPLAY_2)

        val cursorPerDisplayRepository =
            PerDisplayInstanceRepositoryImpl(
                debugName = "testCursorPositionPerDisplayInstanceRepository",
                instanceProvider =
                    TestCursorPositionRepositoryInstanceProvider(
                        testHandler,
                        { channel ->
                            listener = defaultInputEventListenerBuilder.build(channel)
                            listener
                        },
                    ) { _: String, _: Int ->
                        inputMonitor
                    },
                displayLifecycleManager,
                kosmos.backgroundScope,
                displayRepository,
                kosmos.perDisplayDumpHelper,
            )

        underTest =
            MultiDisplayCursorPositionRepositoryImpl(
                displayRepository,
                backgroundScope = kosmos.backgroundScope,
                cursorPerDisplayRepository,
            )
    }

    @Test
    fun getCursorPositionFromDefaultDisplay() = setUpAndRunTest {
        val event = getMotionEvent(x, y, 0)
        listener.onInputEvent(event)

        assertThat(emittedCursorPosition).isEqualTo(CursorPosition(x, y, 0))
    }

    @Test
    fun getCursorPositionFromAdditionDisplay() = setUpAndRunTest {
        addDisplay(id = DISPLAY_2, type = TYPE_EXTERNAL)

        val event = getMotionEvent(x, y, DISPLAY_2)
        listener.onInputEvent(event)

        assertThat(emittedCursorPosition).isEqualTo(CursorPosition(x, y, DISPLAY_2))
    }

    @Test
    fun noCursorPositionFromRemovedDisplay() = setUpAndRunTest {
        addDisplay(id = DISPLAY_2, type = TYPE_EXTERNAL)
        removeDisplay(DISPLAY_2)

        val event = getMotionEvent(x, y, DISPLAY_2)
        listener.onInputEvent(event)

        assertThat(emittedCursorPosition).isEqualTo(null)
    }

    @Test
    fun disposeInputMonitorAndInputReceiver() = setUpAndRunTest {
        addDisplay(DISPLAY_2, TYPE_EXTERNAL)
        removeDisplay(DISPLAY_2)

        verify(inputMonitor).dispose()
        verify(inputReceiver).dispose()
    }

    private fun setUpAndRunTest(block: suspend () -> Unit) =
        kosmos.runTest {
            // Add default display before creating cursor repository
            displayRepository.addDisplays(display(id = DEFAULT_DISPLAY, type = TYPE_INTERNAL))

            backgroundScope.launch {
                underTest.cursorPositions.collect { emittedCursorPosition = it }
            }
            // Run all tasks received by TestHandler to create input monitors
            testableLooper.processAllMessages()

            block()
        }

    private suspend fun addDisplay(id: Int, type: Int) {
        displayRepository.addDisplays(display(id = id, type = type))
        testableLooper.processAllMessages()
    }

    private suspend fun removeDisplay(id: Int) {
        displayRepository.removeDisplay(id)
        testableLooper.processAllMessages()
    }

    private fun getMotionEvent(x: Float, y: Float, displayId: Int): MotionEvent {
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, x, y, 0)
        event.source = cursorEventSource
        event.displayId = displayId
        return event
    }

    private companion object {
        const val DISPLAY_2 = DEFAULT_DISPLAY + 1

        @JvmStatic
        @Parameters(name = "source = {0}")
        fun data(): List<Int> {
            return listOf(SOURCE_MOUSE, SOURCE_TOUCHPAD)
        }
    }
}
