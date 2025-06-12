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

package com.android.server.appop;

import static android.app.AppOpsManager.UID_STATE_CACHED;
import static android.app.AppOpsManager.UID_STATE_MAX_LAST_NON_RESTRICTED;
import static android.app.AppOpsManager.UID_STATE_NONEXISTENT;
import static android.permission.flags.Flags.finishRunningOpsForKilledPackages;

import static com.android.server.appop.AppOpsUidStateTracker.processStateToUidState;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSession;
import com.android.internal.os.Clock;
import com.android.server.appop.AppOpsUidStateTracker.UidStateChangedCallback;
import com.android.server.appop.AppOpsUidStateTrackerImpl.DelayableExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collection;
import java.util.PriorityQueue;

@RunWith(Parameterized.class)
public class AppOpsUidStateTrackerTransitionCallbackTest {

    private static final int UID = 10001;

    private static final int STATE_TOP = ActivityManager.PROCESS_STATE_TOP;
    private static final int STATE_FOREGROUND_SERVICE =
            ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE;
    private static final int STATE_FOREGROUND = ActivityManager.PROCESS_STATE_BOUND_TOP;
    private static final int STATE_BACKGROUND = ActivityManager.PROCESS_STATE_SERVICE;
    private static final int STATE_CACHED = ActivityManager.PROCESS_STATE_CACHED_ACTIVITY;
    private static final int STATE_NONEXISTENT = ActivityManager.PROCESS_STATE_NONEXISTENT;

    private static final int[] STATES = {STATE_TOP, STATE_FOREGROUND_SERVICE, STATE_FOREGROUND,
            STATE_BACKGROUND, STATE_CACHED, STATE_NONEXISTENT};
    private static final String[] STATES_NAMES = {"TOP", "FOREGROUND_SERVICE", "FOREGROUND",
            "BACKGROUND", "CACHED", "NONEXISTENT"};

    @Mock
    ActivityManagerInternal mAmi;

    @Mock
    AppOpsService.Constants mConstants;

    AppOpsUidStateTrackerTestExecutor mExecutor = new AppOpsUidStateTrackerTestExecutor();

    AppOpsUidStateTrackerTestClock mClock = new AppOpsUidStateTrackerTestClock(mExecutor);

    AppOpsUidStateTracker mIntf;

    StaticMockitoSession mSession;

    private final int mInitialState;
    private final int mMiddleState;
    private final int mFinalState;

    @Parameterized.Parameters(name = "{3} -> {4} -> {5}")
    public static Collection<Object[]> getParameters() {
        ArrayList<Object[]> parameters = new ArrayList<>();

        for (int i = 0; i < STATES.length; i++) {
            for (int j = 0; j < STATES.length; j++) {
                for (int k = 0; k < STATES.length; k++) {
                    parameters
                            .add(new Object[]{STATES[i], STATES[j], STATES[k], STATES_NAMES[i],
                                    STATES_NAMES[j], STATES_NAMES[k]});
                }
            }
        }

        return parameters;
    }

    public AppOpsUidStateTrackerTransitionCallbackTest(int initialState, int middleState,
            int finalState, String ignoredInitialStateName, String ignoredMiddleStateName,
            String ignoredFinalStateName) {
        mInitialState = initialState;
        mMiddleState = middleState;
        mFinalState = finalState;
    }

    @Before
    public void setUp() {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .startMocking();
        mConstants.TOP_STATE_SETTLE_TIME = 10 * 1000L;
        mConstants.FG_SERVICE_STATE_SETTLE_TIME = 5 * 1000L;
        mConstants.BG_STATE_SETTLE_TIME = 1 * 1000L;
        mIntf = new AppOpsUidStateTrackerImpl(mAmi, mExecutor, mClock, mConstants,
                Thread.currentThread());
    }

    @After
    public void tearDown() {
        mSession.finishMocking();
    }

    @Test
    public void testUidStateChangedCallback() {
        testUidStateTransition(mInitialState, mMiddleState, true);
        testUidStateTransition(mMiddleState, mFinalState, false);
    }

    private void testUidStateTransition(int initialState, int finalState,
            boolean initializeState) {
        int initialUidState = processStateToUidState(initialState);
        int finalUidState = processStateToUidState(finalState);

        boolean expectUidProcessDeath =
                finalUidState == UID_STATE_NONEXISTENT
                        && initialUidState != UID_STATE_NONEXISTENT
                        && finishRunningOpsForKilledPackages();

        boolean foregroundChange = initialUidState <= UID_STATE_MAX_LAST_NON_RESTRICTED
                != finalUidState <= UID_STATE_MAX_LAST_NON_RESTRICTED;
        boolean finalUidStateIsBackgroundAndLessImportant =
                finalUidState > UID_STATE_MAX_LAST_NON_RESTRICTED
                        && finalUidState > initialUidState;

        if (initializeState) {
            mIntf.updateUidProcState(UID, initialState, ActivityManager.PROCESS_CAPABILITY_NONE);
        }

        UidStateChangedCallback cb = addUidStateChangeCallback();

        mIntf.updateUidProcState(UID, finalState, ActivityManager.PROCESS_CAPABILITY_NONE);

        if (finalUidStateIsBackgroundAndLessImportant) {
            mClock.advanceTime(mConstants.TOP_STATE_SETTLE_TIME + 1);
        }

        int expectedInitialUidState = initialUidState == UID_STATE_NONEXISTENT
                ? UID_STATE_CACHED : initialUidState;
        int expectedFinalUidState = finalUidState == UID_STATE_NONEXISTENT
                ? UID_STATE_CACHED : finalUidState;

        if (expectedInitialUidState != expectedFinalUidState) {
            verify(cb, times(1))
                    .onUidStateChanged(eq(UID), eq(expectedFinalUidState), eq(foregroundChange));
            verify(cb, times(1))
                    .onUidStateChanged(anyInt(), anyInt(), anyBoolean());
        } else {
            verify(cb, never()).onUidStateChanged(anyInt(), anyInt(), anyBoolean());
        }
        if (expectUidProcessDeath) {
            verify(cb, times(1)).onUidProcessDeath(eq(UID));
            verify(cb, times(1)).onUidProcessDeath(anyInt());
        } else {
            verify(cb, never()).onUidProcessDeath(anyInt());
        }
    }

    private UidStateChangedCallback addUidStateChangeCallback() {
        UidStateChangedCallback cb =
                Mockito.mock(UidStateChangedCallback.class);
        mIntf.addUidStateChangedCallback(r -> r.run(), cb);
        return cb;
    }

    private static class AppOpsUidStateTrackerTestClock extends Clock {

        private AppOpsUidStateTrackerTestExecutor mExecutor;
        long mElapsedRealTime = 0x5f3759df;

        AppOpsUidStateTrackerTestClock(AppOpsUidStateTrackerTestExecutor executor) {
            mExecutor = executor;
            executor.setUptime(mElapsedRealTime);
        }

        @Override
        public long elapsedRealtime() {
            return mElapsedRealTime;
        }

        void advanceTime(long time) {
            mElapsedRealTime += time;
            mExecutor.setUptime(mElapsedRealTime); // assume uptime == elapsedtime
        }
    }

    private static class AppOpsUidStateTrackerTestExecutor implements DelayableExecutor {

        private static class QueueElement implements Comparable<QueueElement> {

            private long mExecutionTime;
            private Runnable mRunnable;

            private QueueElement(long executionTime, Runnable runnable) {
                mExecutionTime = executionTime;
                mRunnable = runnable;
            }

            @Override
            public int compareTo(QueueElement queueElement) {
                return Long.compare(mExecutionTime, queueElement.mExecutionTime);
            }
        }

        private long mUptime = 0;

        private PriorityQueue<QueueElement> mDelayedMessages = new PriorityQueue();

        @Override
        public void execute(Runnable runnable) {
            runnable.run();
        }

        @Override
        public void executeDelayed(Runnable runnable, long delay) {
            if (delay <= 0) {
                execute(runnable);
            }

            mDelayedMessages.add(new QueueElement(mUptime + delay, runnable));
        }

        private void setUptime(long uptime) {
            while (!mDelayedMessages.isEmpty()
                    && mDelayedMessages.peek().mExecutionTime <= uptime) {
                mDelayedMessages.poll().mRunnable.run();
            }

            mUptime = uptime;
        }
    }
}
