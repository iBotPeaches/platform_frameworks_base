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

package com.android.server.display;

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
import static android.util.DisplayMetrics.DENSITY_HIGH;
import static android.util.DisplayMetrics.DENSITY_MEDIUM;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.hardware.display.VirtualDisplay;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.android.server.am.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Tests that applications can receive display events correctly.
 */
@RunWith(Parameterized.class)
public class DisplayEventDeliveryTest extends EventDeliveryTestBase {
    private static final String TAG = "DisplayEventDeliveryTest";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String NAME = TAG;
    private static final int WIDTH = 720;
    private static final int HEIGHT = 480;

    private static final int DISPLAY_ADDED = 1;
    private static final int DISPLAY_CHANGED = 2;
    private static final int DISPLAY_REMOVED = 3;

    private static final String TEST_PACKAGE =
            "com.android.servicestests.apps.displaymanagertestapp";
    private static final String TEST_ACTIVITY = TEST_PACKAGE + ".DisplayEventActivity";
    private static final String TEST_DISPLAYS = "DISPLAYS";

    private final Object mLock = new Object();

    /**
     * Array of DisplayBundle. The test handler uses it to check if certain display events have
     * been sent to DisplayEventActivity.
     * Key: displayId of each new VirtualDisplay created by this test
     * Value: DisplayBundle, storing the VirtualDisplay and its expected display events
     *
     * NOTE: The lock is required when adding and removing virtual displays. Otherwise it's not
     * necessary to lock mDisplayBundles when accessing it from the test function.
     */
    @GuardedBy("mLock")
    private SparseArray<DisplayBundle> mDisplayBundles;

    /**
     * Helper class to store VirtualDisplay and its corresponding display events expected to be
     * sent to DisplayEventActivity.
     */
    private static final class DisplayBundle {
        private VirtualDisplay mVirtualDisplay;
        private final int mDisplayId;

        // Display events we expect to receive before timeout
        private final LinkedBlockingQueue<Integer> mExpectations;

        DisplayBundle(VirtualDisplay display) {
            mVirtualDisplay = display;
            mDisplayId = display.getDisplay().getDisplayId();
            mExpectations = new LinkedBlockingQueue<>();
        }

        public void releaseDisplay() {
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
            }
            mVirtualDisplay = null;
        }

        /**
         * Add the received display event from the test activity to the queue
         *
         * @param event The corresponding display event
         */
        public void addDisplayEvent(int event) {
            Log.d(TAG, "Received " + mDisplayId + " " + event);
            mExpectations.offer(event);
        }

        /**
         * Assert that there isn't any unexpected display event from the test activity
         */
        public void assertNoDisplayEvents() {
            try {
                assertNull(mExpectations.poll(EVENT_TIMEOUT_MSEC, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Wait for the expected display event from the test activity
         *
         * @param expect The expected display event
         */
        public void waitDisplayEvent(int expect) {
            while (true) {
                try {
                    final Integer event;
                    event = mExpectations.poll(TEST_FAILURE_TIMEOUT_MSEC, TimeUnit.MILLISECONDS);
                    assertNotNull(event);
                    if (expect == event) {
                        Log.d(TAG, "Found    " + mDisplayId + " " + event);
                        return;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * How many virtual displays to create during the test
     */
    @Parameter(0)
    public int mDisplayCount;

    @Parameters(name = "#{index}: {0}")
    public static Iterable<? extends Object> data() {
        return Arrays.asList(new Object[][]{ {1}, {2}, {3}, {10} });
    }

    private class TestHandler extends Handler {
        TestHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MESSAGE_LAUNCHED:
                    mPid = msg.arg1;
                    mUid = msg.arg2;
                    Log.d(TAG, "Launched " + mPid + " " + mUid);
                    mLatchActivityLaunch.countDown();
                    break;
                case MESSAGE_CALLBACK:
                    Log.d(TAG, "Callback " + msg.arg1 + " " + msg.arg2);
                    synchronized (mLock) {
                        // arg1: displayId
                        DisplayBundle bundle = mDisplayBundles.get(msg.arg1);
                        if (bundle != null) {
                            // arg2: display event
                            bundle.addDisplayEvent(msg.arg2);
                        }
                    }
                    break;
                default:
                    fail("Unexpected value: " + msg.what);
                    break;
            }
        }
    }

    @Before
    public void setUp() {
        super.setUp();
        // The lock is not functionally necessary but eliminates lint error messages.
        synchronized (mLock) {
            mDisplayBundles = new SparseArray<>();
        }
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        synchronized (mLock) {
            for (int i = 0; i < mDisplayBundles.size(); i++) {
                DisplayBundle bundle = mDisplayBundles.valueAt(i);
                // Clean up unreleased virtual display
                bundle.releaseDisplay();
            }
            mDisplayBundles.clear();
        }
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Handler getHandler(Looper looper) {
        return new TestHandler(looper);
    }

    @Override
    protected String getTestPackage() {
        return TEST_PACKAGE;
    }

    @Override
    protected String getTestActivity() {
        return TEST_ACTIVITY;
    }

    @Override
    protected void putExtra(Intent intent) {
        intent.putExtra(TEST_DISPLAYS, mDisplayCount);
    }

    /**
     * Return a display bundle at the stated index.  The bundle is retrieved under lock.
     */
    private DisplayBundle displayBundleAt(int i) {
        synchronized (mLock) {
            return mDisplayBundles.valueAt(i);
        }
    }

    /**
     * Create virtual displays, change their configurations and release them. The number of
     * displays is set by the {@link #data()} parameter.
     */
    private void testDisplayEventsInternal(boolean cached, boolean frozen) {
        Log.d(TAG, "Start test testDisplayEvents " + mDisplayCount + " " + cached + " " + frozen);
        // Launch DisplayEventActivity and start listening to display events
        int pid = launchTestActivity();

        // The test activity in cached or frozen mode won't receive the pending display events.
        if (cached) {
            makeTestActivityCached();
        }
        if (frozen) {
            makeTestActivityFrozen(pid);
        }

        // Create new virtual displays
        for (int i = 0; i < mDisplayCount; i++) {
            // Lock is needed here to ensure the handler can query the displays
            synchronized (mLock) {
                VirtualDisplay display = createVirtualDisplay(NAME + i);
                DisplayBundle bundle = new DisplayBundle(display);
                mDisplayBundles.put(bundle.mDisplayId, bundle);
            }
        }

        for (int i = 0; i < mDisplayCount; i++) {
            if (cached || frozen) {
                // DISPLAY_ADDED should be deferred for a cached or frozen process.
                displayBundleAt(i).assertNoDisplayEvents();
            } else {
                // DISPLAY_ADDED should arrive immediately for non-cached process
                displayBundleAt(i).waitDisplayEvent(DISPLAY_ADDED);
            }
        }

        // Change the virtual displays
        for (int i = 0; i < mDisplayCount; i++) {
            DisplayBundle bundle = displayBundleAt(i);
            bundle.mVirtualDisplay.resize(WIDTH, HEIGHT, DENSITY_HIGH);
        }

        for (int i = 0; i < mDisplayCount; i++) {
            if (cached || frozen) {
                // DISPLAY_CHANGED should be deferred for cached or frozen  process.
                displayBundleAt(i).assertNoDisplayEvents();
            } else {
                // DISPLAY_CHANGED should arrive immediately for non-cached process
                displayBundleAt(i).waitDisplayEvent(DISPLAY_CHANGED);
            }
        }

        // Unfreeze the test activity, if it was frozen.
        if (frozen) {
            makeTestActivityUnfrozen(pid);
        }

        if (cached || frozen) {
            // Always ensure the test activity is not cached.
            bringTestActivityTop();

            // The test activity becomes non-cached and should receive the pending display events
            for (int i = 0; i < mDisplayCount; i++) {
                // The pending DISPLAY_ADDED & DISPLAY_CHANGED should arrive now
                displayBundleAt(i).waitDisplayEvent(DISPLAY_ADDED);
                displayBundleAt(i).waitDisplayEvent(DISPLAY_CHANGED);
            }
        }

        // Release the virtual displays
        for (int i = 0; i < mDisplayCount; i++) {
            displayBundleAt(i).releaseDisplay();
        }

        // DISPLAY_REMOVED should arrive now
        for (int i = 0; i < mDisplayCount; i++) {
            displayBundleAt(i).waitDisplayEvent(DISPLAY_REMOVED);
        }
    }

    /**
     * Create virtual displays, change their configurations and release them.
     */
    @Test
    public void testDisplayEvents() {
        testDisplayEventsInternal(false, false);
    }

    /**
     * Create virtual displays, change their configurations and release them.  The display app is
     * moved to cached and the test verifies that no events are delivered to the cached app.
     */
    @Test
    public void testDisplayEventsCached() {
        testDisplayEventsInternal(true, false);
    }

    /**
     * Create virtual displays, change their configurations and release them.  The display app is
     * frozen and the test verifies that no events are delivered to the frozen app.
     */
    @RequiresFlagsEnabled(Flags.FLAG_DEFER_DISPLAY_EVENTS_WHEN_FROZEN)
    @Test
    public void testDisplayEventsFrozen() {
        assumeTrue(isAppFreezerEnabled());
        testDisplayEventsInternal(false, true);
    }

    /**
     * Create virtual displays, change their configurations and release them.  The display app is
     * cached and frozen and the test verifies that no events are delivered to the app.
     */
    @RequiresFlagsEnabled(Flags.FLAG_DEFER_DISPLAY_EVENTS_WHEN_FROZEN)
    @Test
    public void testDisplayEventsCachedFrozen() {
        assumeTrue(isAppFreezerEnabled());
        testDisplayEventsInternal(true, true);
    }

    /**
     * Create a virtual display
     *
     * @param name The name of the new virtual display
     * @return The new virtual display
     */
    private VirtualDisplay createVirtualDisplay(String name) {
        return mDisplayManager.createVirtualDisplay(name, WIDTH, HEIGHT, DENSITY_MEDIUM,
                null /* surface: as we don't actually draw anything, null is enough */,
                VIRTUAL_DISPLAY_FLAG_PUBLIC | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                /* flags: a public virtual display that another app can access */);
    }
}
