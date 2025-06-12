/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.window.flags.Flags.FLAG_ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS;

import static junit.framework.Assert.assertFalse;

import static org.junit.Assert.assertTrue;

import android.compat.testing.PlatformCompatChangeRule;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.view.DisplayInfo;

import androidx.test.filters.MediumTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/**
 * Build/Install/Run:
 *  atest WmTests:DisplayCompatTests
 */
@MediumTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class DisplayCompatTests extends WindowTestsBase {

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @EnableFlags(FLAG_ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS)
    @Test
    public void testFixedMiscConfigurationWhenMovingToDisplay() {
        // Create an app on the default display, at which point the restart menu isn't enabled.
        final Task task = createTask(mDefaultDisplay);
        final ActivityRecord activity = createActivityRecord(task);
        assertFalse(task.getTaskInfo().appCompatTaskInfo.isRestartMenuEnabledForDisplayMove());

        // Move the app to a secondary display, and the restart menu must get enabled.
        final DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.copyFrom(mDisplayInfo);
        displayInfo.displayId = DEFAULT_DISPLAY + 1;
        final DisplayContent secondaryDisplay = createNewDisplay(displayInfo);
        task.reparent(secondaryDisplay.getDefaultTaskDisplayArea(), true);
        assertTrue(task.getTaskInfo().appCompatTaskInfo.isRestartMenuEnabledForDisplayMove());

        // Once the app gets restarted, the restart menu must be gone.
        activity.restartProcessIfVisible();
        assertFalse(task.getTaskInfo().appCompatTaskInfo.isRestartMenuEnabledForDisplayMove());
    }
}
