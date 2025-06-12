/*
 * Copyright 2024 The Android Open Source Project
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

package android.hardware.input

import android.content.Context
import android.content.ContextWrapper
import android.platform.test.annotations.Presubmit
import android.platform.test.flag.junit.SetFlagsRule
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.android.test.input.MockInputManagerRule
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner

/**
 * Tests for [InputManager.KeyGestureEventHandler].
 *
 * Build/Install/Run: atest InputTests:KeyGestureEventHandlerTest
 */
@Presubmit
@RunWith(MockitoJUnitRunner::class)
class KeyGestureEventHandlerTest {

    companion object {
        const val DEVICE_ID = 1
        val HOME_GESTURE_EVENT =
            KeyGestureEvent.Builder()
                .setDeviceId(DEVICE_ID)
                .setKeycodes(intArrayOf(KeyEvent.KEYCODE_H))
                .setModifierState(KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON)
                .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_HOME)
                .build()
        val BACK_GESTURE_EVENT =
            KeyGestureEvent.Builder()
                .setDeviceId(DEVICE_ID)
                .setKeycodes(intArrayOf(KeyEvent.KEYCODE_DEL))
                .setModifierState(KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON)
                .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_BACK)
                .build()
    }

    @get:Rule val rule = SetFlagsRule()
    @get:Rule val inputManagerRule = MockInputManagerRule()

    private var registeredListener: IKeyGestureHandler? = null
    private lateinit var context: Context
    private lateinit var inputManager: InputManager

    @Before
    fun setUp() {
        context = Mockito.spy(ContextWrapper(ApplicationProvider.getApplicationContext()))
        inputManager = InputManager(context)
        `when`(context.getSystemService(Mockito.eq(Context.INPUT_SERVICE))).thenReturn(inputManager)

        // Handle key gesture handler registration.
        doAnswer {
                val listener = it.getArgument(1) as IKeyGestureHandler
                if (
                    registeredListener != null &&
                        registeredListener!!.asBinder() != listener.asBinder()
                ) {
                    // There can only be one registered key gesture handler per process.
                    fail("Trying to register a new listener when one already exists")
                }
                registeredListener = listener
                null
            }
            .`when`(inputManagerRule.mock)
            .registerKeyGestureHandler(Mockito.any(), Mockito.any())

        // Handle key gesture handler being unregistered.
        doAnswer {
                val listener = it.getArgument(0) as IKeyGestureHandler
                if (
                    registeredListener == null ||
                        registeredListener!!.asBinder() != listener.asBinder()
                ) {
                    fail("Trying to unregister a listener that is not registered")
                }
                registeredListener = null
                null
            }
            .`when`(inputManagerRule.mock)
            .unregisterKeyGestureHandler(Mockito.any())
    }

    private fun handleKeyGestureEvent(event: KeyGestureEvent) {
        val eventToSend = AidlKeyGestureEvent()
        eventToSend.deviceId = event.deviceId
        eventToSend.keycodes = event.keycodes
        eventToSend.modifierState = event.modifierState
        eventToSend.gestureType = event.keyGestureType
        eventToSend.action = event.action
        eventToSend.displayId = event.displayId
        eventToSend.flags = event.flags
        registeredListener!!.handleKeyGesture(eventToSend, null)
    }

    @Test
    fun testHandlerHasCorrectGestureNotified() {
        var callbackCount = 0

        // Add a key gesture event listener
        inputManager.registerKeyGestureEventHandler(
            listOf(KeyGestureEvent.KEY_GESTURE_TYPE_HOME)
        ) { event, _ ->
            assertEquals(HOME_GESTURE_EVENT, event)
            callbackCount++
        }

        // Request handling for key gesture event will notify the handler.
        handleKeyGestureEvent(HOME_GESTURE_EVENT)
        assertEquals(1, callbackCount)
    }

    @Test
    fun testAddingHandlersRegistersInternalCallbackHandler() {
        // Set up two callbacks.
        val callback1 = InputManager.KeyGestureEventHandler { _, _ -> }
        val callback2 = InputManager.KeyGestureEventHandler { _, _ -> }

        assertNull(registeredListener)

        // Adding the handler should register the callback with InputManagerService.
        inputManager.registerKeyGestureEventHandler(
            listOf(KeyGestureEvent.KEY_GESTURE_TYPE_HOME),
            callback1,
        )
        assertNotNull(registeredListener)

        // Adding another handler should not register new internal listener.
        val currListener = registeredListener
        inputManager.registerKeyGestureEventHandler(
            listOf(KeyGestureEvent.KEY_GESTURE_TYPE_BACK),
            callback2,
        )
        assertEquals(currListener, registeredListener)
    }

    @Test
    fun testRemovingHandlersUnregistersInternalCallbackHandler() {
        // Set up two callbacks.
        val callback1 = InputManager.KeyGestureEventHandler { _, _ -> }
        val callback2 = InputManager.KeyGestureEventHandler { _, _ -> }

        inputManager.registerKeyGestureEventHandler(
            listOf(KeyGestureEvent.KEY_GESTURE_TYPE_HOME),
            callback1,
        )
        inputManager.registerKeyGestureEventHandler(
            listOf(KeyGestureEvent.KEY_GESTURE_TYPE_BACK),
            callback2,
        )

        // Only removing all handlers should remove the internal callback
        inputManager.unregisterKeyGestureEventHandler(callback1)
        assertNotNull(registeredListener)
        inputManager.unregisterKeyGestureEventHandler(callback2)
        assertNull(registeredListener)
    }

    @Test
    fun testMultipleHandlers() {
        // Set up two callbacks.
        var callbackCount1 = 0
        var callbackCount2 = 0
        // Handler 1 captures all home gestures
        val callback1 =
            InputManager.KeyGestureEventHandler { event, _ ->
                callbackCount1++
                assertEquals(KeyGestureEvent.KEY_GESTURE_TYPE_HOME, event.keyGestureType)
            }
        // Handler 2 captures all back gestures
        val callback2 =
            InputManager.KeyGestureEventHandler { event, _ ->
                callbackCount2++
                assertEquals(KeyGestureEvent.KEY_GESTURE_TYPE_BACK, event.keyGestureType)
            }

        // Add both key gesture event handlers
        inputManager.registerKeyGestureEventHandler(
            listOf(KeyGestureEvent.KEY_GESTURE_TYPE_HOME),
            callback1,
        )
        inputManager.registerKeyGestureEventHandler(
            listOf(KeyGestureEvent.KEY_GESTURE_TYPE_BACK),
            callback2,
        )

        // Request handling for home key gesture event, should notify only callback1
        handleKeyGestureEvent(HOME_GESTURE_EVENT)
        assertEquals(1, callbackCount1)
        assertEquals(0, callbackCount2)

        // Request handling for back key gesture event, should notify only callback2
        handleKeyGestureEvent(BACK_GESTURE_EVENT)
        assertEquals(1, callbackCount1)
        assertEquals(1, callbackCount2)

        inputManager.unregisterKeyGestureEventHandler(callback1)

        // Request handling for home key gesture event, should not trigger callback2
        handleKeyGestureEvent(HOME_GESTURE_EVENT)
        assertEquals(1, callbackCount1)
        assertEquals(1, callbackCount2)
    }

    @Test
    fun testUnableToRegisterSameHandlerTwice() {
        val handler = InputManager.KeyGestureEventHandler { _, _ -> }

        inputManager.registerKeyGestureEventHandler(
            listOf(KeyGestureEvent.KEY_GESTURE_TYPE_HOME),
            handler,
        )

        assertThrows(IllegalArgumentException::class.java) {
            inputManager.registerKeyGestureEventHandler(
                listOf(KeyGestureEvent.KEY_GESTURE_TYPE_BACK),
                handler,
            )
        }
    }

    @Test
    fun testUnableToRegisterSameGestureTwice() {
        val handler1 = InputManager.KeyGestureEventHandler { _, _ -> }
        val handler2 = InputManager.KeyGestureEventHandler { _, _ -> }

        inputManager.registerKeyGestureEventHandler(
            listOf(KeyGestureEvent.KEY_GESTURE_TYPE_HOME),
            handler1,
        )

        assertThrows(IllegalArgumentException::class.java) {
            inputManager.registerKeyGestureEventHandler(
                listOf(KeyGestureEvent.KEY_GESTURE_TYPE_HOME),
                handler2,
            )
        }
    }
}
