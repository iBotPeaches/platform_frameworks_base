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

package com.android.server.power.stats;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.os.BatteryManager;
import android.os.BatteryStats;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.power.PowerStatsInternal;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.os.BatteryStatsHistoryIterator;
import com.android.internal.os.MonotonicClock;
import com.android.internal.os.PowerProfile;
import com.android.server.LocalServices;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

@RunWith(AndroidJUnit4.class)
@android.platform.test.annotations.DisabledOnRavenwood(reason =
        "PowerStatsInternal is not supported under Ravenwood")
@SuppressWarnings("GuardedBy")
public class BatteryHistoryStepDetailsProviderTest {
    private static final int APP_UID = Process.FIRST_APPLICATION_UID + 42;

    private final MockClock mMockClock = new MockClock();
    private MockBatteryStatsImpl mBatteryStats;
    private final Random mRandom = new Random();
    private Handler mHandler;

    @Before
    public void setup() {
        mMockClock.currentTime = 3000;
        mHandler = new Handler(Looper.getMainLooper());
        mBatteryStats = new MockBatteryStatsImpl(mMockClock, null, mHandler,
                mock(PowerProfile.class));
        mBatteryStats.setRecordAllHistoryLocked(true);
        mBatteryStats.forceRecordAllHistory();
        mBatteryStats.setNoAutoReset(true);
    }

    @Test
    public void update() {
        MockPowerStatsInternal powerStatsService = new MockPowerStatsInternal();
        powerStatsService.addPowerEntity(42, "foo");
        powerStatsService.addPowerEntityState(42, 0, "off");
        powerStatsService.addPowerEntityState(42, 1, "on");
        LocalServices.addService(PowerStatsInternal.class, powerStatsService);
        mBatteryStats.onSystemReady(mock(Context.class));

        mockUpdateCpuStats(100, 1_000_010, 1_000_010);
        powerStatsService.addStateResidencyResult(42, 0, 1000, 2000, 3000);
        powerStatsService.addStateResidencyResult(42, 1, 4000, 5000, 6000);

        mBatteryStats.setBatteryStateLocked(BatteryManager.BATTERY_STATUS_DISCHARGING,
                100, /* plugType */ 0, 90, 72, 3700, 3_600_000, 4_000_000, 0,
                1_000_000, 1_000_000, 1_000_000);
        awaitCompletion();

        mockUpdateCpuStats(200, 5_000_010, 5_000_010);
        powerStatsService.reset();
        powerStatsService.addStateResidencyResult(42, 0, 1111, 2222, 3333);
        powerStatsService.addStateResidencyResult(42, 1, 4444, 5555, 6666);

        // Battery level is unchanged, so we don't write battery level details in history
        mBatteryStats.setBatteryStateLocked(BatteryManager.BATTERY_STATUS_DISCHARGING,
                100, /* plugType */ 0, 80, 72, 3700, 2_400_000, 4_000_000, 0,
                5_500_000, 5_500_000, 5_000_000);
        awaitCompletion();

        // Not a battery state change event, so details are not written
        mBatteryStats.noteAlarmStartLocked("wakeup", null, APP_UID, 6_000_000, 6_000_000);

        mockUpdateCpuStats(300, 6_000_010, 6_000_010);
        powerStatsService.reset();

        // Battery level drops, so we write the accumulated battery level details
        mBatteryStats.setBatteryStateLocked(BatteryManager.BATTERY_STATUS_DISCHARGING,
                100, /* plugType */ 0, 70, 72, 3700, 2_000_000, 4_000_000, 0,
                6_000_000, 6_000_000, 6_000_000);
        awaitCompletion();

        final BatteryStatsHistoryIterator iterator =
                mBatteryStats.iterateBatteryStatsHistory(0, MonotonicClock.UNDEFINED);

        BatteryStats.HistoryItem item;
        assertThat(item = iterator.next()).isNotNull();
        assertThat(item.cmd).isEqualTo((int) BatteryStats.HistoryItem.CMD_RESET);
        assertThat(item.stepDetails).isNull();

        assertThat(item = iterator.next()).isNotNull();
        assertThat(item.batteryLevel).isEqualTo(90);
        assertThat(item.stepDetails.userTime).isEqualTo(100);
        assertThat(item.stepDetails.statSubsystemPowerState).contains("subsystem_0 name=foo "
                + "state_0 name=off time=1000 count=2000 last entry=3000 "
                + "state_1 name=on time=4000 count=5000 last entry=6000");

        assertThat(item = iterator.next()).isNotNull();
        assertThat(item.batteryLevel).isEqualTo(80);
        assertThat(item.stepDetails.userTime).isEqualTo(200);
        assertThat(item.stepDetails.statSubsystemPowerState).contains("subsystem_0 name=foo "
                + "state_0 name=off time=1111 count=2222 last entry=3333 "
                + "state_1 name=on time=4444 count=5555 last entry=6666");

        assertThat(item = iterator.next()).isNotNull();
        assertThat(item.batteryLevel).isEqualTo(80);
        assertThat(item.stepDetails).isNull();

        assertThat(item = iterator.next()).isNotNull();
        assertThat(item.batteryLevel).isEqualTo(70);
        assertThat(item.stepDetails.userTime).isEqualTo(300);
        assertThat(item.stepDetails.statSubsystemPowerState).isNull();

        assertThat(iterator.next()).isNull();
    }

    private void mockUpdateCpuStats(int totalUTimeMs, long elapsedRealtime, long uptime) {
        BatteryStatsImpl.BatteryCallback callback = mock(BatteryStatsImpl.BatteryCallback.class);
        doAnswer(inv -> {
            mMockClock.realtime = elapsedRealtime;
            mMockClock.uptime = uptime;
            synchronized (mBatteryStats) {
                mBatteryStats.addCpuStatsLocked(totalUTimeMs, 0, 0, 0, 0, 0, 0, 0);
                mBatteryStats.finishAddingCpuStatsLocked();
            }
            return null;
        }).when(callback).batteryNeedsCpuUpdate();
        mBatteryStats.setCallback(callback);
    }

    private void awaitCompletion() {
        ConditionVariable done = new ConditionVariable();
        mHandler.post(done::open);
        done.block();
    }
}
