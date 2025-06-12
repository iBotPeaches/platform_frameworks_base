/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.test.input

import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.app.ApplicationExitInfo
import android.app.Instrumentation
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.IInputConstants.UNMULTIPLIED_DEFAULT_DISPATCHING_TIMEOUT_MILLIS
import android.os.SystemClock
import android.server.wm.CtsWindowInfoUtils.getWindowCenter
import android.server.wm.CtsWindowInfoUtils.waitForWindowOnTop
import android.testing.PollingCheck
import android.util.Log
import android.view.InputEvent
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.cts.input.BlockingQueueEventVerifier
import com.android.cts.input.DebugInputRule
import com.android.cts.input.ShowErrorDialogsRule
import com.android.cts.input.UinputTouchScreen
import com.android.cts.input.inputeventmatchers.withMotionAction
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.function.Supplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Click on the center of the window identified by the provided window token. The click is performed
 * using "UinputTouchScreen" device. If the touchscreen device is closed too soon, it may cause the
 * click to be dropped. Therefore, the provided runnable can ensure that the click is delivered
 * before the device is closed, thus avoiding this race.
 */
private fun clickOnWindow(
    token: IBinder,
    displayId: Int,
    instrumentation: Instrumentation,
    waitForEvent: Runnable,
) {
    val displayManager = instrumentation.context.getSystemService(DisplayManager::class.java)
    val display = displayManager.getDisplay(displayId)
    val point = getWindowCenter({ token }, display.displayId)
    UinputTouchScreen(instrumentation, display).use { touchScreen ->
        touchScreen.touchDown(point.x, point.y).lift()
        // If the device is allowed to close without waiting here, the injected click may be dropped
        waitForEvent.run()
    }
}

/**
 * This test makes sure that an unresponsive gesture monitor gets an ANR.
 *
 * The gesture monitor must be registered from a different process than the instrumented process.
 * Otherwise, when the test runs, you will get: Test failed to run to completion. Reason:
 * 'Instrumentation run failed due to 'keyDispatchingTimedOut''. Check device logcat for details
 * RUNNER ERROR: Instrumentation run failed due to 'keyDispatchingTimedOut'
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class AnrTest {
    companion object {
        private const val TAG = "AnrTest"
        private const val NO_MAX = 0
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var PACKAGE_NAME: String
    private val DISPATCHING_TIMEOUT =
        (UNMULTIPLIED_DEFAULT_DISPATCHING_TIMEOUT_MILLIS * Build.HW_TIMEOUT_MULTIPLIER)
    private var remoteWindowToken: IBinder? = null
    private var remoteDisplayId: Int? = null
    private var remotePid: Int? = null
    private val remoteInputEvents = LinkedBlockingQueue<InputEvent>()
    private val verifier = BlockingQueueEventVerifier(remoteInputEvents)

    // Some devices don't support ANR error dialogs, such as cars, TVs, etc.
    private val anrDialogsAreSupported =
        ActivityTaskManager.currentUiModeSupportsErrorDialogs(instrumentation.targetContext)

    val binder =
        object : IAnrTestService.Stub() {
            override fun provideActivityInfo(token: IBinder, displayId: Int, pid: Int) {
                remoteWindowToken = token
                remoteDisplayId = displayId
                remotePid = pid
            }

            override fun notifyMotion(event: MotionEvent) {
                remoteInputEvents.add(event)
            }
        }

    @get:Rule val showErrorDialogs = ShowErrorDialogsRule()

    @get:Rule val debugInputRule = DebugInputRule()

    @Test
    @DebugInputRule.DebugInput(bug = 339924248)
    fun testGestureMonitorAnr_Close() {
        startUnresponsiveActivity()
        val timestamp = System.currentTimeMillis()
        triggerAnr()
        if (anrDialogsAreSupported) {
            clickCloseAppOnAnrDialog()
        } else {
            Log.i(TAG, "The device does not support ANR dialogs, skipping check for ANR window")
            // We still want to wait for the app to get killed by the ActivityManager
        }
        waitForNewExitReasonAfter(timestamp)
    }

    @Test
    @DebugInputRule.DebugInput(bug = 339924248)
    fun testGestureMonitorAnr_Wait() {
        assumeTrue(anrDialogsAreSupported)
        startUnresponsiveActivity()
        triggerAnr()
        clickWaitOnAnrDialog()
        SystemClock.sleep(500) // Wait at least 500ms after tapping on wait
        // ANR dialog should reappear after a delay - find the close button on it to verify
        val timestamp = System.currentTimeMillis()
        clickCloseAppOnAnrDialog()
        waitForNewExitReasonAfter(timestamp)
    }

    private fun clickCloseAppOnAnrDialog() {
        // Find anr dialog and kill app
        val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)
        val closeAppButton: UiObject2? =
            uiDevice.wait(Until.findObject(By.res("android:id/aerr_close")), 20000)
        if (closeAppButton == null) {
            fail("Could not find anr dialog/close button")
            return
        }
        closeAppButton.click()
    }

    private fun clickWaitOnAnrDialog() {
        // Find anr dialog and tap on wait
        val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)
        val waitButton: UiObject2? =
            uiDevice.wait(Until.findObject(By.res("android:id/aerr_wait")), 20000)
        if (waitButton == null) {
            fail("Could not find anr dialog/wait button")
            return
        }
        waitButton.click()
    }

    private fun getExitReasons(): List<ApplicationExitInfo> {
        val packageName = UnresponsiveGestureMonitorActivity::class.java.getPackage()!!.name
        lateinit var infos: List<ApplicationExitInfo>
        instrumentation.runOnMainSync {
            val am = instrumentation.getContext().getSystemService(ActivityManager::class.java)!!
            infos = am.getHistoricalProcessExitReasons(packageName, remotePid!!, NO_MAX)
        }
        return infos
    }

    /**
     * We must wait for the app to be fully closed before exiting this test. This is because another
     * test may again invoke 'am start' for the same activity. If the 1st process that got ANRd
     * isn't killed by the time second 'am start' runs, the killing logic will apply to the newly
     * launched 'am start' instance, and the second test will fail because the unresponsive activity
     * will never be launched.
     *
     * Also, we must ensure that we wait until it's killed, so that the next test can launch this
     * activity again.
     */
    private fun waitForNewExitReasonAfter(timestamp: Long) {
        PollingCheck.waitFor(Duration.ofSeconds(20).toMillis() * Build.HW_TIMEOUT_MULTIPLIER) {
            val reasons = getExitReasons()
            !reasons.isEmpty() && reasons[0].timestamp >= timestamp
        }
        val reasons = getExitReasons()
        assertTrue(reasons[0].timestamp > timestamp)
        assertEquals(ApplicationExitInfo.REASON_ANR, reasons[0].reason)
    }

    private fun triggerAnr() {
        clickOnWindow(remoteWindowToken!!, remoteDisplayId!!, instrumentation) {
            verifier.assertReceivedMotion(withMotionAction(ACTION_DOWN))
        }

        SystemClock.sleep(DISPATCHING_TIMEOUT.toLong()) // default ANR timeout for gesture monitors
    }

    private fun startUnresponsiveActivity() {
        remoteWindowToken = null
        val intent =
            Intent(instrumentation.targetContext, UnresponsiveGestureMonitorActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        val bundle = Bundle()
        bundle.putBinder("serviceBinder", binder)
        intent.putExtra("serviceBundle", bundle)
        instrumentation.targetContext.startActivity(intent)
        // first, wait for the token to become valid
        PollingCheck.check(
            "UnresponsiveGestureMonitorActivity failed to call 'provideActivityInfo'",
            Duration.ofSeconds(10).toMillis() * Build.HW_TIMEOUT_MULTIPLIER,
        ) {
            remoteWindowToken != null
        }
        // next, wait for the window of the activity to get on top
        // we could combine the two checks above, but the current setup makes it easier to detect
        // errors
        assertTrue(
            "Remote activity window did not become visible",
            waitForWindowOnTop(Duration.ofSeconds(5), Supplier { remoteWindowToken }),
        )
    }
}
