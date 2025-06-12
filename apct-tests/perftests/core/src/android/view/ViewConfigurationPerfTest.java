/*
 * Copyright 2023 The Android Open Source Project
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

package android.view;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.content.Context;
import android.perftests.utils.BenchmarkState;
import android.perftests.utils.PerfStatusReporter;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class ViewConfigurationPerfTest {
    @Rule
    public PerfStatusReporter mPerfStatusReporter = new PerfStatusReporter();

    private final Context mContext = getInstrumentation().getTargetContext();

    @Test
    public void testGet_newViewConfiguration() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();

        while (state.keepRunning()) {
            state.pauseTiming();
            // Reset cache so that `ViewConfiguration#get` creates a new instance.
            ViewConfiguration.resetCacheForTesting();
            state.resumeTiming();

            ViewConfiguration.get(mContext);
        }
    }

    @Test
    public void testGet_cachedViewConfiguration() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        // Do `get` once to make sure there's something cached.
        ViewConfiguration.get(mContext);

        while (state.keepRunning()) {
            ViewConfiguration.get(mContext);
        }
    }

    @Test
    public void testGetPressedStateDuration_unCached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();

        while (state.keepRunning()) {
            state.pauseTiming();
            // Reset any caches.
            ViewConfiguration.resetCacheForTesting();
            state.resumeTiming();

            ViewConfiguration.getPressedStateDuration();
        }
    }

    @Test
    public void testGetPressedStateDuration_cached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        // Do `get` once to make sure the value gets cached.
        ViewConfiguration.getPressedStateDuration();

        while (state.keepRunning()) {
            ViewConfiguration.getPressedStateDuration();
        }
    }

    @Test
    public void testGetTapTimeout_unCached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();

        while (state.keepRunning()) {
            state.pauseTiming();
            // Reset any caches.
            ViewConfiguration.resetCacheForTesting();
            state.resumeTiming();

            ViewConfiguration.getTapTimeout();
        }
    }

    @Test
    public void testGetTapTimeout_cached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        // Do `get` once to make sure the value gets cached.
        ViewConfiguration.getTapTimeout();

        while (state.keepRunning()) {
            ViewConfiguration.getTapTimeout();
        }
    }

    @Test
    public void testGetJumpTapTimeout_unCached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();

        while (state.keepRunning()) {
            state.pauseTiming();
            // Reset any caches.
            ViewConfiguration.resetCacheForTesting();
            state.resumeTiming();

            ViewConfiguration.getJumpTapTimeout();
        }
    }

    @Test
    public void testGetJumpTapTimeout_cached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        // Do `get` once to make sure the value gets cached.
        ViewConfiguration.getJumpTapTimeout();

        while (state.keepRunning()) {
            ViewConfiguration.getJumpTapTimeout();
        }
    }

    @Test
    public void testGetDoubleTapTimeout_unCached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();

        while (state.keepRunning()) {
            state.pauseTiming();
            // Reset any caches.
            ViewConfiguration.resetCacheForTesting();
            state.resumeTiming();

            ViewConfiguration.getDoubleTapTimeout();
        }
    }

    @Test
    public void testGetDoubleTapTimeout_cached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        // Do `get` once to make sure the value gets cached.
        ViewConfiguration.getDoubleTapTimeout();

        while (state.keepRunning()) {
            ViewConfiguration.getDoubleTapTimeout();
        }
    }

    @Test
    public void testGetDoubleTapMinTime_unCached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();

        while (state.keepRunning()) {
            state.pauseTiming();
            // Reset any caches.
            ViewConfiguration.resetCacheForTesting();
            state.resumeTiming();

            ViewConfiguration.getDoubleTapMinTime();
        }
    }

    @Test
    public void testGetDoubleTapMinTime_cached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        // Do `get` once to make sure the value gets cached.
        ViewConfiguration.getDoubleTapMinTime();

        while (state.keepRunning()) {
            ViewConfiguration.getDoubleTapMinTime();
        }
    }

    @Test
    public void testGetZoomControlsTimeout_unCached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();

        while (state.keepRunning()) {
            state.pauseTiming();
            // Reset any caches.
            ViewConfiguration.resetCacheForTesting();
            state.resumeTiming();

            ViewConfiguration.getZoomControlsTimeout();
        }
    }

    @Test
    public void testGetZoomControlsTimeout_cached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        // Do `get` once to make sure the value gets cached.
        ViewConfiguration.getZoomControlsTimeout();

        while (state.keepRunning()) {
            ViewConfiguration.getZoomControlsTimeout();
        }
    }

    @Test
    public void testGetLongPressTimeout() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();

        while (state.keepRunning()) {
            ViewConfiguration.getLongPressTimeout();
        }
    }

    @Test
    public void testGetMultiPressTimeout() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();

        while (state.keepRunning()) {
            ViewConfiguration.getMultiPressTimeout();
        }
    }

    @Test
    public void testGetKeyRepeatTimeout() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();

        while (state.keepRunning()) {
            ViewConfiguration.getKeyRepeatTimeout();
        }
    }

    @Test
    public void testGetKeyRepeatDelay() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();

        while (state.keepRunning()) {
            ViewConfiguration.getKeyRepeatDelay();
        }
    }

    @Test
    public void testGetHoverTapSlop_unCached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();

        while (state.keepRunning()) {
            state.pauseTiming();
            // Reset any caches.
            ViewConfiguration.resetCacheForTesting();
            state.resumeTiming();

            ViewConfiguration.getHoverTapSlop();
        }
    }

    @Test
    public void testGetHoverTapSlop_cached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        // Do `get` once to make sure the value gets cached.
        ViewConfiguration.getHoverTapSlop();

        while (state.keepRunning()) {
            ViewConfiguration.getHoverTapSlop();
        }
    }

    @Test
    public void testGetScrollFriction_unCached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();

        while (state.keepRunning()) {
            state.pauseTiming();
            // Reset any caches.
            ViewConfiguration.resetCacheForTesting();
            state.resumeTiming();

            ViewConfiguration.getScrollFriction();
        }
    }

    @Test
    public void testGetScrollFriction_cached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        // Do `get` once to make sure the value gets cached.
        ViewConfiguration.getScrollFriction();

        while (state.keepRunning()) {
            ViewConfiguration.getScrollFriction();
        }
    }

    @Test
    public void testGetDefaultActionModeHideDuration_unCached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();

        while (state.keepRunning()) {
            state.pauseTiming();
            // Reset any caches.
            ViewConfiguration.resetCacheForTesting();
            state.resumeTiming();

            ViewConfiguration.getDefaultActionModeHideDuration();
        }
    }

    @Test
    public void testGetDefaultActionModeHideDuration_cached() {
        final BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        // Do `get` once to make sure the value gets cached.
        ViewConfiguration.getDefaultActionModeHideDuration();

        while (state.keepRunning()) {
            ViewConfiguration.getDefaultActionModeHideDuration();
        }
    }
}
