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
package android.media.projection;

import static com.android.compatibility.common.util.FeatureUtil.isAutomotive;
import static com.android.compatibility.common.util.FeatureUtil.isTV;
import static com.android.compatibility.common.util.FeatureUtil.isWatch;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.cts.MediaProjectionRule;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.telecom.TelecomManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.media.projection.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link MediaProjection} stopping behavior.
 *
 * Run with:
 * atest MediaProjectionTests:MediaProjectionStoppingTest
 */
@FrameworkSpecificTest
public class MediaProjectionStoppingTest {
    private static final String TAG = "MediaProjectionStoppingTest";
    private static final int STOP_DIALOG_WAIT_TIMEOUT_MS = 5000;
    private static final String CALL_HELPER_START_CALL = "start_call";
    private static final String CALL_HELPER_STOP_CALL = "stop_call";
    private static final String STOP_DIALOG_TITLE_RES_ID = "android:id/alertTitle";
    private static final String STOP_DIALOG_CLOSE_BUTTON_RES_ID = "android:id/button2";

    @Rule public MediaProjectionRule mMediaProjectionRule = new MediaProjectionRule();

    private Context mContext;
    private int mTimeoutMs;
    private TelecomManager mTelecomManager;
    private TelephonyManager mTelephonyManager;
    private TestCallStateListener mTestCallStateListener;

    @Before
    public void setUp() throws InterruptedException {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        runWithShellPermissionIdentity(
                () -> {
                    mContext.getPackageManager()
                            .revokeRuntimePermission(
                                    mContext.getPackageName(),
                                    Manifest.permission.SYSTEM_ALERT_WINDOW,
                                    new UserHandle(mContext.getUserId()));
                });
        mTimeoutMs = 1000;

        mTestCallStateListener = new TestCallStateListener(mContext);
    }

    @After
    public void cleanup() {
        mTestCallStateListener.release();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_STOP_MEDIA_PROJECTION_ON_CALL_END)
    @ApiTest(apis = "android.media.projection.MediaProjection.Callback#onStop")
    public void testMediaProjectionStop_callStartedAfterMediaProjection_doesNotStop()
            throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELECOM));

        mMediaProjectionRule.startMediaProjection();

        CountDownLatch latch = new CountDownLatch(1);
        mMediaProjectionRule.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        latch.countDown();
                    }
                });
        mMediaProjectionRule.createVirtualDisplay();

        try {
            startPhoneCall();
        } finally {
            endPhoneCall();
        }

        assertWithMessage("MediaProjection should not be stopped on call end")
                .that(latch.await(mTimeoutMs, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_STOP_MEDIA_PROJECTION_ON_CALL_END)
    @RequiresFlagsDisabled(Flags.FLAG_SHOW_STOP_DIALOG_POST_CALL_END)
    @ApiTest(apis = "android.media.projection.MediaProjection.Callback#onStop")
    public void
    testMediaProjectionStop_callStartedBeforeMediaProjection_stopDialogFlagDisabled__shouldStop()
            throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELECOM));
        CountDownLatch latch = new CountDownLatch(1);
        try {
            startPhoneCall();

            mMediaProjectionRule.startMediaProjection();

            mMediaProjectionRule.registerCallback(
                    new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            latch.countDown();
                        }
                    });
            mMediaProjectionRule.createVirtualDisplay();

        } finally {
            endPhoneCall();
        }

        assertWithMessage("MediaProjection was not stopped after call end")
                .that(latch.await(mTimeoutMs, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled({
            Flags.FLAG_STOP_MEDIA_PROJECTION_ON_CALL_END,
            Flags.FLAG_SHOW_STOP_DIALOG_POST_CALL_END
    })
    public void
    callEnds_mediaProjectionStartedDuringCallAndIsActive_stopDialogFlagEnabled_showsStopDialog()
            throws Exception {
        // MediaProjection stop Dialog is only available on phones.
        assumeFalse(isWatch());
        assumeFalse(isAutomotive());
        assumeFalse(isTV());

        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELECOM));

        try {
            startPhoneCall();
            mMediaProjectionRule.startMediaProjection();

            mMediaProjectionRule.registerCallback(
                    new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            fail(
                                    "MediaProjection should not be stopped when"
                                            + " FLAG_SHOW_STOP_DIALOG_POST_CALL_END is enabled");
                        }
                    });
            mMediaProjectionRule.createVirtualDisplay();

        } finally {
            endPhoneCall();
        }

        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        boolean isDialogShown =
                device.wait(
                        Until.hasObject(By.res(STOP_DIALOG_TITLE_RES_ID)),
                        STOP_DIALOG_WAIT_TIMEOUT_MS);
        assertWithMessage("Stop dialog should be visible").that(isDialogShown).isTrue();

        // Find and click the "Close" button
        boolean hasCloseButton =
                device.wait(
                        Until.hasObject(By.res(STOP_DIALOG_CLOSE_BUTTON_RES_ID)),
                        STOP_DIALOG_WAIT_TIMEOUT_MS);
        if (hasCloseButton) {
            device.findObject(By.res(STOP_DIALOG_CLOSE_BUTTON_RES_ID)).click();
            Log.d(TAG, "Clicked on 'Close' button to dismiss the stop dialog.");
        } else {
            fail("Close button not found, unable to dismiss stop dialog.");
        }
    }

    private void startPhoneCall() throws InterruptedException {
        mTestCallStateListener.assertCallState(false);
        mContext.startActivity(getCallHelperIntent(CALL_HELPER_START_CALL));
        mTestCallStateListener.waitForNextCallState(true, mTimeoutMs, TimeUnit.MILLISECONDS);
    }

    private void endPhoneCall() throws InterruptedException {
        mTestCallStateListener.assertCallState(true);
        mContext.startActivity(getCallHelperIntent(CALL_HELPER_STOP_CALL));
        mTestCallStateListener.waitForNextCallState(false, mTimeoutMs, TimeUnit.MILLISECONDS);
    }

    private Intent getCallHelperIntent(String action) {
        return new Intent(action)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .setComponent(
                        new ComponentName(
                                "android.media.projection.cts.helper",
                                "android.media.projection.cts.helper.CallHelperActivity"));
    }

    private static final class TestCallStateListener extends TelephonyCallback
            implements TelephonyCallback.CallStateListener {
        private final BlockingQueue<Boolean> mCallStates = new LinkedBlockingQueue<>();
        private final TelecomManager mTelecomManager;
        private final TelephonyManager mTelephonyManager;

        private TestCallStateListener(Context context) throws InterruptedException {
            mTelecomManager = context.getSystemService(TelecomManager.class);
            mTelephonyManager = context.getSystemService(TelephonyManager.class);
            mCallStates.offer(isInCall());

            assertThat(mCallStates.take()).isFalse();

            runWithShellPermissionIdentity(
                    () ->
                            mTelephonyManager.registerTelephonyCallback(
                                    context.getMainExecutor(), this));
        }

        public void release() {
            runWithShellPermissionIdentity(
                    () -> mTelephonyManager.unregisterTelephonyCallback(this));
        }

        @Override
        public void onCallStateChanged(int state) {
            mCallStates.offer(isInCall());
        }

        public void waitForNextCallState(boolean expectedCallState, long timeout, TimeUnit unit)
                throws InterruptedException {
            String message =
                    String.format(
                            "Call was not %s after timeout",
                            expectedCallState ? "started" : "ended");

            boolean value;
            do {
                value = mCallStates.poll(timeout, unit);
            } while (value != expectedCallState);
            assertWithMessage(message).that(value).isEqualTo(expectedCallState);
        }

        private boolean isInCall() {
            return runWithShellPermissionIdentity(mTelecomManager::isInCall);
        }

        public void assertCallState(boolean expected) {
            assertWithMessage("Unexpected call state").that(isInCall()).isEqualTo(expected);
        }
    }
}
