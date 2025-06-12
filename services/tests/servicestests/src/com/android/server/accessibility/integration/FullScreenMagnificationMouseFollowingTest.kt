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
package com.android.server.accessibility.integration

import android.Manifest
import android.accessibility.cts.common.AccessibilityDumpOnFailureRule
import android.accessibility.cts.common.InstrumentedAccessibilityService
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.MagnificationConfig
import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.companion.virtual.VirtualDeviceManager
import android.graphics.PointF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.input.InputManager
import android.hardware.input.VirtualMouse
import android.hardware.input.VirtualMouseConfig
import android.hardware.input.VirtualMouseRelativeEvent
import android.os.Handler
import android.os.Looper
import android.os.OutcomeReceiver
import android.platform.test.annotations.RequiresFlagsEnabled
import android.testing.PollingCheck
import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent
import android.virtualdevice.cts.common.VirtualDeviceRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.accessibility.Flags
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

// Convenient extension functions for float.
private const val EPS = 0.00001f
private fun Float.nearEq(other: Float) = abs(this - other) < EPS
private fun PointF.nearEq(other: PointF) = this.x.nearEq(other.x) && this.y.nearEq(other.y)

/** End-to-end tests for full screen magnification following mouse cursor. */
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_MAGNIFICATION_FOLLOWS_MOUSE_WITH_POINTER_MOTION_FILTER)
class FullScreenMagnificationMouseFollowingTest {

    private lateinit var instrumentation: Instrumentation
    private lateinit var uiAutomation: UiAutomation

    private val magnificationAccessibilityServiceRule =
        InstrumentedAccessibilityServiceTestRule<TestMagnificationAccessibilityService>(
            TestMagnificationAccessibilityService::class.java, false
        )
    private lateinit var service: TestMagnificationAccessibilityService

    // virtualDeviceRule tears down `virtualDevice` and `virtualDisplay`.
    // Note that CheckFlagsRule is a part of VirtualDeviceRule. See its javadoc.
    val virtualDeviceRule: VirtualDeviceRule =
        VirtualDeviceRule.withAdditionalPermissions(Manifest.permission.MANAGE_ACTIVITY_TASKS)
    private lateinit var virtualDevice: VirtualDeviceManager.VirtualDevice
    private lateinit var virtualDisplay: VirtualDisplay

    // Once created, it's our responsibility to close the mouse.
    private lateinit var virtualMouse: VirtualMouse

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain.outerRule(virtualDeviceRule)
            .around(magnificationAccessibilityServiceRule)
            .around(AccessibilityDumpOnFailureRule())

    @Before
    fun setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        uiAutomation =
            instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        uiAutomation.serviceInfo =
            uiAutomation.serviceInfo!!.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }

        prepareVirtualDevices()

        launchTestActivityFullscreen(virtualDisplay.display.displayId)

        service = magnificationAccessibilityServiceRule.enableService()
        service.observingDisplayId = virtualDisplay.display.displayId
    }

    @After
    fun cleanUp() {
        if (this::virtualMouse.isInitialized) {
            virtualMouse.close()
        }
    }

    // Note on continuous movement:
    // Assume that the entire display is magnified, and the zoom level is z.
    // In continuous movement, mouse speed relative to the unscaled physical display is the same as
    // unmagnified speed. While, when a cursor moves from the left edge to the right edge of the
    // screen, the magnification center moves from the left bound to the right bound, which is
    // (display width) * (z - 1) / z.
    //
    // Similarly, when the mouse cursor moves by d in unscaled, display coordinates,
    // the magnification center moves by d * (z - 1) / z.

    @Test
    fun testContinuous_toBottomRight() {
        ensureMouseAtCenter()

        val controller = service.getMagnificationController(virtualDisplay.display.displayId)

        scaleTo(controller, 2f)
        assertMagnification(controller, scale = 2f, CENTER_X, CENTER_Y)

        // Move cursor by (10, 15)
        // This will move magnification center by (5, 7.5)
        sendMouseMove(10f, 15f)
        assertCursorLocation(CENTER_X + 10, CENTER_Y + 15)
        assertMagnification(controller, scale = 2f, CENTER_X + 5, CENTER_Y + 7.5f)

        // Move cursor to the rest of the way to the edge.
        sendMouseMove(DISPLAY_WIDTH - 10, DISPLAY_HEIGHT - 15)
        assertCursorLocation(DISPLAY_WIDTH - 1, DISPLAY_HEIGHT - 1)
        assertMagnification(controller, scale = 2f, DISPLAY_WIDTH * 3 / 4, DISPLAY_HEIGHT * 3 / 4)

        // Move cursor further won't move the magnification.
        sendMouseMove(100f, 100f)
        assertCursorLocation(DISPLAY_WIDTH - 1, DISPLAY_HEIGHT - 1)
    }

    @Test
    fun testContinuous_toTopLeft() {
        ensureMouseAtCenter()

        val controller = service.getMagnificationController(virtualDisplay.display.displayId)

        scaleTo(controller, 3f)
        assertMagnification(controller, scale = 3f, CENTER_X, CENTER_Y)

        // Move cursor by (-30, -15)
        // This will move magnification center by (-20, -10)
        sendMouseMove(-30f, -15f)
        assertCursorLocation(CENTER_X - 30, CENTER_Y - 15)
        assertMagnification(controller, scale = 3f, CENTER_X - 20, CENTER_Y - 10)

        // Move cursor to the rest of the way to the edge.
        sendMouseMove(-CENTER_X + 30, -CENTER_Y + 15)
        assertCursorLocation(0f, 0f)
        assertMagnification(controller, scale = 3f, DISPLAY_WIDTH / 6, DISPLAY_HEIGHT / 6)

        // Move cursor further won't move the magnification.
        sendMouseMove(-100f, -100f)
        assertCursorLocation(0f, 0f)
        assertMagnification(controller, scale = 3f, DISPLAY_WIDTH / 6, DISPLAY_HEIGHT / 6)
    }

    private fun ensureMouseAtCenter() {
        val displayCenter = PointF(320f, 240f)
        val cursorLocation = virtualMouse.cursorPosition
        if (!cursorLocation.nearEq(displayCenter)) {
            sendMouseMove(displayCenter.x - cursorLocation.x, displayCenter.y - cursorLocation.y)
            assertCursorLocation(320f, 240f)
        }
    }

    private fun sendMouseMove(dx: Float, dy: Float) {
        virtualMouse.sendRelativeEvent(
            VirtualMouseRelativeEvent.Builder().setRelativeX(dx).setRelativeY(dy).build()
        )
    }

    /**
     * Asserts that the cursor location is at the specified coordinates. The coordinates
     * are in the non-scaled, display coordinates.
     */
    private fun assertCursorLocation(x: Float, y: Float) {
        PollingCheck.check("Wait for the cursor at ($x, $y)", CURSOR_TIMEOUT.inWholeMilliseconds) {
            service.lastObservedCursorLocation?.let { it.x.nearEq(x) && it.y.nearEq(y) } ?: false
        }
    }

    private fun scaleTo(controller: AccessibilityService.MagnificationController, scale: Float) {
        val config =
            MagnificationConfig.Builder()
                .setActivated(true)
                .setMode(MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN)
                .setScale(scale)
                .build()
        val setResult = BooleanArray(1)
        service.runOnServiceSync { setResult[0] = controller.setMagnificationConfig(config, false) }
        assertThat(setResult[0]).isTrue()
    }

    private fun assertMagnification(
        controller: AccessibilityService.MagnificationController,
        scale: Float = Float.NaN, centerX: Float = Float.NaN, centerY: Float = Float.NaN
    ) {
        PollingCheck.check(
            "Wait for the magnification to scale=$scale, centerX=$centerX, centerY=$centerY",
            MAGNIFICATION_TIMEOUT.inWholeMilliseconds
        ) check@{
            val actual = controller.getMagnificationConfig() ?: return@check false
            actual.isActivated &&
                (actual.mode == MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN) &&
                (scale.isNaN() || scale.nearEq(actual.scale)) &&
                (centerX.isNaN() || centerX.nearEq(actual.centerX)) &&
                (centerY.isNaN() || centerY.nearEq(actual.centerY))
        }
    }

    /**
     * Sets up a virtual display and a virtual mouse for the test. The virtual mouse is associated
     * with the virtual display.
     */
    private fun prepareVirtualDevices() {
        val deviceLatch = CountDownLatch(1)
        val im = instrumentation.context.getSystemService(InputManager::class.java)
        val inputDeviceListener =
            object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) {
                    onInputDeviceChanged(deviceId)
                }

                override fun onInputDeviceRemoved(deviceId: Int) {}

                override fun onInputDeviceChanged(deviceId: Int) {
                    val device = im.getInputDevice(deviceId) ?: return
                    if (device.vendorId == VIRTUAL_MOUSE_VENDOR_ID &&
                        device.productId == VIRTUAL_MOUSE_PRODUCT_ID
                    ) {
                        deviceLatch.countDown()
                    }
                }
            }
        im.registerInputDeviceListener(inputDeviceListener, Handler(Looper.getMainLooper()))

        virtualDevice = virtualDeviceRule.createManagedVirtualDevice()
        virtualDisplay =
            virtualDeviceRule.createManagedVirtualDisplay(
                virtualDevice,
                VirtualDeviceRule
                    .createDefaultVirtualDisplayConfigBuilder(
                        DISPLAY_WIDTH.toInt(),
                        DISPLAY_HEIGHT.toInt()
                    )
                    .setFlags(
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                            or DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                            or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                    )
            )!!
        virtualMouse =
            virtualDevice.createVirtualMouse(
                VirtualMouseConfig.Builder()
                    .setVendorId(VIRTUAL_MOUSE_VENDOR_ID)
                    .setProductId(VIRTUAL_MOUSE_PRODUCT_ID)
                    .setAssociatedDisplayId(virtualDisplay.display.displayId)
                    .setInputDeviceName("VirtualMouse")
                    .build()
            )

        deviceLatch.await(UI_IDLE_GLOBAL_TIMEOUT.inWholeSeconds, TimeUnit.SECONDS)
        im.unregisterInputDeviceListener(inputDeviceListener)
    }

    /**
     * Launches a test (empty) activity and makes it fullscreen on the specified display. This
     * ensures that system bars are hidden and the full screen magnification enlarges the entire
     * display.
     */
    private fun launchTestActivityFullscreen(displayId: Int) {
        val future = CompletableFuture<Void?>()
        val fullscreenCallback =
            object : OutcomeReceiver<Void, Throwable> {
                override fun onResult(result: Void?) {
                    future.complete(null)
                }

                override fun onError(error: Throwable) {
                    future.completeExceptionally(error)
                }
            }

        val activity =
            virtualDeviceRule.startActivityOnDisplaySync<TestActivity>(
                displayId,
                TestActivity::class.java
            )
        instrumentation.runOnMainSync {
            activity.requestFullscreenMode(
                Activity.FULLSCREEN_MODE_REQUEST_ENTER,
                fullscreenCallback
            )
        }
        future.get(UI_IDLE_GLOBAL_TIMEOUT.inWholeSeconds, TimeUnit.SECONDS)

        uiAutomation.waitForIdle(
            UI_IDLE_TIMEOUT.inWholeMilliseconds, UI_IDLE_GLOBAL_TIMEOUT.inWholeMilliseconds
        )
    }

    class TestMagnificationAccessibilityService : InstrumentedAccessibilityService() {
        private val lock = Any()

        var observingDisplayId = Display.INVALID_DISPLAY
            set(v) {
                synchronized(lock) { field = v }
            }

        var lastObservedCursorLocation: PointF? = null
            private set
            get() {
                synchronized(lock) {
                    return field
                }
            }

        override fun onServiceConnected() {
            serviceInfo =
                getServiceInfo()!!.apply { setMotionEventSources(InputDevice.SOURCE_MOUSE) }

            super.onServiceConnected()
        }

        override fun onMotionEvent(event: MotionEvent) {
            super.onMotionEvent(event)

            synchronized(lock) {
                if (event.displayId == observingDisplayId) {
                    lastObservedCursorLocation = PointF(event.x, event.y)
                }
            }
        }
    }

    class TestActivity : Activity()

    companion object {
        private const val VIRTUAL_MOUSE_VENDOR_ID = 123
        private const val VIRTUAL_MOUSE_PRODUCT_ID = 456

        private val CURSOR_TIMEOUT = 1.seconds
        private val MAGNIFICATION_TIMEOUT = 3.seconds
        private val UI_IDLE_TIMEOUT = 500.milliseconds
        private val UI_IDLE_GLOBAL_TIMEOUT = 5.seconds

        private const val DISPLAY_WIDTH = 640.0f
        private const val DISPLAY_HEIGHT = 480.0f
        private const val CENTER_X = DISPLAY_WIDTH / 2f
        private const val CENTER_Y = DISPLAY_HEIGHT / 2f
    }
}
