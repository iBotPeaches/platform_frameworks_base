/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.inputmethodservice;

import static android.view.WindowInsets.Type.captionBar;

import static com.android.apps.inputmethod.simpleime.ims.InputMethodServiceWrapper.EVENT_CONFIG;
import static com.android.apps.inputmethod.simpleime.ims.InputMethodServiceWrapper.EVENT_HIDE;
import static com.android.apps.inputmethod.simpleime.ims.InputMethodServiceWrapper.EVENT_SHOW;
import static com.android.apps.inputmethod.simpleime.ims.InputMethodServiceWrapper.eventToString;
import static com.android.compatibility.common.util.SystemUtil.eventually;
import static com.android.cts.input.injectinputinprocess.InjectInputInProcessKt.clickOnViewCenter;
import static com.android.internal.inputmethod.InputMethodNavButtonFlags.IME_DRAWS_IME_NAV_BAR;
import static com.android.internal.inputmethod.InputMethodNavButtonFlags.SHOW_IME_SWITCHER_WHEN_IME_IS_SHOWN;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.os.Build;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.server.wm.DumpOnFailure;
import android.server.wm.WindowManagerStateHelper;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.WindowManagerGlobal;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.Flags;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.apps.inputmethod.simpleime.ims.InputMethodServiceWrapper;
import com.android.apps.inputmethod.simpleime.ims.InputMethodServiceWrapper.Event;
import com.android.apps.inputmethod.simpleime.testing.TestActivity;
import com.android.compatibility.common.util.GestureNavSwitchHelper;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class InputMethodServiceTest {

    private static final String TAG = "SimpleIMSTest";
    private static final String INPUT_METHOD_SERVICE_NAME = ".SimpleInputMethodService";
    private static final String INPUT_METHOD_NAV_BACK_ID =
            "android:id/input_method_nav_back";
    private static final String INPUT_METHOD_NAV_IME_SWITCHER_ID =
            "android:id/input_method_nav_ime_switcher";

    /** Timeout until the uiObject should be found. */
    private static final long TIMEOUT_MS = 5000L * Build.HW_TIMEOUT_MULTIPLIER;

    /** Timeout until the event is expected. */
    private static final long EXPECT_TIMEOUT_MS = 3000L * Build.HW_TIMEOUT_MULTIPLIER;

    /** Timeout during which the event is not expected. */
    private static final long NOT_EXCEPT_TIMEOUT_MS = 2000L * Build.HW_TIMEOUT_MULTIPLIER;

    /** Command to set showing the IME when a hardware keyboard is connected. */
    private static final String SET_SHOW_IME_WITH_HARD_KEYBOARD_CMD =
            "settings put secure " + Settings.Secure.SHOW_IME_WITH_HARD_KEYBOARD;
    /** Command to get verbose ImeTracker logging state. */
    private static final String GET_VERBOSE_IME_TRACKER_LOGGING_CMD =
            "getprop persist.debug.imetracker";
    /** Command to set verbose ImeTracker logging state. */
    private static final String SET_VERBOSE_IME_TRACKER_LOGGING_CMD =
            "setprop persist.debug.imetracker";

    /** The ids of the subtypes of SimpleIme. */
    private static final int[] SUBTYPE_IDS = new int[]{1, 2};

    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();

    private final GestureNavSwitchHelper mGestureNavSwitchHelper = new GestureNavSwitchHelper();

    private final DeviceFlagsValueProvider mFlagsValueProvider = new DeviceFlagsValueProvider();

    @Rule
    public final TestName mName = new TestName();

    @Rule
    public final DumpOnFailure mDumpOnFailure = new DumpOnFailure();

    private Instrumentation mInstrumentation;
    private UiDevice mUiDevice;
    private InputMethodManager mImm;
    private String mTargetPackageName;
    private String mInputMethodId;
    private TestActivity mActivity;
    private InputMethodServiceWrapper mInputMethodService;
    private boolean mOriginalVerboseImeTrackerLoggingEnabled;
    private boolean mOriginalShowImeWithHardKeyboardEnabled;

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mUiDevice = UiDevice.getInstance(mInstrumentation);
        mImm = mInstrumentation.getContext().getSystemService(InputMethodManager.class);
        mTargetPackageName = mInstrumentation.getTargetContext().getPackageName();
        mInputMethodId = getInputMethodId();
        mOriginalVerboseImeTrackerLoggingEnabled = getVerboseImeTrackerLogging();
        if (!mOriginalVerboseImeTrackerLoggingEnabled) {
            setVerboseImeTrackerLogging(true);
        }
        mUiDevice.setOrientationNatural();
        prepareIme();
        prepareActivity();
        // Waits for input binding ready.
        eventually(() -> {
            mInputMethodService = InputMethodServiceWrapper.getInstance();
            assertWithMessage("IME is not null").that(mInputMethodService).isNotNull();

            // The activity gets focus.
            assertWithMessage("Activity window has input focus")
                    .that(mActivity.hasWindowFocus()).isTrue();
            final var editorInfo = mInputMethodService.getCurrentInputEditorInfo();
            assertWithMessage("EditorInfo is not null").that(editorInfo).isNotNull();
            assertWithMessage("EditorInfo package matches target package")
                    .that(editorInfo.packageName).isEqualTo(mTargetPackageName);

            assertWithMessage("Input connection is started")
                    .that(mInputMethodService.getCurrentInputStarted()).isTrue();
            // The editor won't bring up the IME by default.
            assertWithMessage("IME is not shown during setup")
                    .that(mInputMethodService.getCurrentInputViewStarted()).isFalse();
        });
        // Save the original value of show_ime_with_hard_keyboard from Settings.
        mOriginalShowImeWithHardKeyboardEnabled =
                mInputMethodService.getShouldShowImeWithHardKeyboardForTesting();
    }

    @After
    public void tearDown() throws Exception {
        if (!mUiDevice.isNaturalOrientation()) {
            mUiDevice.setOrientationNatural();
        }
        mUiDevice.unfreezeRotation();
        if (!mOriginalVerboseImeTrackerLoggingEnabled) {
            setVerboseImeTrackerLogging(false);
        }
        // Change back the original value of show_ime_with_hard_keyboard in Settings.
        setShowImeWithHardKeyboard(mOriginalShowImeWithHardKeyboardEnabled);
        executeShellCommand("ime disable " + mInputMethodId);
    }

    /**
     * This checks that the IME can be shown and hidden by user actions
     * (i.e. tapping on an EditText, tapping the Home button).
     */
    @Test
    public void testShowHideKeyboard_byUserAction() {
        waitUntilActivityReadyForInputInjection(mActivity);

        setShowImeWithHardKeyboard(true /* enable */);

        // Performs click on EditText to bring up the IME.
        Log.i(TAG, "Click on EditText");
        verifyInputViewStatus(
                () -> clickOnViewCenter(mActivity.getEditText()),
                EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");

        // Press home key to hide IME.
        Log.i(TAG, "Press home");
        if (mFlagsValueProvider.getBoolean(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)) {
            assertWithMessage("Home key press was handled").that(mUiDevice.pressHome()).isTrue();
            // This doesn't call verifyInputViewStatus, as the refactor delays the events such that
            // getCurrentInputStarted() would be false, as we would already be in launcher.
            // The IME visibility is only sent at the end of the animation. Therefore, we have to
            // wait until the visibility was sent to the server and the IME window hidden.
            eventually(() -> assertWithMessage("IME is not shown")
                    .that(mInputMethodService.isInputViewShown()).isFalse());
        } else {
            verifyInputViewStatus(
                    () -> assertWithMessage("Home key press was handled")
                            .that(mUiDevice.pressHome()).isTrue(),
                    EVENT_HIDE, true /* eventExpected */, false /* shown */, "IME is not shown");
        }
    }

    /**
     * This checks that the IME can be shown and hidden using the InputMethodManager APIs.
     */
    @Test
    public void testShowHideKeyboard_byInputMethodManager() {
        setShowImeWithHardKeyboard(true /* enable */);

        verifyInputViewStatusOnMainSync(
                () -> assertThat(mActivity.showImeWithInputMethodManager(0 /* flags */)).isTrue(),
                EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");

        verifyInputViewStatusOnMainSync(
                () -> assertThat(mActivity.hideImeWithInputMethodManager(0 /* flags */)).isTrue(),
                EVENT_HIDE, true /* eventExpected */, false /* shown */, "IME is not shown");
    }

    /**
     * This checks that the IME can be shown and hidden using the WindowInsetsController APIs.
     */
    @Test
    public void testShowHideKeyboard_byInsetsController() {
        setShowImeWithHardKeyboard(true /* enable */);

        verifyInputViewStatusOnMainSync(
                () -> mActivity.showImeWithWindowInsetsController(),
                EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");

        verifyInputViewStatusOnMainSync(
                () -> mActivity.hideImeWithWindowInsetsController(),
                EVENT_HIDE, true /* eventExpected */, false /* shown */, "IME is not shown");
    }

    /**
     * This checks that the surface is removed after the window was hidden in
     * InputMethodService#hideSoftInput
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)
    public void testSurfaceRemovedAfterHideSoftInput() {
        setShowImeWithHardKeyboard(true /* enabled */);

        // Triggers to show IME via public API.
        verifyInputViewStatusOnMainSync(() -> mActivity.showImeWithWindowInsetsController(),
                EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");
        assertWithMessage("IME is shown").that(mInputMethodService.isInputViewShown()).isTrue();

        final var window = mInputMethodService.getWindow().getWindow();
        assertWithMessage("IME window exists").that(window).isNotNull();
        eventually(() -> assertWithMessage("IME window showing").that(
                window.getDecorView().getVisibility()).isEqualTo(View.VISIBLE));

        mActivity.getWindow().getDecorView().setWindowInsetsAnimationCallback(
                new WindowInsetsAnimation.Callback(
                        WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    @NonNull
                    @Override
                    public WindowInsetsAnimation.Bounds onStart(
                            @NonNull WindowInsetsAnimation animation,
                            @NonNull WindowInsetsAnimation.Bounds bounds) {
                        return super.onStart(animation, bounds);
                    }

                    @NonNull
                    @Override
                    public WindowInsets onProgress(@NonNull WindowInsets insets,
                            @NonNull List<WindowInsetsAnimation> runningAnimations) {
                        assertWithMessage("IME surface not removed during the animation").that(
                                window.getDecorView().getVisibility()).isEqualTo(View.VISIBLE);
                        return insets;
                    }

                    @Override
                    public void onEnd(@NonNull WindowInsetsAnimation animation) {
                        assertWithMessage(
                                "IME surface not removed before the end of the animation").that(
                                window.getDecorView().getVisibility()).isEqualTo(View.VISIBLE);
                        super.onEnd(animation);
                    }
                });

        // Triggers to hide IME via public API.
        verifyInputViewStatusOnMainSync(() -> mActivity.hideImeWithWindowInsetsController(),
                EVENT_HIDE, true /* eventExpected */, false /* shown */, "IME is not shown");
        eventually(() -> assertWithMessage("IME window not showing").that(
                window.getDecorView().getVisibility()).isEqualTo(View.GONE));
    }

    /**
     * This checks the result of calling IMS#requestShowSelf and IMS#requestHideSelf.
     */
    @Test
    public void testShowHideSelf() {
        setShowImeWithHardKeyboard(true /* enable */);

        verifyInputViewStatusOnMainSync(
                () -> mInputMethodService.requestShowSelf(0 /* flags */),
                EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");

        verifyInputViewStatusOnMainSync(
                () -> mInputMethodService.requestHideSelf(0 /* flags */),
                EVENT_HIDE, true /* eventExpected */, false /* shown */, "IME is not shown");
    }

    /**
     * This checks the return value of IMS#onEvaluateInputViewShown,
     * when show_ime_with_hard_keyboard is enabled.
     */
    @Test
    public void testOnEvaluateInputViewShown_showImeWithHardKeyboard() {
        setShowImeWithHardKeyboard(true /* enable */);

        final var config = mInputMethodService.getResources().getConfiguration();
        final var initialConfig = new Configuration(config);
        try {
            config.keyboard = Configuration.KEYBOARD_QWERTY;
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO;
            assertWithMessage("InputView should show with visible hardware keyboard")
                    .that(mInputMethodService.onEvaluateInputViewShown()).isTrue();

            config.keyboard = Configuration.KEYBOARD_NOKEYS;
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO;
            assertWithMessage("InputView should show without hardware keyboard")
                    .that(mInputMethodService.onEvaluateInputViewShown()).isTrue();

            config.keyboard = Configuration.KEYBOARD_QWERTY;
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES;
            assertWithMessage("InputView should show with hidden hardware keyboard")
                    .that(mInputMethodService.onEvaluateInputViewShown()).isTrue();
        } finally {
            mInputMethodService.getResources()
                    .updateConfiguration(initialConfig, null /* metrics */, null /* compat */);
        }
    }

    /**
     * This checks the return value of IMS#onEvaluateInputViewShown,
     * when show_ime_with_hard_keyboard is disabled.
     */
    @Test
    public void testOnEvaluateInputViewShown_disableShowImeWithHardKeyboard() {
        setShowImeWithHardKeyboard(false /* enable */);

        final var config = mInputMethodService.getResources().getConfiguration();
        final var initialConfig = new Configuration(config);
        try {
            config.keyboard = Configuration.KEYBOARD_QWERTY;
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO;
            assertWithMessage("InputView should not show with visible hardware keyboard")
                    .that(mInputMethodService.onEvaluateInputViewShown()).isFalse();

            config.keyboard = Configuration.KEYBOARD_NOKEYS;
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO;
            assertWithMessage("InputView should show without hardware keyboard")
                    .that(mInputMethodService.onEvaluateInputViewShown()).isTrue();

            config.keyboard = Configuration.KEYBOARD_QWERTY;
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES;
            assertWithMessage("InputView should show with hidden hardware keyboard")
                    .that(mInputMethodService.onEvaluateInputViewShown()).isTrue();
        } finally {
            mInputMethodService.getResources()
                    .updateConfiguration(initialConfig, null /* metrics */, null /* compat */);
        }
    }

    /**
     * This checks that any (implicit or explicit) show request,
     * when IMS#onEvaluateInputViewShown returns false, results in the IME not being shown.
     */
    @Test
    public void testShowSoftInput_disableShowImeWithHardKeyboard() {
        setShowImeWithHardKeyboard(false /* enable */);

        final var config = mInputMethodService.getResources().getConfiguration();
        final var initialConfig = new Configuration(config);
        try {
            // Simulate connecting a hardware keyboard.
            config.keyboard = Configuration.KEYBOARD_QWERTY;
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO;

            // When InputMethodService#onEvaluateInputViewShown() returns false, the IME should
            // not be shown no matter what the show flag is.
            verifyInputViewStatusOnMainSync(() -> assertThat(
                            mActivity.showImeWithInputMethodManager(
                                    InputMethodManager.SHOW_IMPLICIT)).isTrue(),
                    EVENT_SHOW, false /* eventExpected */, false /* shown */,
                    "IME is not shown after SHOW_IMPLICIT");

            verifyInputViewStatusOnMainSync(
                    () -> assertThat(mActivity.showImeWithInputMethodManager(0 /* flags */))
                            .isTrue(),
                    EVENT_SHOW, false /* eventExpected */, false /* shown */,
                    "IME is not shown after SHOW_EXPLICIT");
        } finally {
            mInputMethodService.getResources()
                    .updateConfiguration(initialConfig, null /* metrics */, null /* compat */);
        }
    }

    /**
     * This checks that an explicit show request when the IME is not previously shown,
     * and it should be shown in fullscreen mode, results in the IME being shown.
     */
    @Test
    public void testShowSoftInputExplicitly_fullScreenMode() {
        setShowImeWithHardKeyboard(true /* enable */);

        // Set orientation landscape to enable fullscreen mode.
        setOrientation(2);
        eventually(() -> assertWithMessage("Activity was re-created after rotation")
                .that(TestActivity.getInstance()).isNotEqualTo(mActivity));
        mActivity = TestActivity.getInstance();
        assertWithMessage("Re-created activity is not null").that(mActivity).isNotNull();
        // Wait for the new EditText to be served by InputMethodManager.
        eventually(() -> assertWithMessage("Has an input connection to the re-created Activity")
                .that(mImm.hasActiveInputConnection(mActivity.getEditText())).isTrue());

        verifyInputViewStatusOnMainSync(() -> assertThat(
                        mActivity.showImeWithInputMethodManager(0 /* flags */)).isTrue(),
                EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");
    }

    /**
     * This checks that an implicit show request when the IME is not previously shown,
     * and it should be shown in fullscreen mode behaves like an explicit show request, resulting
     * in the IME being shown. This is due to the refactor in b/298172246, causing us to lose flag
     * information like {@link InputMethodManager#SHOW_IMPLICIT}.
     *
     * <p>Previously, an implicit show request when the IME is not previously shown,
     * and it should be shown in fullscreen mode, would result in the IME not being shown.
     */
    @Test
    public void testShowSoftInputImplicitly_fullScreenMode() {
        setShowImeWithHardKeyboard(true /* enable */);

        // Set orientation landscape to enable fullscreen mode.
        setOrientation(2);
        eventually(() -> assertWithMessage("Activity was re-created after rotation")
                .that(TestActivity.getInstance()).isNotEqualTo(mActivity));
        mActivity = TestActivity.getInstance();
        assertWithMessage("Re-created activity is not null").that(mActivity).isNotNull();
        // Wait for the new EditText to be served by InputMethodManager.
        eventually(() -> assertWithMessage("Has an input connection to the re-created Activity")
                .that(mImm.hasActiveInputConnection(mActivity.getEditText())).isTrue());

        if (mFlagsValueProvider.getBoolean(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)) {
            verifyInputViewStatusOnMainSync(() -> assertThat(
                            mActivity.showImeWithInputMethodManager(
                                    InputMethodManager.SHOW_IMPLICIT))
                            .isTrue(),
                    EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");
        } else {
            verifyInputViewStatusOnMainSync(() -> assertThat(
                            mActivity.showImeWithInputMethodManager(
                                    InputMethodManager.SHOW_IMPLICIT))
                            .isTrue(),
                    EVENT_SHOW, false /* eventExpected */, false /* shown */, "IME is not shown");
        }
    }

    /**
     * This checks that an explicit show request when a hardware keyboard is connected,
     * results in the IME being shown.
     */
    @Test
    public void testShowSoftInputExplicitly_withHardKeyboard() {
        setShowImeWithHardKeyboard(false /* enable */);

        final var config = mInputMethodService.getResources().getConfiguration();
        final var initialConfig = new Configuration(config);
        try {
            // Simulate connecting a hardware keyboard.
            config.keyboard = Configuration.KEYBOARD_QWERTY;
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES;

            verifyInputViewStatusOnMainSync(() -> assertThat(
                            mActivity.showImeWithInputMethodManager(0 /* flags */)).isTrue(),
                    EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");
        } finally {
            mInputMethodService.getResources()
                    .updateConfiguration(initialConfig, null /* metrics */, null /* compat */);
        }
    }

    /**
     * This checks that an implicit show request when a hardware keyboard is connected behaves
     * like an explicit show request, resulting in the IME being shown. This is due to the
     * refactor in b/298172246, causing us to lose flag information like
     * {@link InputMethodManager#SHOW_IMPLICIT}.
     *
     * <p>Previously, an implicit show request when a hardware keyboard is connected would
     * result in the IME not being shown.
     */
    @Test
    public void testShowSoftInputImplicitly_withHardKeyboard() {
        setShowImeWithHardKeyboard(false /* enable */);

        final var config = mInputMethodService.getResources().getConfiguration();
        final var initialConfig = new Configuration(config);
        try {
            // Simulate connecting a hardware keyboard.
            config.keyboard = Configuration.KEYBOARD_QWERTY;
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES;

            if (mFlagsValueProvider.getBoolean(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)) {
                verifyInputViewStatusOnMainSync(() -> assertThat(
                                mActivity.showImeWithInputMethodManager(
                                        InputMethodManager.SHOW_IMPLICIT))
                                .isTrue(),
                        EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");
            } else {
                verifyInputViewStatusOnMainSync(() -> assertThat(
                                mActivity.showImeWithInputMethodManager(
                                        InputMethodManager.SHOW_IMPLICIT))
                                .isTrue(),
                        EVENT_SHOW, false /* eventExpected */, false /* shown */,
                        "IME is not shown");
            }
        } finally {
            mInputMethodService.getResources()
                    .updateConfiguration(initialConfig, null /* metrics */, null /* compat */);
        }
    }

    /**
     * This checks that an explicit show request followed by connecting a hardware keyboard
     * and a configuration change, still results in the IME being shown.
     */
    @Test
    public void testShowSoftInputExplicitly_thenConfigurationChanged() {
        setShowImeWithHardKeyboard(false /* enable */);

        final var config = mInputMethodService.getResources().getConfiguration();
        final var initialConfig = new Configuration(config);
        try {
            // Start with no hardware keyboard.
            config.keyboard = Configuration.KEYBOARD_NOKEYS;
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES;

            verifyInputViewStatusOnMainSync(
                    () -> assertThat(mActivity.showImeWithInputMethodManager(0 /* flags */))
                            .isTrue(),
                    EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");

            // Simulate connecting a hardware keyboard.
            config.keyboard = Configuration.KEYBOARD_QWERTY;
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES;

            // Simulate a fake configuration change to avoid the recreation of TestActivity.
            config.orientation = Configuration.ORIENTATION_LANDSCAPE;

            verifyInputViewStatusOnMainSync(
                    () -> mInputMethodService.onConfigurationChanged(config),
                    EVENT_CONFIG, true /* eventExpected */, true /* shown */,
                    "IME is still shown after a configuration change");
        } finally {
            mInputMethodService.getResources()
                    .updateConfiguration(initialConfig, null /* metrics */, null /* compat */);
        }
    }

    /**
     * This checks that an implicit show request followed by connecting a hardware keyboard
     * and a configuration change behaves like an explicit show request, resulting in the IME
     * still being shown. This is due to the refactor in b/298172246, causing us to lose flag
     * information like {@link InputMethodManager#SHOW_IMPLICIT}.
     *
     * <p>Previously, an implicit show request followed by connecting a hardware keyboard
     * and a configuration change, would not trigger IMS#onFinishInputView, but resulted in the
     * IME being hidden.
     */
    @Test
    public void testShowSoftInputImplicitly_thenConfigurationChanged() {
        setShowImeWithHardKeyboard(false /* enable */);

        final var config = mInputMethodService.getResources().getConfiguration();
        final var initialConfig = new Configuration(config);
        try {
            // Start with no hardware keyboard.
            config.keyboard = Configuration.KEYBOARD_NOKEYS;
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES;

            verifyInputViewStatusOnMainSync(() -> assertThat(
                            mActivity.showImeWithInputMethodManager(
                                    InputMethodManager.SHOW_IMPLICIT)).isTrue(),
                    EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");

            // Simulate connecting a hardware keyboard.
            config.keyboard = Configuration.KEYBOARD_QWERTY;
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES;

            // Simulate a fake configuration change to avoid the recreation of TestActivity.
            config.orientation = Configuration.ORIENTATION_LANDSCAPE;

            if (mFlagsValueProvider.getBoolean(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)) {
                verifyInputViewStatusOnMainSync(
                        () -> mInputMethodService.onConfigurationChanged(config),
                        EVENT_CONFIG, true /* eventExpected */, true /* shown */,
                        "IME is still shown after a configuration change");
            } else {
                // Normally, IMS#onFinishInputView will be called when finishing the input view by
                // the user. But if IMS#hideWindow is called when receiving a new configuration
                // change, we don't expect that it's user-driven to finish the lifecycle of input
                // view with IMS#onFinishInputView, because the input view will be re-initialized
                // according to the last #mShowInputRequested state. So in this case we treat the
                // input view as still alive.
                verifyInputViewStatusOnMainSync(
                        () -> mInputMethodService.onConfigurationChanged(config),
                        EVENT_CONFIG, true /* eventExpected */, true /* inputViewStarted */,
                        false /* shown */, "IME is not shown after a configuration change");
            }
        } finally {
            mInputMethodService.getResources()
                    .updateConfiguration(initialConfig, null /* metrics */, null /* compat */);
        }
    }

    /**
     * This checks that an explicit show request directly followed by an implicit show request,
     * while a hardware keyboard is connected, still results in the IME being shown
     * (i.e. the implicit show request is treated as explicit).
     */
    @Test
    public void testShowSoftInputExplicitly_thenShowSoftInputImplicitly_withHardKeyboard() {
        setShowImeWithHardKeyboard(false /* enable */);

        final var config = mInputMethodService.getResources().getConfiguration();
        final var initialConfig = new Configuration(config);
        try {
            // Simulate connecting a hardware keyboard.
            config.keyboard = Configuration.KEYBOARD_QWERTY;
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES;

            verifyInputViewStatusOnMainSync(() -> assertThat(
                            mActivity.showImeWithInputMethodManager(0 /* flags */)).isTrue(),
                    EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");

            verifyInputViewStatusOnMainSync(() -> assertThat(
                            mActivity.showImeWithInputMethodManager(
                                    InputMethodManager.SHOW_IMPLICIT)).isTrue(),
                    EVENT_SHOW, false /* eventExpected */, true /* shown */, "IME is still shown");

            // Simulate a fake configuration change to avoid the recreation of TestActivity.
            // This should now consider the implicit show request, but keep the state from the
            // explicit show request, and thus not hide the IME.
            verifyInputViewStatusOnMainSync(
                    () -> mInputMethodService.onConfigurationChanged(config),
                    EVENT_CONFIG, true /* eventExpected */, true /* shown */,
                    "IME is still shown after a configuration change");
        } finally {
            mInputMethodService.getResources()
                    .updateConfiguration(initialConfig, null /* metrics */, null /* compat */);
        }
    }

    /**
     * This checks that a forced show request directly followed by an explicit show request,
     * and then a not always hide request behaves like a normal hide request, resulting in the
     * IME being hidden (i.e. the explicit show request does not retain the forced state). This is
     * due to the refactor in b/298172246, causing us to lose flag information like
     * {@link InputMethodManager#SHOW_FORCED}.
     *
     * <p>Previously, a forced show request directly followed by an explicit show request,
     * and then a not always hide request, would result in the IME still being shown
     * (i.e. the explicit show request would retain the forced state).
     */
    @Test
    public void testShowSoftInputForced_testShowSoftInputExplicitly_thenHideSoftInputNotAlways() {
        setShowImeWithHardKeyboard(true /* enable */);

        verifyInputViewStatusOnMainSync(() -> assertThat(
                mActivity.showImeWithInputMethodManager(InputMethodManager.SHOW_FORCED)).isTrue(),
                EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");

        verifyInputViewStatusOnMainSync(() -> assertThat(
                        mActivity.showImeWithInputMethodManager(0 /* flags */)).isTrue(),
                EVENT_SHOW, false /* eventExpected */, true /* shown */, "IME is still shown");

        if (mFlagsValueProvider.getBoolean(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)) {
            verifyInputViewStatusOnMainSync(() -> assertThat(mActivity
                            .hideImeWithInputMethodManager(InputMethodManager.HIDE_NOT_ALWAYS))
                            .isTrue(),
                    EVENT_HIDE, true /* eventExpected */, false /* shown */,
                    "IME is not shown after HIDE_NOT_ALWAYS");
        } else {
            verifyInputViewStatusOnMainSync(() -> assertThat(mActivity
                            .hideImeWithInputMethodManager(InputMethodManager.HIDE_NOT_ALWAYS))
                            .isTrue(),
                    EVENT_HIDE, false /* eventExpected */, true /* shown */,
                    "IME is still shown after HIDE_NOT_ALWAYS");
        }
    }

    /**
     * This checks that an explicit show request followed by an implicit only hide request
     * behaves like a normal hide request, resulting in the IME being hidden. This is due to
     * the refactor in b/298172246, causing us to lose flag information like
     * {@link InputMethodManager#SHOW_IMPLICIT} and {@link InputMethodManager#HIDE_IMPLICIT_ONLY}.
     *
     * <p>Previously, an explicit show request followed by an implicit only hide request
     * would result in the IME still being shown.
     */
    @Test
    public void testShowSoftInputExplicitly_thenHideSoftInputImplicitOnly() {
        setShowImeWithHardKeyboard(true /* enable */);

        verifyInputViewStatusOnMainSync(
                () -> mActivity.showImeWithInputMethodManager(0 /* flags */),
                EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");

        if (mFlagsValueProvider.getBoolean(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)) {
            verifyInputViewStatusOnMainSync(
                    () -> mActivity.hideImeWithInputMethodManager(
                            InputMethodManager.HIDE_IMPLICIT_ONLY),
                    EVENT_HIDE, true /* eventExpected */, false /* shown */,
                    "IME is not shown after HIDE_IMPLICIT_ONLY");
        } else {
            verifyInputViewStatusOnMainSync(
                    () -> mActivity.hideImeWithInputMethodManager(
                            InputMethodManager.HIDE_IMPLICIT_ONLY),
                    EVENT_HIDE, false /* eventExpected */, true /* shown */,
                    "IME is still shown after HIDE_IMPLICIT_ONLY");
        }
    }

    /**
     * This checks that an implicit show request followed by an implicit only hide request
     * results in the IME being hidden.
     */
    @Test
    public void testShowSoftInputImplicitly_thenHideSoftInputImplicitOnly() {
        setShowImeWithHardKeyboard(true /* enable */);

        verifyInputViewStatusOnMainSync(
                () -> mActivity.showImeWithInputMethodManager(InputMethodManager.SHOW_IMPLICIT),
                EVENT_SHOW, true /* eventExpected */, true /* shown */,
                "IME is shown with SHOW_IMPLICIT");

        verifyInputViewStatusOnMainSync(
                () -> mActivity.hideImeWithInputMethodManager(
                        InputMethodManager.HIDE_IMPLICIT_ONLY),
                EVENT_HIDE, true /* eventExpected */, false /* shown */,
                "IME is not shown after HIDE_IMPLICIT_ONLY");
    }

    /**
     * This checks that an explicit show self request followed by an implicit only hide self request
     * behaves like a normal hide self request, resulting in the IME being hidden. This is due to
     * the refactor in b/298172246, causing us to lose flag information like
     * {@link InputMethodManager#SHOW_IMPLICIT} and {@link InputMethodManager#HIDE_IMPLICIT_ONLY}.
     *
     * <p>Previously, an explicit show self request followed by an implicit only hide self request
     * would result in the IME still being shown.
     */
    @Test
    public void testShowSelfExplicitly_thenHideSelfImplicitOnly() {
        setShowImeWithHardKeyboard(true /* enable */);

        verifyInputViewStatusOnMainSync(
                () -> mInputMethodService.requestShowSelf(0 /* flags */),
                EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");

        if (mFlagsValueProvider.getBoolean(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)) {
            verifyInputViewStatusOnMainSync(
                    () -> mInputMethodService.requestHideSelf(
                            InputMethodManager.HIDE_IMPLICIT_ONLY),
                    EVENT_HIDE, true /* eventExpected */, false /* shown */,
                    "IME is not shown after HIDE_IMPLICIT_ONLY");
        } else {
            verifyInputViewStatusOnMainSync(
                    () -> mInputMethodService.requestHideSelf(
                            InputMethodManager.HIDE_IMPLICIT_ONLY),
                    EVENT_HIDE, false /* eventExpected */, true /* shown */,
                    "IME is still shown after HIDE_IMPLICIT_ONLY");
        }
    }

    /**
     * This checks that an implicit show self request followed by an implicit only hide self request
     * results in the IME being hidden.
     */
    @Test
    public void testShowSelfImplicitly_thenHideSelfImplicitOnly() {
        setShowImeWithHardKeyboard(true /* enable */);

        verifyInputViewStatusOnMainSync(
                () -> mInputMethodService.requestShowSelf(InputMethodManager.SHOW_IMPLICIT),
                EVENT_SHOW, true /* eventExpected */, true /* shown */,
                "IME is shown with SHOW_IMPLICIT");

        verifyInputViewStatusOnMainSync(
                () -> mInputMethodService.requestHideSelf(
                        InputMethodManager.HIDE_IMPLICIT_ONLY),
                EVENT_HIDE, true /* eventExpected */, false /* shown */,
                "IME is not shown after HIDE_IMPLICIT_ONLY");
    }

    /**
     * This checks that the IME fullscreen mode state is updated after changing orientation.
     */
    @Test
    public void testFullScreenMode() {
        setShowImeWithHardKeyboard(true /* enable */);

        Log.i(TAG, "Set orientation natural");
        verifyFullscreenMode(() -> setOrientation(0), false /* eventExpected */,
                true /* orientationPortrait */);

        Log.i(TAG, "Set orientation left");
        verifyFullscreenMode(() -> setOrientation(1), true /* eventExpected */,
                false /* orientationPortrait */);

        Log.i(TAG, "Set orientation right");
        verifyFullscreenMode(() -> setOrientation(2), false /* eventExpected */,
                false /* orientationPortrait */);
    }

    /**
     * This checks that when the system navigation bar is not created (e.g. emulator),
     * then the IME caption bar is also not created.
     */
    @Test
    public void testNoNavigationBar_thenImeNoCaptionBar() {
        assumeFalse("Must not have a navigation bar", hasNavigationBar());

        assertWithMessage("No IME caption bar insets")
                .that(mInputMethodService.getWindow().getWindow().getDecorView()
                        .getRootWindowInsets().getInsetsIgnoringVisibility(captionBar()))
                .isEqualTo(Insets.NONE);
    }

    /**
     * This checks that trying to show and hide the navigation bar takes effect
     * when the IME does draw the IME navigation bar.
     */
    @Test
    public void testShowHideImeNavigationBar_doesDrawImeNavBar() {
        assumeTrue("Must have a navigation bar", hasNavigationBar());

        setShowImeWithHardKeyboard(true /* enable */);

        verifyInputViewStatusOnMainSync(
                () -> {
                    setDrawsImeNavBarAndSwitcherButton(true /* enable */);
                    mActivity.showImeWithWindowInsetsController();
                },
                EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");
        assertWithMessage("IME navigation bar is initially shown")
                .that(mInputMethodService.isImeNavigationBarShownForTesting()).isTrue();

        mInstrumentation.runOnMainSync(() -> setShowImeNavigationBar(false /* show */));
        mInstrumentation.waitForIdleSync();
        assertWithMessage("IME navigation bar is not shown after hide request")
                .that(mInputMethodService.isImeNavigationBarShownForTesting()).isFalse();

        mInstrumentation.runOnMainSync(() -> setShowImeNavigationBar(true /* show */));
        mInstrumentation.waitForIdleSync();
        assertWithMessage("IME navigation bar is shown after show request")
                .that(mInputMethodService.isImeNavigationBarShownForTesting()).isTrue();
    }

    /**
     * This checks that trying to show and hide the navigation bar has no effect
     * when the IME does not draw the IME navigation bar.
     *
     * <p>Note: The IME navigation bar is *never* visible in 3 button navigation mode.
     */
    @Test
    public void testShowHideImeNavigationBar_doesNotDrawImeNavBar() {
        assumeTrue("Must have a navigation bar", hasNavigationBar());

        setShowImeWithHardKeyboard(true /* enable */);

        verifyInputViewStatusOnMainSync(
                () -> {
                    setDrawsImeNavBarAndSwitcherButton(false /* enable */);
                    mActivity.showImeWithWindowInsetsController();
                },
                EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");
        assertWithMessage("IME navigation bar is initially not shown")
                .that(mInputMethodService.isImeNavigationBarShownForTesting()).isFalse();

        mInstrumentation.runOnMainSync(() -> setShowImeNavigationBar(false /* show */));
        mInstrumentation.waitForIdleSync();
        assertWithMessage("IME navigation bar is not shown after hide request")
                .that(mInputMethodService.isImeNavigationBarShownForTesting()).isFalse();

        mInstrumentation.runOnMainSync(() -> setShowImeNavigationBar(true /* show */));
        mInstrumentation.waitForIdleSync();
        assertWithMessage("IME navigation bar is not shown after show request")
                .that(mInputMethodService.isImeNavigationBarShownForTesting()).isFalse();
    }

    /**
     * Verifies that clicking on the IME navigation bar back button hides the IME.
     */
    @Test
    public void testBackButtonClick() throws Exception {
        assumeTrue("Must have a navigation bar", hasNavigationBar());

        waitUntilActivityReadyForInputInjection(mActivity);

        setShowImeWithHardKeyboard(true /* enable */);

        try (var ignored = mGestureNavSwitchHelper.withGestureNavigationMode()) {
            verifyInputViewStatusOnMainSync(
                    () -> mActivity.showImeWithWindowInsetsController(),
                    EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");

            eventually(() -> assertWithMessage("IME navigation bar is shown")
                    .that(mInputMethodService.isImeNavigationBarShownForTesting()).isTrue());

            final var backButton = getUiObject(By.res(INPUT_METHOD_NAV_BACK_ID));
            verifyInputViewStatus(
                    () -> {
                        backButton.click();
                        mInstrumentation.waitForIdleSync();
                    },
                    EVENT_HIDE, true /* eventExpected */, false /* shown */, "IME is not shown");
        }
    }

    /**
     * Verifies that long clicking on the IME navigation bar back button hides the IME.
     */
    @Test
    public void testBackButtonLongClick() throws Exception {
        assumeTrue("Must have a navigation bar", hasNavigationBar());

        waitUntilActivityReadyForInputInjection(mActivity);

        setShowImeWithHardKeyboard(true /* enable */);

        try (var ignored = mGestureNavSwitchHelper.withGestureNavigationMode()) {
            verifyInputViewStatusOnMainSync(
                    () -> mActivity.showImeWithWindowInsetsController(),
                    EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");

            eventually(() -> assertWithMessage("IME navigation bar is shown")
                    .that(mInputMethodService.isImeNavigationBarShownForTesting()).isTrue());

            final var backButton = getUiObject(By.res(INPUT_METHOD_NAV_BACK_ID));
            verifyInputViewStatus(
                    () -> {
                        backButton.longClick();
                        mInstrumentation.waitForIdleSync();
                    },
                    EVENT_HIDE, true /* eventExpected */, false /* shown */, "IME is not shown");
        }
    }

    /**
     * Verifies that clicking on the IME switch button switches the input method subtype.
     */
    @Test
    public void testImeSwitchButtonClick() throws Exception {
        assumeTrue("Must have a navigation bar", hasNavigationBar());

        waitUntilActivityReadyForInputInjection(mActivity);

        setShowImeWithHardKeyboard(true /* enable */);

        final var info = mImm.getCurrentInputMethodInfo();
        assertWithMessage("InputMethodInfo is not null").that(info).isNotNull();
        mImm.setExplicitlyEnabledInputMethodSubtypes(info.getId(), SUBTYPE_IDS);

        final var initialSubtype = mImm.getCurrentInputMethodSubtype();

        try (var ignored = mGestureNavSwitchHelper.withGestureNavigationMode()) {
            verifyInputViewStatusOnMainSync(
                    () -> {
                        setDrawsImeNavBarAndSwitcherButton(true /* enable */);
                        mActivity.showImeWithWindowInsetsController();
                    },
                    EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");

            eventually(() -> assertWithMessage("IME navigation bar is shown")
                    .that(mInputMethodService.isImeNavigationBarShownForTesting()).isTrue());

            final var imeSwitcherButton = getUiObject(By.res(INPUT_METHOD_NAV_IME_SWITCHER_ID));
            imeSwitcherButton.click();
            mInstrumentation.waitForIdleSync();

            final var newSubtype = mImm.getCurrentInputMethodSubtype();

            assertWithMessage("Input method subtype was switched")
                    .that(!Objects.equals(initialSubtype, newSubtype))
                    .isTrue();

            assertWithMessage("IME is still shown after IME Switcher button was clicked")
                    .that(mInputMethodService.isInputViewShown()).isTrue();
        }
    }

    /**
     * Verifies that long clicking on the IME switch button shows the Input Method Switcher Menu.
     */
    @Test
    public void testImeSwitchButtonLongClick() throws Exception {
        assumeTrue("Must have a navigation bar", hasNavigationBar());

        waitUntilActivityReadyForInputInjection(mActivity);

        setShowImeWithHardKeyboard(true /* enable */);

        final var info = mImm.getCurrentInputMethodInfo();
        assertWithMessage("InputMethodInfo is not null").that(info).isNotNull();
        mImm.setExplicitlyEnabledInputMethodSubtypes(info.getId(), SUBTYPE_IDS);

        try (var ignored = mGestureNavSwitchHelper.withGestureNavigationMode()) {
            verifyInputViewStatusOnMainSync(
                    () -> {
                        setDrawsImeNavBarAndSwitcherButton(true /* enable */);
                        mActivity.showImeWithWindowInsetsController();
                    },
                    EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");

            eventually(() -> assertWithMessage("IME navigation bar is shown")
                    .that(mInputMethodService.isImeNavigationBarShownForTesting()).isTrue());

            final var imeSwitcherButton = getUiObject(By.res(INPUT_METHOD_NAV_IME_SWITCHER_ID));
            imeSwitcherButton.longClick();
            mInstrumentation.waitForIdleSync();

            assertWithMessage("Input Method Switcher Menu is shown")
                    .that(isInputMethodPickerShown(mImm)).isTrue();
            assertWithMessage("IME is still shown after IME Switcher button was long clicked")
                    .that(mInputMethodService.isInputViewShown()).isTrue();

            // Hide the IME Switcher Menu before finishing.
            mUiDevice.pressBack();
        }
    }

    private void verifyInputViewStatus(@NonNull Runnable runnable, @Event int eventType,
            boolean eventExpected, boolean shown, @NonNull String message) {
        verifyInputViewStatusInternal(runnable, eventType, eventExpected,
                shown /* inputViewStarted */, shown, message, false /* runOnMainSync */);
    }

    private void verifyInputViewStatusOnMainSync(@NonNull Runnable runnable, @Event int eventType,
            boolean eventExpected, boolean shown, @NonNull String message) {
        verifyInputViewStatusInternal(runnable, eventType, eventExpected,
                shown /* inputViewStarted */, shown, message, true /* runOnMainSync */);
    }

    private void verifyInputViewStatusOnMainSync(@NonNull Runnable runnable, @Event int eventType,
            boolean eventExpected, boolean inputViewStarted, boolean shown,
            @NonNull String message) {
        verifyInputViewStatusInternal(runnable, eventType, eventExpected, inputViewStarted, shown,
                message, true /* runOnMainSync */);
    }

    /**
     * Verifies the status of the Input View after executing the given runnable, and waiting that
     * the event was either called or not.
     *
     * @param runnable         the runnable to call the event.
     * @param eventType        the event type to wait for.
     * @param eventExpected    whether the event is expected to be called.
     * @param inputViewStarted whether the input view is expected to be started.
     * @param shown            whether the input view is expected to be shown.
     * @param message          the message for the input view shown assertion.
     * @param runOnMainSync    whether to execute the runnable on the main thread.
     */
    private void verifyInputViewStatusInternal(@NonNull Runnable runnable, @Event int eventType,
            boolean eventExpected, boolean inputViewStarted, boolean shown, @NonNull String message,
            boolean runOnMainSync) {
        final boolean eventCalled;
        try {
            final var latch = new CountDownLatch(1);
            mInputMethodService.setCountDownLatchForTesting(latch, eventType);
            if (runOnMainSync) {
                mInstrumentation.runOnMainSync(runnable);
            } else {
                runnable.run();
            }
            mInstrumentation.waitForIdleSync();
            eventCalled = latch.await(eventExpected ? EXPECT_TIMEOUT_MS : NOT_EXCEPT_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("Interrupted while waiting for latch: " + e.getMessage());
            return;
        } finally {
            mInputMethodService.setCountDownLatchForTesting(null /* latch */, eventType);
        }

        if (eventExpected && !eventCalled) {
            fail("Timed out waiting for " + eventToString(eventType));
        } else if (!eventExpected && eventCalled) {
            fail("Unexpected call " + eventToString(eventType));
        }
        // Input connection is not finished.
        assertWithMessage("Input connection is still started")
                .that(mInputMethodService.getCurrentInputStarted()).isTrue();
        assertWithMessage("Input view started matches expected")
                .that(mInputMethodService.getCurrentInputViewStarted()).isEqualTo(inputViewStarted);

        if (mFlagsValueProvider.getBoolean(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)) {
            // The IME visibility is only sent at the end of the hide animation. Therefore, we have
            // to wait until the visibility was sent to the server and the IME window hidden.
            eventually(() -> assertWithMessage(message).that(mInputMethodService.isInputViewShown())
                    .isEqualTo(shown));
        } else {
            assertWithMessage(message).that(mInputMethodService.isInputViewShown())
                    .isEqualTo(shown);
        }
    }

    private void setOrientation(int orientation) {
        // Simple wrapper for catching RemoteException.
        try {
            switch (orientation) {
                case 1:
                    mUiDevice.setOrientationLeft();
                    break;
                case 2:
                    mUiDevice.setOrientationRight();
                    break;
                default:
                    mUiDevice.setOrientationNatural();
            }
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Verifies the IME fullscreen mode state after executing the given runnable.
     *
     * @param runnable            the runnable to set the orientation.
     * @param eventExpected       whether the event is expected to be called.
     * @param orientationPortrait whether the orientation is expected to be portrait.
     */
    private void verifyFullscreenMode(@NonNull Runnable runnable, boolean eventExpected,
            boolean orientationPortrait) {
        verifyInputViewStatus(runnable, EVENT_CONFIG, eventExpected, false /* shown */,
                "IME is not shown");
        if (eventExpected) {
            eventually(() -> assertWithMessage("Activity was re-created after rotation")
                    .that(TestActivity.getInstance()).isNotEqualTo(mActivity));
            mActivity = TestActivity.getInstance();
            assertWithMessage("Re-created activity is not null").that(mActivity).isNotNull();
            // Wait for the new EditText to be served by InputMethodManager.
            eventually(() -> assertWithMessage("Has an input connection to the re-created Activity")
                    .that(mImm.hasActiveInputConnection(mActivity.getEditText())).isTrue());
        }

        verifyInputViewStatusOnMainSync(() -> mActivity.showImeWithWindowInsetsController(),
                EVENT_SHOW, true /* eventExpected */, true /* shown */, "IME is shown");

        assertWithMessage("IME orientation matches expected")
                .that(mInputMethodService.getResources().getConfiguration().orientation)
                .isEqualTo(orientationPortrait
                        ? Configuration.ORIENTATION_PORTRAIT : Configuration.ORIENTATION_LANDSCAPE);
        final var editorInfo = mInputMethodService.getCurrentInputEditorInfo();
        assertWithMessage("IME_FLAG_NO_FULLSCREEN not set")
                .that(editorInfo.imeOptions & EditorInfo.IME_FLAG_NO_FULLSCREEN).isEqualTo(0);
        assertWithMessage("IME_INTERNAL_FLAG_APP_WINDOW_PORTRAIT matches expected")
                .that(editorInfo.internalImeOptions
                        & EditorInfo.IME_INTERNAL_FLAG_APP_WINDOW_PORTRAIT)
                .isEqualTo(
                        orientationPortrait ? EditorInfo.IME_INTERNAL_FLAG_APP_WINDOW_PORTRAIT : 0);
        assertWithMessage("onEvaluateFullscreenMode matches orientation")
                .that(mInputMethodService.onEvaluateFullscreenMode())
                .isEqualTo(!orientationPortrait);
        assertWithMessage("isFullscreenMode matches orientation")
                .that(mInputMethodService.isFullscreenMode()).isEqualTo(!orientationPortrait);

        // Hide IME before finishing the run.
        verifyInputViewStatusOnMainSync(() -> mActivity.hideImeWithWindowInsetsController(),
                EVENT_HIDE, true /* eventExpected */, false /* shown */, "IME is not shown");
    }

    private void prepareIme() {
        executeShellCommand("ime enable " + mInputMethodId);
        executeShellCommand("ime set " + mInputMethodId);
        mInstrumentation.waitForIdleSync();
        Log.i(TAG, "Finish preparing IME");
    }

    private void prepareActivity() {
        mActivity = TestActivity.startSync(mInstrumentation);
        mInstrumentation.waitForIdleSync();
        Log.i(TAG, "Finish preparing activity with editor.");
    }

    private void waitUntilActivityReadyForInputInjection(@NonNull TestActivity activity) {
        try {
            mWmState.waitUntilActivityReadyForInputInjection(activity, mInstrumentation, TAG,
                    "test: " + mName.getMethodName());
        } catch (InterruptedException e) {
            fail("Interrupted while waiting for activity to be ready: " + e.getMessage());
        }
    }

    @NonNull
    private String getInputMethodId() {
        return mTargetPackageName + "/" + INPUT_METHOD_SERVICE_NAME;
    }

    /**
     * Sets the value of {@link Settings.Secure#SHOW_IME_WITH_HARD_KEYBOARD}, only if it is
     * different to the current value, and waits for the update to take effect.
     *
     * @param enable the value to be set.
     */
    private void setShowImeWithHardKeyboard(boolean enable) {
        if (mInputMethodService == null) {
            // If the IME is no longer around, reset the setting unconditionally.
            executeShellCommand(SET_SHOW_IME_WITH_HARD_KEYBOARD_CMD + " " + (enable ? "1" : "0"));
            return;
        }

        final boolean currentEnabled =
                mInputMethodService.getShouldShowImeWithHardKeyboardForTesting();
        if (currentEnabled != enable) {
            executeShellCommand(SET_SHOW_IME_WITH_HARD_KEYBOARD_CMD + " " + (enable ? "1" : "0"));
            eventually(() -> assertWithMessage("showImeWithHardKeyboard updated")
                    .that(mInputMethodService.getShouldShowImeWithHardKeyboardForTesting())
                    .isEqualTo(enable));
        }
    }

    /**
     * Gets the verbose logging state in {@link android.view.inputmethod.ImeTracker}.
     *
     * @return {@code true} iff verbose logging is enabled.
     */
    private static boolean getVerboseImeTrackerLogging() {
        return executeShellCommand(GET_VERBOSE_IME_TRACKER_LOGGING_CMD).trim().equals("1");
    }

    /**
     * Sets verbose logging in {@link android.view.inputmethod.ImeTracker}.
     *
     * @param enabled whether to enable or disable verbose logging.
     *
     * @implNote This must use {@link ActivityManager#notifySystemPropertiesChanged()} to listen
     *           for changes to the system property for the verbose ImeTracker logging.
     */
    private void setVerboseImeTrackerLogging(boolean enabled) {
        final var context = mInstrumentation.getContext();
        final var am = context.getSystemService(ActivityManager.class);

        executeShellCommand(SET_VERBOSE_IME_TRACKER_LOGGING_CMD + " " + (enabled ? "1" : "0"));
        am.notifySystemPropertiesChanged();
    }

    @NonNull
    private static String executeShellCommand(@NonNull String cmd) {
        Log.i(TAG, "Run command: " + cmd);
        return SystemUtil.runShellCommandOrThrow(cmd);
    }

    /**
     * Checks if the Input Method Switcher Menu is shown. This runs by adopting the Shell's
     * permission to ensure we have TEST_INPUT_METHOD permission.
     */
    private static boolean isInputMethodPickerShown(@NonNull InputMethodManager imm) {
        return SystemUtil.runWithShellPermissionIdentity(imm::isInputMethodPickerShown);
    }

    @NonNull
    private UiObject2 getUiObject(@NonNull BySelector bySelector) {
        final var preScreenshot = mInstrumentation.getUiAutomation().takeScreenshot();
        mDumpOnFailure.dumpOnFailure("pre-getUiObject", preScreenshot);
        final var uiObject = mUiDevice.wait(Until.findObject(bySelector), TIMEOUT_MS);
        mInstrumentation.waitForIdleSync();
        final var postScreenshot = mInstrumentation.getUiAutomation().takeScreenshot();
        mDumpOnFailure.dumpOnFailure("post-getUiObject", postScreenshot);
        try {
            final var outputStream = new ByteArrayOutputStream();
            mUiDevice.dumpWindowHierarchy(outputStream);
            final String windowHierarchy = outputStream.toString(StandardCharsets.UTF_8);
            mDumpOnFailure.dumpOnFailure("post-getUiObject", windowHierarchy);
        } catch (Exception e) {
            Log.i(TAG, "Failed to dump windowHierarchy", e);
        }
        assertWithMessage("UiObject with " + bySelector + " was found").that(uiObject).isNotNull();
        return uiObject;
    }

    /** Checks whether the device has a navigation bar on the IME's display. */
    private boolean hasNavigationBar() {
        try {
            return WindowManagerGlobal.getWindowManagerService()
                    .hasNavigationBar(mInputMethodService.getDisplayId())
                    && mGestureNavSwitchHelper.hasNavigationBar();
        } catch (RemoteException e) {
            fail("Failed to check whether the device has a navigation bar: " + e.getMessage());
            return false;
        }
    }

    /**
     * Manually sets whether the IME draws the IME navigation bar and IME Switcher button,
     * regardless of the current navigation bar mode.
     *
     * <p>Note, neither of these are normally drawn when in three button navigation mode.
     *
     * @param enable whether the IME nav bar and IME Switcher button are drawn.
     */
    private void setDrawsImeNavBarAndSwitcherButton(boolean enable) {
        final int flags = enable ? IME_DRAWS_IME_NAV_BAR | SHOW_IME_SWITCHER_WHEN_IME_IS_SHOWN : 0;
        mInputMethodService.getInputMethodInternal().onNavButtonFlagsChanged(flags);
    }

    /**
     * Set whether the IME navigation bar should be shown or not.
     *
     * <p>Note, this has no effect when the IME does not draw the IME navigation bar.
     *
     * @param show whether the IME navigation bar should be shown.
     */
    private void setShowImeNavigationBar(boolean show) {
        final var controller = mInputMethodService.getWindow().getWindow().getInsetsController();
        if (show) {
            controller.show(captionBar());
        } else {
            controller.hide(captionBar());
        }
    }
}
