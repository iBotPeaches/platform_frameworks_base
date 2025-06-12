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

package com.android.server.am;

import static android.app.AppProtoEnums.BROADCAST_TYPE_FOREGROUND;
import static android.app.AppProtoEnums.BROADCAST_TYPE_STICKY;
import static android.os.Process.SYSTEM_UID;

import static com.android.internal.util.FrameworkStatsLog.BROADCAST_PROCESSED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.util.FrameworkStatsLog;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

@SmallTest
public class BroadcastProcessedEventRecordTest {

    private static final String ACTION = "action";
    private static final String PROCESS_NAME = "process";
    private static final int[] BROADCAST_TYPES =
            new int[]{BROADCAST_TYPE_FOREGROUND, BROADCAST_TYPE_STICKY};

    private BroadcastProcessedEventRecord mBroadcastProcessedEventRecord;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(
            this).mockStatic(FrameworkStatsLog.class).build();

    @Before
    public void setUp() {
        mBroadcastProcessedEventRecord = createBroadcastProcessEventRecord();
    }

    @Test
    public void addReceiverFinishDetails_withNewRecord_updatesBroadcastRecordEventTime() {
        assertThat(mBroadcastProcessedEventRecord.getReceiverUidForTest()).isEqualTo(SYSTEM_UID);
        assertThat(mBroadcastProcessedEventRecord.getSenderUidForTest()).isEqualTo(SYSTEM_UID);
        assertThat(mBroadcastProcessedEventRecord.getIntentActionForTest()).isEqualTo(ACTION);
        assertThat(mBroadcastProcessedEventRecord.getReceiverProcessNameForTest()).isEqualTo(
                PROCESS_NAME);
        assertThat(mBroadcastProcessedEventRecord.getBroadcastTypesForTest()).isEqualTo(
                BROADCAST_TYPES);

        mBroadcastProcessedEventRecord.addReceiverFinishTime(20);
        verifyBroadcastProcessEventUpdateRecord(
                /* numberOfReceivers= */ 1,
                /* totalBroadcastFinishTimeMillis= */ 20,
                /* maxReceiverFinishTimeMillis= */ 20);

        mBroadcastProcessedEventRecord.addReceiverFinishTime(25);
        verifyBroadcastProcessEventUpdateRecord(
                /* numberOfReceivers= */ 2,
                /* totalBroadcastFinishTimeMillis= */ 45,
                /* maxReceiverFinishTimeMillis= */ 25);

        mBroadcastProcessedEventRecord.addReceiverFinishTime(10);
        verifyBroadcastProcessEventUpdateRecord(
                /* numberOfReceivers= */ 3,
                /* totalBroadcastFinishTimeMillis= */ 55,
                /* maxReceiverFinishTimeMillis= */ 25);
    }

    @Test
    public void logToStatsD_loggingSuccessful() {
        mBroadcastProcessedEventRecord.addReceiverFinishTime(20);
        mBroadcastProcessedEventRecord.logToStatsD();

        ExtendedMockito.verify(() -> FrameworkStatsLog.write(eq(BROADCAST_PROCESSED),
                eq(ACTION),
                eq(SYSTEM_UID),
                eq(SYSTEM_UID),
                eq(1),
                eq(PROCESS_NAME),
                eq(20L),
                eq(20L),
                eq(BROADCAST_TYPES)));
    }

    @Test
    public void logToStatsD_withTotalTimeLessThanTenMs_NoLogging() {
        mBroadcastProcessedEventRecord.addReceiverFinishTime(8);
        mBroadcastProcessedEventRecord.logToStatsD();

        ExtendedMockito.verify(() -> FrameworkStatsLog.write(eq(BROADCAST_PROCESSED),
                eq(ACTION),
                eq(SYSTEM_UID),
                eq(SYSTEM_UID),
                eq(1),
                eq(PROCESS_NAME),
                eq(8L),
                eq(8L),
                eq(BROADCAST_TYPES)), Mockito.never());
    }

    private BroadcastProcessedEventRecord createBroadcastProcessEventRecord() {
        return new BroadcastProcessedEventRecord()
                .setBroadcastTypes(BROADCAST_TYPES)
                .setIntentAction(ACTION)
                .setReceiverProcessName(PROCESS_NAME)
                .setReceiverUid(SYSTEM_UID)
                .setSenderUid(SYSTEM_UID);
    }

    private void verifyBroadcastProcessEventUpdateRecord(
            int numberOfReceivers,
            long totalBroadcastFinishTimeMillis,
            long maxReceiverFinishTimeMillis) {
        assertThat(mBroadcastProcessedEventRecord.getNumberOfReceiversForTest())
                .isEqualTo(numberOfReceivers);
        assertThat(mBroadcastProcessedEventRecord.getTotalBroadcastFinishTimeMillisForTest())
                .isEqualTo(totalBroadcastFinishTimeMillis);
        assertThat(mBroadcastProcessedEventRecord.getMaxReceiverFinishTimeMillisForTest())
                .isEqualTo(maxReceiverFinishTimeMillis);
    }

}
