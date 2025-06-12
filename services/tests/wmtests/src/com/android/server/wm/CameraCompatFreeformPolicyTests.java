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

package com.android.server.wm;

import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_FREEFORM_LANDSCAPE_DEVICE_IN_LANDSCAPE;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_FREEFORM_LANDSCAPE_DEVICE_IN_PORTRAIT;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_FREEFORM_NONE;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_FREEFORM_PORTRAIT_DEVICE_IN_LANDSCAPE;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_FREEFORM_PORTRAIT_DEVICE_IN_PORTRAIT;
import static android.app.CameraCompatTaskInfo.FreeformCameraCompatMode;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.servertransaction.ActivityLifecycleItem.ON_PAUSE;
import static android.app.servertransaction.ActivityLifecycleItem.ON_STOP;
import static android.content.pm.ActivityInfo.OVERRIDE_CAMERA_COMPAT_DISABLE_SIMULATE_REQUESTED_ORIENTATION;
import static android.content.pm.ActivityInfo.OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.wm.AppCompatConfiguration.MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO;
import static com.android.window.flags.Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING;
import static com.android.window.flags.Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.app.IApplicationThread;
import android.app.WindowConfiguration.WindowingMode;
import android.app.servertransaction.RefreshCallbackItem;
import android.app.servertransaction.ResumeActivityItem;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.pm.ActivityInfo.ScreenOrientation;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Configuration.Orientation;
import android.graphics.Rect;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.RemoteException;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.view.Surface;

import androidx.test.filters.SmallTest;

import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Tests for {@link CameraCompatFreeformPolicy}.
 *
 * Build/Install/Run:
 *  atest WmTests:CameraCompatFreeformPolicyTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class CameraCompatFreeformPolicyTests extends WindowTestsBase {
    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    // Main activity package name needs to be the same as the process to test overrides.
    private static final String TEST_PACKAGE_1 = "com.android.frameworks.wmtests";
    private static final String TEST_PACKAGE_2 = "com.test.package.two";
    private static final String CAMERA_ID_1 = "camera-1";

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testFeatureDisabled_cameraCompatFreeformPolicyNotCreated() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkCameraCompatPolicyNotCreated();
        });
    }

    @Test
    @EnableFlags({FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING,
            FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT})
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_DISABLE_SIMULATE_REQUESTED_ORIENTATION})
    public void testIsCameraRunningAndWindowingModeEligible_disabledViaOverride_returnsFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsCameraRunningAndWindowingModeEligible(false);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testIsCameraRunningAndWindowingModeEligible_cameraNotRunning_returnsFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.checkIsCameraRunningAndWindowingModeEligible(false);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testIsCameraRunningAndWindowingModeEligible_notFreeformWindowing_returnsFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT, WINDOWING_MODE_FULLSCREEN);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsCameraRunningAndWindowingModeEligible(false);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testIsCameraRunningAndWindowingModeEligible_optInFreeformCameraRunning_true() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsCameraRunningAndWindowingModeEligible(true);
        });
    }

    @Test
    @EnableFlags({FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING,
            FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT})
    public void testIsCameraRunningAndWindowingModeEligible_freeformCameraRunning_true() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsCameraRunningAndWindowingModeEligible(true);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT)
    public void testIsFreeformLetterboxingForCameraAllowed_optInMechanism_notOptedIn_retFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsFreeformLetterboxingForCameraAllowed(false);
        });
    }

    @Test
    @EnableFlags({FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING,
            FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT})
    public void testIsFreeformLetterboxingForCameraAllowed_notOptedOut_returnsTrue() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsFreeformLetterboxingForCameraAllowed(true);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testIsFreeformLetterboxingForCameraAllowed_cameraNotRunning_returnsFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.checkIsFreeformLetterboxingForCameraAllowed(false);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testIsFreeformLetterboxingForCameraAllowed_notFreeformWindowing_returnsFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT, WINDOWING_MODE_FULLSCREEN);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsFreeformLetterboxingForCameraAllowed(false);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testIsFreeformLetterboxingForCameraAllowed_optInFreeformCameraRunning_true() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsFreeformLetterboxingForCameraAllowed(true);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testFullscreen_doesNotActivateCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT, WINDOWING_MODE_FULLSCREEN);
            robot.setInFreeformWindowingMode(false);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertNotInCameraCompatMode();
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testOrientationUnspecified_doesNotActivateCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_UNSPECIFIED);

            robot.assertNotInCameraCompatMode();
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testNoCameraConnection_doesNotActivateCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.assertNotInCameraCompatMode();
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testCameraConnected_deviceInPortrait_portraitCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.activity().rotateDisplayForTopActivity(ROTATION_0);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertInCameraCompatMode(CAMERA_COMPAT_FREEFORM_PORTRAIT_DEVICE_IN_PORTRAIT);
            robot.assertActivityRefreshRequested(/* refreshRequested */ false);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testCameraConnected_deviceInLandscape_portraitCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.activity().rotateDisplayForTopActivity(ROTATION_270);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertInCameraCompatMode(CAMERA_COMPAT_FREEFORM_PORTRAIT_DEVICE_IN_LANDSCAPE);
            robot.assertActivityRefreshRequested(/* refreshRequested */ false);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testCameraConnected_deviceInPortrait_landscapeCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_LANDSCAPE);
            robot.activity().rotateDisplayForTopActivity(ROTATION_0);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertInCameraCompatMode(CAMERA_COMPAT_FREEFORM_LANDSCAPE_DEVICE_IN_PORTRAIT);
            robot.assertActivityRefreshRequested(/* refreshRequested */ false);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testCameraConnected_deviceInLandscape_landscapeCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_LANDSCAPE);
            robot.activity().rotateDisplayForTopActivity(ROTATION_270);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertInCameraCompatMode(CAMERA_COMPAT_FREEFORM_LANDSCAPE_DEVICE_IN_LANDSCAPE);
            robot.assertActivityRefreshRequested(/* refreshRequested */ false);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testCameraReconnected_cameraCompatModeAndRefresh() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.activity().rotateDisplayForTopActivity(ROTATION_270);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging(/* letterboxNew= */ true,
                /* lastLetterbox= */ false);
            robot.assertActivityRefreshRequested(/* refreshRequested */ true);
            robot.onCameraClosed(CAMERA_ID_1);
            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            // Activity is letterboxed from the previous configuration change.
            robot.callOnActivityConfigurationChanging(/* letterboxNew= */ true,
                    /* lastLetterbox= */ true);

            robot.assertInCameraCompatMode(CAMERA_COMPAT_FREEFORM_PORTRAIT_DEVICE_IN_LANDSCAPE);
            robot.assertActivityRefreshRequested(/* refreshRequested */ true);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testCameraOpenedForDifferentPackage_notInCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_2);

            robot.assertNotInCameraCompatMode();
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT)
    public void testShouldApplyCameraCompatFreeformTreatment_overrideNotEnabled_returnsFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsCameraCompatTreatmentActiveForTopActivity(false);
        });
    }

    @Test
    @EnableFlags({FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING,
            FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT})
    public void testShouldApplyCameraCompatFreeformTreatment_notOptedOut_returnsTrue() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsCameraCompatTreatmentActiveForTopActivity(true);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges(OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT)
    public void testShouldApplyCameraCompatFreeformTreatment_enabledByOverride_returnsTrue() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsCameraCompatTreatmentActiveForTopActivity(true);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testShouldRefreshActivity_appBoundsChanged_returnsTrue() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkShouldRefreshActivity(/* expected= */ true,
                    robot.createConfiguration(/* letterbox= */ true, /* rotation= */ 0),
                    robot.createConfiguration(/* letterbox= */ false, /* rotation= */ 0));
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testShouldRefreshActivity_displayRotationChanged_returnsTrue() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkShouldRefreshActivity(/* expected= */ true,
                    robot.createConfiguration(/* letterbox= */ true, /* rotation= */ 90),
                    robot.createConfiguration(/* letterbox= */ true, /* rotation= */ 0));
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testShouldRefreshActivity_appBoundsNorDisplayChanged_returnsFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkShouldRefreshActivity(/* expected= */ false,
                    robot.createConfiguration(/* letterbox= */ true, /* rotation= */ 0),
                    robot.createConfiguration(/* letterbox= */ true, /* rotation= */ 0));
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testOnActivityConfigurationChanging_refreshDisabledViaFlag_noRefresh() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.activity().setShouldRefreshActivityForCameraCompat(false);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging();

            robot.assertActivityRefreshRequested(/* refreshRequested */ false);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testOnActivityConfigurationChanging_cycleThroughStopDisabled() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.conf().enableCameraCompatRefreshCycleThroughStop(false);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging();

            robot.assertActivityRefreshRequested(/* refreshRequested */ true,
                    /* cycleThroughStop */ false);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testOnActivityConfigurationChanging_cycleThroughStopDisabledForApp() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.setShouldRefreshActivityViaPause(true);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging();

            robot.assertActivityRefreshRequested(/* refreshRequested */ true,
                    /* cycleThroughStop */ false);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testGetCameraCompatAspectRatio_activityNotInCameraCompat_returnsDefaultAspRatio() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_FULL_USER);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging();

            robot.checkCameraCompatAspectRatioEquals(MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testGetCameraCompatAspectRatio_activityInCameraCompat_returnsConfigAspectRatio() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            final float configAspectRatio = 1.5f;
            robot.conf().setCameraCompatAspectRatio(configAspectRatio);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging();

            robot.checkCameraCompatAspectRatioEquals(configAspectRatio);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testGetCameraCompatAspectRatio_inCameraCompatPerAppOverride_returnDefAspectRatio() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.conf().setCameraCompatAspectRatio(1.5f);
            robot.setOverrideMinAspectRatioEnabled(true);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.callOnActivityConfigurationChanging();

            robot.checkCameraCompatAspectRatioEquals(MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testOnCameraOpened_portraitActivity_sandboxesDisplayRotationAndUpdatesApp() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.activity().rotateDisplayForTopActivity(ROTATION_270);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            // This is a portrait rotation for a device with portrait natural orientation (most
            // common, currently the only one supported).
            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_0);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_ENABLE_FREEFORM_WINDOWING_TREATMENT})
    public void testOnCameraOpened_landscapeActivity_sandboxesDisplayRotationAndUpdatesApp() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_LANDSCAPE);
            robot.activity().rotateDisplayForTopActivity(ROTATION_0);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            // This is a landscape rotation for a device with portrait natural orientation (most
            // common, currently the only one supported).
            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_90);
        });
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    void runTestScenario(@NonNull Consumer<CameraCompatFreeformPolicyRobotTests> consumer) {
        final CameraCompatFreeformPolicyRobotTests robot =
                new CameraCompatFreeformPolicyRobotTests(mWm, mAtm, mSupervisor, this);
        consumer.accept(robot);
    }

    private static class CameraCompatFreeformPolicyRobotTests extends AppCompatRobotBase {
        private final WindowTestsBase mWindowTestsBase;

        private CameraManager.AvailabilityCallback mCameraAvailabilityCallback;

        CameraCompatFreeformPolicyRobotTests(@NonNull WindowManagerService wm,
                @NonNull ActivityTaskManagerService atm,
                @NonNull ActivityTaskSupervisor supervisor,
                @NonNull WindowTestsBase windowTestsBase) {
            super(wm, atm, supervisor);
            mWindowTestsBase = windowTestsBase;
            setupCameraManager();
            setupAppCompatConfiguration();
        }

        @Override
        void onPostDisplayContentCreation(@NonNull DisplayContent displayContent) {
            super.onPostDisplayContentCreation(displayContent);
            spyOn(displayContent.mAppCompatCameraPolicy);
            if (displayContent.mAppCompatCameraPolicy.mCameraCompatFreeformPolicy != null) {
                spyOn(displayContent.mAppCompatCameraPolicy.mCameraCompatFreeformPolicy);
            }
        }

        @Override
        void onPostActivityCreation(@NonNull ActivityRecord activity) {
            super.onPostActivityCreation(activity);
            setupCameraManager();
            setupHandler();
            setupMockApplicationThread();
        }

        private void setupMockApplicationThread() {
            IApplicationThread mockApplicationThread = mock(IApplicationThread.class);
            spyOn(activity().top().app);
            doReturn(mockApplicationThread).when(activity().top().app).getThread();
        }

        private Configuration createConfiguration(boolean letterbox, int rotation) {
            final Configuration configuration = createConfiguration(letterbox);
            configuration.windowConfiguration.setDisplayRotation(rotation);
            return configuration;
        }

        private Configuration createConfiguration(boolean letterbox) {
            final Configuration configuration = new Configuration();
            Rect bounds = letterbox ? new Rect(/*left*/ 300, /*top*/ 0, /*right*/ 700, /*bottom*/
                    600)
                    : new Rect(/*left*/ 0, /*top*/ 0, /*right*/ 1000, /*bottom*/ 600);
            configuration.windowConfiguration.setAppBounds(bounds);
            return configuration;
        }

        private void setupAppCompatConfiguration() {
            applyOnConf((c) -> {
                c.enableCameraCompatTreatment(true);
                c.enableCameraCompatTreatmentAtBuildTime(true);
                c.enableCameraCompatRefresh(true);
                c.enableCameraCompatRefreshCycleThroughStop(true);
                c.enableCameraCompatSplitScreenAspectRatio(false);
            });
        }

        private void setupCameraManager() {
            final CameraManager mockCameraManager = mock(CameraManager.class);
            doAnswer(invocation -> {
                mCameraAvailabilityCallback = invocation.getArgument(1);
                return null;
            }).when(mockCameraManager).registerAvailabilityCallback(
                    any(Executor.class), any(CameraManager.AvailabilityCallback.class));

            doReturn(mockCameraManager).when(mWindowTestsBase.mWm.mContext).getSystemService(
                    CameraManager.class);
        }

        private void setupHandler() {
            final Handler handler = activity().top().mWmService.mH;
            spyOn(handler);

            doAnswer(invocation -> {
                ((Runnable) invocation.getArgument(0)).run();
                return null;
            }).when(handler).postDelayed(any(Runnable.class), anyLong());
        }

        private void configureActivity(@ScreenOrientation int activityOrientation) {
            configureActivity(activityOrientation, WINDOWING_MODE_FREEFORM);
        }

        private void configureActivity(@ScreenOrientation int activityOrientation,
                @WindowingMode int windowingMode) {
            configureActivityAndDisplay(activityOrientation, ORIENTATION_PORTRAIT, windowingMode);
        }

        private void configureActivityAndDisplay(@ScreenOrientation int activityOrientation,
                @Orientation int naturalOrientation, @WindowingMode int windowingMode) {
            applyOnActivity(a -> {
                dw().allowEnterDesktopMode(true);
                a.createActivityWithComponentInNewTaskAndDisplay();
                a.setIgnoreOrientationRequest(true);
                a.rotateDisplayForTopActivity(ROTATION_90);
                a.configureTopActivity(/* minAspect */ -1, /* maxAspect */ -1,
                        activityOrientation, /* isUnresizable */ false);
                a.top().setWindowingMode(windowingMode);
                a.displayContent().setWindowingMode(windowingMode);
                a.setDisplayNaturalOrientation(naturalOrientation);
                spyOn(a.top().mAppCompatController.getCameraOverrides());
                spyOn(a.top().info);
                doReturn(a.displayContent().getDisplayInfo()).when(
                        a.displayContent().mWmService.mDisplayManagerInternal).getDisplayInfo(
                        a.displayContent().mDisplayId);
            });
        }

        private void onCameraOpened(@NonNull String cameraId, @NonNull String packageName) {
            mCameraAvailabilityCallback.onCameraOpened(cameraId, packageName);
            waitHandlerIdle();
        }

        private void onCameraClosed(@NonNull String cameraId) {
            mCameraAvailabilityCallback.onCameraClosed(cameraId);
        }

        private void waitHandlerIdle() {
            mWindowTestsBase.waitHandlerIdle(activity().displayContent().mWmService.mH);
        }

        void setInFreeformWindowingMode(boolean inFreeform) {
            doReturn(inFreeform).when(activity().top()).inFreeformWindowingMode();
        }

        void setShouldRefreshActivityViaPause(boolean enabled) {
            doReturn(enabled).when(activity().top().mAppCompatController.getCameraOverrides())
                    .shouldRefreshActivityViaPauseForCameraCompat();
        }

        void checkShouldRefreshActivity(boolean expected, Configuration newConfig,
                Configuration oldConfig) {
            assertEquals(expected, cameraCompatFreeformPolicy().shouldRefreshActivity(
                    activity().top(),  newConfig, oldConfig));
        }

        void checkCameraCompatPolicyNotCreated() {
            assertNull(cameraCompatFreeformPolicy());
        }

        void checkIsCameraRunningAndWindowingModeEligible(boolean expected) {
            assertEquals(expected, cameraCompatFreeformPolicy()
                    .isCameraRunningAndWindowingModeEligible(activity().top()));
        }

        void checkIsFreeformLetterboxingForCameraAllowed(boolean expected) {
            assertEquals(expected, cameraCompatFreeformPolicy()
                    .isFreeformLetterboxingForCameraAllowed(activity().top()));
        }

        void checkCameraCompatAspectRatioEquals(float aspectRatio) {
            assertEquals(aspectRatio,
                    cameraCompatFreeformPolicy().getCameraCompatAspectRatio(activity().top()),
                    /* delta= */ 0.001);
        }

        private void assertInCameraCompatMode(@FreeformCameraCompatMode int mode) {
            assertEquals(mode, cameraCompatFreeformPolicy().getCameraCompatMode(activity().top()));
        }

        private void assertNotInCameraCompatMode() {
            assertInCameraCompatMode(CAMERA_COMPAT_FREEFORM_NONE);
        }

        private void assertActivityRefreshRequested(boolean refreshRequested) {
            assertActivityRefreshRequested(refreshRequested, /* cycleThroughStop*/ true);
        }

        private void assertActivityRefreshRequested(boolean refreshRequested,
                boolean cycleThroughStop) {
            verify(activity().top().mAppCompatController.getCameraOverrides(),
                    times(refreshRequested ? 1 : 0)).setIsRefreshRequested(true);

            final RefreshCallbackItem refreshCallbackItem =
                    new RefreshCallbackItem(activity().top().token,
                            cycleThroughStop ? ON_STOP : ON_PAUSE);
            final ResumeActivityItem resumeActivityItem = new ResumeActivityItem(
                    activity().top().token,
                    /* isForward */ false, /* shouldSendCompatFakeFocus */ false);
            try {
                verify(activity().top().mAtmService.getLifecycleManager(),
                        times(refreshRequested ? 1 : 0))
                        .scheduleTransactionItems(activity().top().app.getThread(),
                                refreshCallbackItem, resumeActivityItem);
            } catch (RemoteException e) {
                fail(e.getMessage());
            }
        }

        private void callOnActivityConfigurationChanging() {
            callOnActivityConfigurationChanging(/* letterboxNew= */ true,
                    /* lastLetterbox= */false);
        }

        private void callOnActivityConfigurationChanging(boolean letterboxNew,
                boolean lastLetterbox) {
            activity().displayContent().mAppCompatCameraPolicy.mActivityRefresher
                    .onActivityConfigurationChanging(activity().top(),
                            /* newConfig */ createConfiguration(letterboxNew),
                            /* lastReportedConfig */ createConfiguration(lastLetterbox));
        }

        void checkIsCameraCompatTreatmentActiveForTopActivity(boolean active) {
            assertEquals(active,
                    cameraCompatFreeformPolicy().isTreatmentEnabledForActivity(activity().top(),
                            /* checkOrientation */ true));
        }

        void setOverrideMinAspectRatioEnabled(boolean enabled) {
            doReturn(enabled).when(activity().top().mAppCompatController.getCameraOverrides())
                    .isOverrideMinAspectRatioForCameraEnabled();
        }

        void assertCompatibilityInfoSentWithDisplayRotation(@Surface.Rotation int
                expectedRotation) {
            final ArgumentCaptor<CompatibilityInfo> compatibilityInfoArgumentCaptor =
                    ArgumentCaptor.forClass(CompatibilityInfo.class);
            try {
                verify(activity().top().app.getThread()).updatePackageCompatibilityInfo(
                        eq(activity().top().packageName),
                        compatibilityInfoArgumentCaptor.capture());
            } catch (RemoteException e) {
                fail(e.getMessage());
            }

            final CompatibilityInfo compatInfo = compatibilityInfoArgumentCaptor.getValue();
            assertTrue(compatInfo.isOverrideDisplayRotationRequired());
            assertEquals(expectedRotation, compatInfo.applicationDisplayRotation);
        }

        CameraCompatFreeformPolicy cameraCompatFreeformPolicy() {
            return activity().displayContent().mAppCompatCameraPolicy.mCameraCompatFreeformPolicy;
        }
    }
}
