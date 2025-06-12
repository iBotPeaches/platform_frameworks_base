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

package com.android.server.wm;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;

import com.android.window.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.Consumer;

/**
 * Test class for {@link AppCompatSafeRegionPolicy}.
 * Build/Install/Run:
 * atest WmTests:AppCompatSafeRegionPolicyTests
 */
@Presubmit
@RunWith(WindowTestRunner.class)
@EnableFlags(Flags.FLAG_SAFE_REGION_LETTERBOXING)
public class AppCompatSafeRegionPolicyTests extends WindowTestsBase {

    @Test
    public void testHasNeedsSafeRegion_returnTrue() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.setNeedsSafeRegionBounds(/*needsSafeRegionBounds*/ true);

            robot.checkNeedsSafeRegionBounds(/* expected */ true);
        });
    }

    @Test
    public void testDoesNotHaveNeedsSafeRegion_returnFalse() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.setNeedsSafeRegionBounds(/*needsSafeRegionBounds*/ false);

            robot.checkNeedsSafeRegionBounds(/* expected */ false);
        });
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    void runTestScenario(@NonNull Consumer<AppCompatSafeRegionPolicyRobotTest> consumer) {
        final AppCompatSafeRegionPolicyRobotTest robot =
                new AppCompatSafeRegionPolicyRobotTest(mWm, mAtm, mSupervisor);
        consumer.accept(robot);
    }

    private static class AppCompatSafeRegionPolicyRobotTest extends AppCompatRobotBase {

        AppCompatSafeRegionPolicyRobotTest(@NonNull WindowManagerService wm,
                @NonNull ActivityTaskManagerService atm,
                @NonNull ActivityTaskSupervisor supervisor) {
            super(wm, atm, supervisor);
        }

        @Override
        void onPostActivityCreation(@NonNull ActivityRecord activity) {
            super.onPostActivityCreation(activity);
            spyOn(activity.mAppCompatController.getSafeRegionPolicy());
        }

        AppCompatSafeRegionPolicy getAppCompatSafeRegionPolicy() {
            return activity().top().mAppCompatController.getSafeRegionPolicy();
        }

        void setNeedsSafeRegionBounds(boolean needsSafeRegionBounds) {
            doReturn(needsSafeRegionBounds).when(
                    getAppCompatSafeRegionPolicy()).getNeedsSafeRegionBounds();
        }

        void checkNeedsSafeRegionBounds(boolean expected) {
            assertEquals(expected, getAppCompatSafeRegionPolicy().getNeedsSafeRegionBounds());
        }
    }
}
