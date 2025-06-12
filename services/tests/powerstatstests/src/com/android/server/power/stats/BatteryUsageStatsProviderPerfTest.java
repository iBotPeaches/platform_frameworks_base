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

import android.content.Context;
import android.content.res.Resources;
import android.os.BatteryConsumer;
import android.os.BatteryUsageStats;
import android.os.BatteryUsageStatsQuery;
import android.os.ConditionVariable;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.perftests.utils.BenchmarkState;
import android.perftests.utils.PerfStatusReporter;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.internal.os.Clock;
import com.android.internal.os.CpuScalingPolicies;
import com.android.internal.os.CpuScalingPolicyReader;
import com.android.internal.os.MonotonicClock;
import com.android.internal.os.PowerProfile;
import com.android.server.power.stats.processor.MultiStatePowerAttributor;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RunWith(AndroidJUnit4.class)
@LargeTest
@android.platform.test.annotations.DisabledOnRavenwood(reason = "Performance test")
@Ignore("Performance experiment. Comment out @Ignore to run")
public class BatteryUsageStatsProviderPerfTest {
    @Rule
    public final PerfStatusReporter mPerfStatusReporter = new PerfStatusReporter();

    private final Clock mClock = new MockClock();
    private MonotonicClock mMonotonicClock;
    private PowerProfile mPowerProfile;
    private CpuScalingPolicies mCpuScalingPolicies;
    private File mDirectory;
    private Handler mHandler;
    private MockBatteryStatsImpl mBatteryStats;

    @Before
    public void setup() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        mPowerProfile = new PowerProfile(context);
        mCpuScalingPolicies = new CpuScalingPolicyReader().read();

        HandlerThread mHandlerThread = new HandlerThread("batterystats-handler");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        // Extract accumulated battery history to ensure consistent iterations
        mDirectory = Files.createTempDirectory("BatteryUsageStatsProviderPerfTest").toFile();
        File historyDirectory = new File(mDirectory, "battery-history");
        historyDirectory.mkdir();

        long maxMonotonicTime = 0;

        // To recreate battery-history.zip if necessary, perform these commands:
        //   cd /tmp
        //   mkdir battery-history
        //   adb pull /data/system/battery-history
        //   zip battery-history.zip battery-history/*
        //   cp battery-history.zip \
        //       $ANDROID_BUILD_TOP/frameworks/base/services/tests/powerstatstests/res/raw
        Resources resources = context.getResources();
        int resId = resources.getIdentifier("battery-history", "raw", context.getPackageName());
        try (InputStream in = resources.openRawResource(resId)) {
            try (ZipInputStream zis = new ZipInputStream(in)) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    if (!ze.getName().endsWith(".bh")) {
                        continue;
                    }
                    File file = new File(mDirectory, ze.getName());
                    try (OutputStream out = new FileOutputStream(
                            file)) {
                        FileUtils.copy(zis, out);
                    }
                    long timestamp = Long.parseLong(file.getName().replace(".bh", ""));
                    if (timestamp > maxMonotonicTime) {
                        maxMonotonicTime = timestamp;
                    }
                }
            }
        }

        mMonotonicClock = new MonotonicClock(maxMonotonicTime + 1000000000, mClock);
        mBatteryStats = new MockBatteryStatsImpl(mClock, mDirectory);
    }

    @Test
    public void getBatteryUsageStats_accumulated() throws IOException {
        BatteryUsageStatsQuery query = new BatteryUsageStatsQuery.Builder()
                .setMaxStatsAgeMs(0)
                .includePowerStateData()
                .includeScreenStateData()
                .includeProcessStateData()
                .accumulated()
                .build();

        double expectedCpuPower = 0;
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            state.pauseTiming();

            waitForBackgroundThread();

            BatteryUsageStatsProvider provider = createBatteryUsageStatsProvider();
            state.resumeTiming();

            BatteryUsageStats stats = provider.getBatteryUsageStats(mBatteryStats, query);
            waitForBackgroundThread();

            state.pauseTiming();

            double cpuConsumedPower = stats.getAggregateBatteryConsumer(
                            BatteryUsageStats.AGGREGATE_BATTERY_CONSUMER_SCOPE_DEVICE)
                    .getConsumedPower(BatteryConsumer.POWER_COMPONENT_CPU);
            assertThat(cpuConsumedPower).isNonZero();
            if (expectedCpuPower == 0) {
                expectedCpuPower = cpuConsumedPower;
            } else {
                // Verify that all iterations produce the same result
                assertThat(cpuConsumedPower).isEqualTo(expectedCpuPower);
            }
            stats.close();

            state.resumeTiming();
        }
    }

    private BatteryUsageStatsProvider createBatteryUsageStatsProvider() {
        Context context = InstrumentationRegistry.getContext();

        PowerStatsStore store = new PowerStatsStore(mDirectory, mHandler);
        store.reset();

        MultiStatePowerAttributor powerAttributor = new MultiStatePowerAttributor(context, store,
                mPowerProfile, mCpuScalingPolicies, mPowerProfile::getBatteryCapacity);
        return new BatteryUsageStatsProvider(context, powerAttributor, mPowerProfile,
                mCpuScalingPolicies, store, 10000000, mClock, mMonotonicClock);
    }

    private void waitForBackgroundThread() {
        ConditionVariable done = new ConditionVariable();
        mHandler.post(done::open);
        done.block();
    }
}
