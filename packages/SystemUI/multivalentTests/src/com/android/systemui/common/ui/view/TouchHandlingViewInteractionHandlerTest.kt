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
 *
 */

package com.android.systemui.common.ui.view

import android.testing.TestableLooper
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class TouchHandlingViewInteractionHandlerTest : SysuiTestCase() {

    @Mock private lateinit var postDelayed: (Runnable, Long) -> DisposableHandle
    @Mock private lateinit var onLongPressDetected: (Int, Int) -> Unit
    @Mock private lateinit var onSingleTapDetected: (Int, Int) -> Unit
    @Mock private lateinit var onDoubleTapDetected: () -> Unit

    private lateinit var underTest: TouchHandlingViewInteractionHandler

    private var isAttachedToWindow: Boolean = true
    private var delayedRunnable: Runnable? = null

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        whenever(postDelayed.invoke(any(), any())).thenAnswer { invocation ->
            delayedRunnable = invocation.arguments[0] as Runnable
            DisposableHandle { delayedRunnable = null }
        }

        underTest =
            TouchHandlingViewInteractionHandler(
                context = context,
                postDelayed = postDelayed,
                isAttachedToWindow = { isAttachedToWindow },
                onLongPressDetected = onLongPressDetected,
                onSingleTapDetected = onSingleTapDetected,
                onDoubleTapDetected = onDoubleTapDetected,
                longPressDuration = { ViewConfiguration.getLongPressTimeout().toLong() },
                allowedTouchSlop = ViewConfiguration.getTouchSlop(),
            )
        underTest.isLongPressHandlingEnabled = true
        underTest.isDoubleTapHandlingEnabled = true
    }

    @Test
    fun longPress() = runTest {
        val downX = 123
        val downY = 456
        dispatchTouchEvents(
            MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 123f, 456f, 0),
            MotionEvent.obtain(
                0L,
                0L,
                MotionEvent.ACTION_MOVE,
                123f + ViewConfiguration.getTouchSlop() - 0.1f,
                456f,
                0,
            ),
        )
        delayedRunnable?.run()

        verify(onLongPressDetected).invoke(downX, downY)
        verify(onSingleTapDetected, never()).invoke(anyInt(), anyInt())
    }

    @Test
    fun longPressButFeatureNotEnabled() = runTest {
        underTest.isLongPressHandlingEnabled = false
        dispatchTouchEvents(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 123f, 456f, 0))

        assertThat(delayedRunnable).isNull()
        verify(onLongPressDetected, never()).invoke(anyInt(), anyInt())
        verify(onSingleTapDetected, never()).invoke(anyInt(), anyInt())
    }

    @Test
    fun longPressButViewNotAttached() = runTest {
        isAttachedToWindow = false
        dispatchTouchEvents(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 123f, 456f, 0))
        delayedRunnable?.run()

        verify(onLongPressDetected, never()).invoke(anyInt(), anyInt())
        verify(onSingleTapDetected, never()).invoke(anyInt(), anyInt())
    }

    @Test
    fun draggedTooFarToBeConsideredAlongPress() = runTest {
        dispatchTouchEvents(
            MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 123F, 456F, 0),
            // Drag action within touch slop
            MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 123f, 456f, 0).apply {
                addBatch(0L, 123f + ViewConfiguration.getTouchSlop() + 0.1f, 456f, 0f, 0f, 0)
            },
        )

        assertThat(delayedRunnable).isNull()
        verify(onLongPressDetected, never()).invoke(anyInt(), anyInt())
        verify(onSingleTapDetected, never()).invoke(anyInt(), anyInt())
    }

    @Test
    fun heldDownTooBrieflyToBeConsideredAlongPress() = runTest {
        dispatchTouchEvents(
            MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 123f, 456f, 0),
            MotionEvent.obtain(
                0L,
                ViewConfiguration.getLongPressTimeout() - 1L,
                MotionEvent.ACTION_UP,
                123f,
                456F,
                0,
            ),
        )

        assertThat(delayedRunnable).isNull()
        verify(onLongPressDetected, never()).invoke(anyInt(), anyInt())
        verify(onSingleTapDetected).invoke(123, 456)
    }

    @Test
    fun doubleTap() = runTest {
        val secondTapTime = ViewConfiguration.getDoubleTapTimeout() - 1L
        dispatchTouchEvents(
            MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 123f, 456f, 0),
            MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 123f, 456f, 0),
            MotionEvent.obtain(
                secondTapTime,
                secondTapTime,
                MotionEvent.ACTION_DOWN,
                123f,
                456f,
                0,
            ),
            MotionEvent.obtain(secondTapTime, secondTapTime, MotionEvent.ACTION_UP, 123f, 456f, 0),
        )

        verify(onDoubleTapDetected).invoke()
        assertThat(delayedRunnable).isNull()
        verify(onLongPressDetected, never()).invoke(anyInt(), anyInt())
        verify(onSingleTapDetected, times(2)).invoke(anyInt(), anyInt())
    }

    @Test
    fun doubleTapButFeatureNotEnabled() = runTest {
        underTest.isDoubleTapHandlingEnabled = false

        val secondTapTime = ViewConfiguration.getDoubleTapTimeout() - 1L
        dispatchTouchEvents(
            MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 123f, 456f, 0),
            MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 123f, 456f, 0),
            MotionEvent.obtain(
                secondTapTime,
                secondTapTime,
                MotionEvent.ACTION_DOWN,
                123f,
                456f,
                0,
            ),
            MotionEvent.obtain(secondTapTime, secondTapTime, MotionEvent.ACTION_UP, 123f, 456f, 0),
        )

        verify(onDoubleTapDetected, never()).invoke()
        assertThat(delayedRunnable).isNull()
        verify(onLongPressDetected, never()).invoke(anyInt(), anyInt())
        verify(onSingleTapDetected, times(2)).invoke(anyInt(), anyInt())
    }

    @Test
    fun tapIntoLongPress() = runTest {
        val secondTapTime = ViewConfiguration.getDoubleTapTimeout() - 1L
        dispatchTouchEvents(
            MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 123f, 456f, 0),
            MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 123f, 456f, 0),
            MotionEvent.obtain(
                secondTapTime,
                secondTapTime,
                MotionEvent.ACTION_DOWN,
                123f,
                456f,
                0,
            ),
            MotionEvent.obtain(
                secondTapTime + ViewConfiguration.getLongPressTimeout() + 1L,
                secondTapTime + ViewConfiguration.getLongPressTimeout() + 1L,
                MotionEvent.ACTION_MOVE,
                123f + ViewConfiguration.getTouchSlop() - 0.1f,
                456f,
                0,
            ),
        )
        delayedRunnable?.run()

        verify(onDoubleTapDetected, never()).invoke()
        verify(onSingleTapDetected).invoke(anyInt(), anyInt())
        verify(onLongPressDetected).invoke(anyInt(), anyInt())
    }

    @Test
    fun tapIntoDownHoldTooBrieflyToBeConsideredLongPress() = runTest {
        val secondTapTime = ViewConfiguration.getDoubleTapTimeout() - 1L
        dispatchTouchEvents(
            MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 123f, 456f, 0),
            MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 123f, 456f, 0),
            MotionEvent.obtain(
                secondTapTime,
                secondTapTime,
                MotionEvent.ACTION_DOWN,
                123f,
                456f,
                0,
            ),
            MotionEvent.obtain(
                secondTapTime + ViewConfiguration.getLongPressTimeout() + 1L,
                secondTapTime + ViewConfiguration.getLongPressTimeout() + 1L,
                MotionEvent.ACTION_UP,
                123f,
                456f,
                0,
            ),
        )
        delayedRunnable?.run()

        verify(onDoubleTapDetected, never()).invoke()
        verify(onLongPressDetected, never()).invoke(anyInt(), anyInt())
        verify(onSingleTapDetected, times(2)).invoke(anyInt(), anyInt())
    }

    @Test
    fun tapIntoDrag() = runTest {
        val secondTapTime = ViewConfiguration.getDoubleTapTimeout() - 1L
        dispatchTouchEvents(
            MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 123f, 456f, 0),
            MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 123f, 456f, 0),
            MotionEvent.obtain(
                secondTapTime,
                secondTapTime,
                MotionEvent.ACTION_DOWN,
                123f,
                456f,
                0,
            ),
            // Drag event within touch slop
            MotionEvent.obtain(secondTapTime, secondTapTime, MotionEvent.ACTION_MOVE, 123f, 456f, 0)
                .apply {
                    addBatch(
                        secondTapTime,
                        123f + ViewConfiguration.getTouchSlop() + 0.1f,
                        456f,
                        0f,
                        0f,
                        0,
                    )
                },
        )
        delayedRunnable?.run()

        verify(onDoubleTapDetected, never()).invoke()
        verify(onLongPressDetected, never()).invoke(anyInt(), anyInt())
        verify(onSingleTapDetected).invoke(anyInt(), anyInt())
    }

    @Test
    fun doubleTapOutOfAllowableSlop() = runTest {
        val secondTapTime = ViewConfiguration.getDoubleTapTimeout() - 1L
        val scaledDoubleTapSlop = ViewConfiguration.get(context).scaledDoubleTapSlop
        dispatchTouchEvents(
            MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 123f, 456f, 0),
            MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 123f, 456f, 0),
            MotionEvent.obtain(
                secondTapTime,
                secondTapTime,
                MotionEvent.ACTION_DOWN,
                123f + scaledDoubleTapSlop + 0.1f,
                456f + scaledDoubleTapSlop + 0.1f,
                0,
            ),
            MotionEvent.obtain(
                secondTapTime,
                secondTapTime,
                MotionEvent.ACTION_UP,
                123f + scaledDoubleTapSlop + 0.1f,
                456f + scaledDoubleTapSlop + 0.1f,
                0,
            ),
        )

        verify(onDoubleTapDetected, never()).invoke()
        assertThat(delayedRunnable).isNull()
        verify(onLongPressDetected, never()).invoke(anyInt(), anyInt())
        verify(onSingleTapDetected, times(2)).invoke(anyInt(), anyInt())
    }

    private fun dispatchTouchEvents(vararg events: MotionEvent) {
        events.forEach { event -> underTest.onTouchEvent(event) }
    }
}
