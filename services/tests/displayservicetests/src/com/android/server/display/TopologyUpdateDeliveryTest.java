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

package com.android.server.display;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.hardware.display.DisplayTopology;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Tests that applications can receive topology updates correctly.
 */
public class TopologyUpdateDeliveryTest extends EventDeliveryTestBase {
    private static final String TAG = TopologyUpdateDeliveryTest.class.getSimpleName();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TEST_PACKAGE = "com.android.servicestests.apps.topologytestapp";
    private static final String TEST_ACTIVITY = TEST_PACKAGE + ".TopologyUpdateActivity";

    // Topology updates we expect to receive before timeout
    private final LinkedBlockingQueue<DisplayTopology> mExpectations = new LinkedBlockingQueue<>();

    /**
     * Add the received topology update from the test activity to the queue
     *
     * @param topology The corresponding topology update
     */
    private void addTopologyUpdate(DisplayTopology topology) {
        Log.d(TAG, "Received " + topology);
        mExpectations.offer(topology);
    }

    /**
     * Assert that there isn't any unexpected display event from the test activity
     */
    private void assertNoTopologyUpdates() {
        try {
            assertNull(mExpectations.poll(EVENT_TIMEOUT_MSEC, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Wait for the expected topology update from the test activity
     *
     * @param expect The expected topology update
     */
    private void waitTopologyUpdate(DisplayTopology expect) {
        while (true) {
            try {
                DisplayTopology update = mExpectations.poll(TEST_FAILURE_TIMEOUT_MSEC,
                        TimeUnit.MILLISECONDS);
                assertNotNull(update);
                if (expect.equals(update)) {
                    Log.d(TAG, "Found " + update);
                    return;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
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
                    DisplayTopology topology = (DisplayTopology) msg.obj;
                    Log.d(TAG, "Callback " + topology);
                    addTopologyUpdate(topology);
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
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
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
    protected void putExtra(Intent intent) { }

    private void testTopologyUpdateInternal(boolean cached, boolean frozen) {
        Log.d(TAG, "Start test testTopologyUpdate " + cached + " " + frozen);
        // Launch activity and start listening to topology updates
        int pid = launchTestActivity();

        // The test activity in cached or frozen mode won't receive the pending topology updates.
        if (cached) {
            makeTestActivityCached();
        }
        if (frozen) {
            makeTestActivityFrozen(pid);
        }

        // Change the topology
        int primaryDisplayId = 3;
        DisplayTopology.TreeNode root = new DisplayTopology.TreeNode(primaryDisplayId,
                /* width= */ 600, /* height= */ 400, DisplayTopology.TreeNode.POSITION_LEFT,
                /* offset= */ 0);
        DisplayTopology.TreeNode child = new DisplayTopology.TreeNode(/* displayId= */ 1,
                /* width= */ 800, /* height= */ 600, DisplayTopology.TreeNode.POSITION_LEFT,
                /* offset= */ 0);
        root.addChild(child);
        DisplayTopology topology = new DisplayTopology(root, primaryDisplayId);
        mDisplayManager.setDisplayTopology(topology);

        if (cached || frozen) {
            assertNoTopologyUpdates();
        } else {
            waitTopologyUpdate(topology);
        }

        // Unfreeze the test activity, if it was frozen.
        if (frozen) {
            makeTestActivityUnfrozen(pid);
        }

        if (cached || frozen) {
            // Always ensure the test activity is not cached.
            bringTestActivityTop();

            // The test activity becomes non-cached and should receive the pending topology updates
            waitTopologyUpdate(topology);
        }
    }

    @Test
    @RequiresFlagsEnabled(com.android.server.display.feature.flags.Flags.FLAG_DISPLAY_TOPOLOGY)
    public void testTopologyUpdate() {
        testTopologyUpdateInternal(false, false);
    }

    /**
     * The app is moved to cached and the test verifies that no updates are delivered to the cached
     * app.
     */
    @Test
    @RequiresFlagsEnabled(com.android.server.display.feature.flags.Flags.FLAG_DISPLAY_TOPOLOGY)
    public void testTopologyUpdateCached() {
        testTopologyUpdateInternal(true, false);
    }

    /**
     * The app is frozen and the test verifies that no updates are delivered to the frozen app.
     */
    @RequiresFlagsEnabled({com.android.server.am.Flags.FLAG_DEFER_DISPLAY_EVENTS_WHEN_FROZEN,
            com.android.server.display.feature.flags.Flags.FLAG_DISPLAY_TOPOLOGY})
    @Test
    public void testTopologyUpdateFrozen() {
        assumeTrue(isAppFreezerEnabled());
        testTopologyUpdateInternal(false, true);
    }

    /**
     * The app is cached and frozen and the test verifies that no updates are delivered to the app.
     */
    @RequiresFlagsEnabled({com.android.server.am.Flags.FLAG_DEFER_DISPLAY_EVENTS_WHEN_FROZEN,
            com.android.server.display.feature.flags.Flags.FLAG_DISPLAY_TOPOLOGY})
    @Test
    public void testTopologyUpdateCachedFrozen() {
        assumeTrue(isAppFreezerEnabled());
        testTopologyUpdateInternal(true, true);
    }
}
