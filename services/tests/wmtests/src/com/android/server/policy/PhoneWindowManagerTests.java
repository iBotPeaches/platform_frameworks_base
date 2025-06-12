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

package com.android.server.policy;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_WALLPAPER;
import static android.view.WindowManagerGlobal.ADD_OKAY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.reset;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;
import static com.android.server.policy.PhoneWindowManager.EXTRA_TRIGGER_HUB;
import static com.android.server.policy.PhoneWindowManager.SHORT_PRESS_POWER_DREAM_OR_AWAKE_OR_SLEEP;
import static com.android.server.policy.PhoneWindowManager.SHORT_PRESS_POWER_HUB_OR_DREAM_OR_SLEEP;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.service.dreams.DreamManagerInternal;
import android.testing.TestableContext;
import android.view.KeyEvent;

import androidx.test.filters.SmallTest;

import com.android.internal.util.test.LocalServiceKeeperRule;
import com.android.internal.widget.LockPatternUtils;
import com.android.server.input.InputManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.policy.keyguard.KeyguardServiceDelegate;
import com.android.server.statusbar.StatusBarManagerInternal;
import com.android.server.wm.ActivityTaskManagerInternal;
import com.android.server.wm.DisplayPolicy;
import com.android.server.wm.DisplayRotation;
import com.android.server.wm.WindowManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Test class for {@link PhoneWindowManager}.
 *
 * Build/Install/Run:
 * atest WmTests:PhoneWindowManagerTests
 */
@Presubmit
@SmallTest
public class PhoneWindowManagerTests {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule
    public LocalServiceKeeperRule mLocalServiceKeeperRule = new LocalServiceKeeperRule();

    @Rule
    public final TestableContext mContext = spy(
            new TestableContext(getInstrumentation().getContext()));

    @Mock private IBinder mInputToken;

    PhoneWindowManager mPhoneWindowManager;
    @Mock
    private ActivityTaskManagerInternal mAtmInternal;
    @Mock
    private DreamManagerInternal mDreamManagerInternal;
    @Mock
    private InputManagerInternal mInputManagerInternal;
    @Mock
    private PowerManagerInternal mPowerManagerInternal;
    @Mock
    private StatusBarManagerInternal mStatusBarManagerInternal;
    @Mock
    private UserManagerInternal mUserManagerInternal;

    @Mock
    private PowerManager mPowerManager;
    @Mock
    private DisplayPolicy mDisplayPolicy;
    @Mock
    private KeyguardServiceDelegate mKeyguardServiceDelegate;
    @Mock
    private LockPatternUtils mLockPatternUtils;

    private static final int INTERCEPT_SYSTEM_KEY_NOT_CONSUMED_DELAY = 0;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(Context.POWER_SERVICE)).thenReturn(mPowerManager);

        mPhoneWindowManager = spy(new PhoneWindowManager());
        spyOn(ActivityManager.getService());

        mLocalServiceKeeperRule.overrideLocalService(ActivityTaskManagerInternal.class,
                mAtmInternal);
        mPhoneWindowManager.mActivityTaskManagerInternal = mAtmInternal;
        mLocalServiceKeeperRule.overrideLocalService(DreamManagerInternal.class,
                mDreamManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(InputManagerInternal.class,
                mInputManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(PowerManagerInternal.class,
                mPowerManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(StatusBarManagerInternal.class,
                mStatusBarManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(UserManagerInternal.class,
                mUserManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(WindowManagerInternal.class,
                mock(WindowManagerInternal.class));

        mPhoneWindowManager.mKeyguardDelegate = mKeyguardServiceDelegate;
        final InputManager im = mock(InputManager.class);
        doNothing().when(im).registerKeyGestureEventHandler(anyList(), any());
        doReturn(im).when(mContext).getSystemService(eq(Context.INPUT_SERVICE));
    }

    @After
    public void tearDown() {
        reset(ActivityManager.getService());
        reset(mContext);
    }

    @Test
    public void testShouldNotStartDockOrHomeWhenSetup() throws Exception {
        mockStartDockOrHome();
        doReturn(false).when(mPhoneWindowManager).isUserSetupComplete();

        mPhoneWindowManager.startDockOrHome(
                0 /* displayId */, false /* fromHomeKey */, false /* awakenFromDreams */);

        verify(mPhoneWindowManager, never()).createHomeDockIntent();
    }

    @Test
    public void testShouldStartDockOrHomeAfterSetup() throws Exception {
        mockStartDockOrHome();
        doReturn(true).when(mPhoneWindowManager).isUserSetupComplete();

        mPhoneWindowManager.startDockOrHome(
                0 /* displayId */, false /* fromHomeKey */, false /* awakenFromDreams */);

        verify(mPhoneWindowManager).createHomeDockIntent();
    }

    @Test
    public void testScreenTurnedOff() {
        doNothing().when(mPhoneWindowManager).updateSettings(any());
        doNothing().when(mPhoneWindowManager).initializeHdmiState();
        final boolean[] isScreenTurnedOff = {false};
        doAnswer(invocation -> isScreenTurnedOff[0] = true).when(mDisplayPolicy).screenTurnedOff(
                anyBoolean());
        doAnswer(invocation -> !isScreenTurnedOff[0]).when(mDisplayPolicy).isScreenOnEarly();
        doAnswer(invocation -> !isScreenTurnedOff[0]).when(mDisplayPolicy).isScreenOnFully();

        when(mPowerManager.isInteractive()).thenReturn(true);
        initPhoneWindowManager();
        assertThat(isScreenTurnedOff[0]).isFalse();
        assertThat(mPhoneWindowManager.mIsGoingToSleepDefaultDisplay).isFalse();

        // Skip sleep-token for non-sleep-screen-off.
        mPhoneWindowManager.screenTurnedOff(DEFAULT_DISPLAY, true /* isSwappingDisplay */);
        verify(mDisplayPolicy).screenTurnedOff(false /* acquireSleepToken */);
        assertThat(isScreenTurnedOff[0]).isTrue();

        // Apply sleep-token for sleep-screen-off.
        isScreenTurnedOff[0] = false;
        mPhoneWindowManager.startedGoingToSleep(DEFAULT_DISPLAY, 0 /* reason */);
        assertThat(mPhoneWindowManager.mIsGoingToSleepDefaultDisplay).isTrue();
        mPhoneWindowManager.screenTurnedOff(DEFAULT_DISPLAY, true /* isSwappingDisplay */);
        verify(mDisplayPolicy).screenTurnedOff(true /* acquireSleepToken */);

        mPhoneWindowManager.finishedGoingToSleep(DEFAULT_DISPLAY, 0 /* reason */);
        assertThat(mPhoneWindowManager.mIsGoingToSleepDefaultDisplay).isFalse();
    }

    @Test
    public void testCheckAddPermission_withoutAccessibilityOverlay_noAccessibilityAppOpLogged() {
        mSetFlagsRule.enableFlags(android.view.contentprotection.flags.Flags.FLAG_CREATE_ACCESSIBILITY_OVERLAY_APP_OP_ENABLED);
        int[] outAppOp = new int[1];
        assertEquals(ADD_OKAY, mPhoneWindowManager.checkAddPermission(TYPE_WALLPAPER,
                /* isRoundedCornerOverlay= */ false, "test.pkg", outAppOp, DEFAULT_DISPLAY));
        assertThat(outAppOp[0]).isEqualTo(AppOpsManager.OP_NONE);
    }

    @Test
    public void testCheckAddPermission_withAccessibilityOverlay() {
        mSetFlagsRule.enableFlags(android.view.contentprotection.flags.Flags.FLAG_CREATE_ACCESSIBILITY_OVERLAY_APP_OP_ENABLED);
        int[] outAppOp = new int[1];
        assertEquals(ADD_OKAY, mPhoneWindowManager.checkAddPermission(TYPE_ACCESSIBILITY_OVERLAY,
                /* isRoundedCornerOverlay= */ false, "test.pkg", outAppOp, DEFAULT_DISPLAY));
        assertThat(outAppOp[0]).isEqualTo(AppOpsManager.OP_CREATE_ACCESSIBILITY_OVERLAY);
    }

    @Test
    public void testCheckAddPermission_withAccessibilityOverlay_flagDisabled() {
        mSetFlagsRule.disableFlags(android.view.contentprotection.flags.Flags.FLAG_CREATE_ACCESSIBILITY_OVERLAY_APP_OP_ENABLED);
        int[] outAppOp = new int[1];
        assertEquals(ADD_OKAY, mPhoneWindowManager.checkAddPermission(TYPE_ACCESSIBILITY_OVERLAY,
                /* isRoundedCornerOverlay= */ false, "test.pkg", outAppOp, DEFAULT_DISPLAY));
        assertThat(outAppOp[0]).isEqualTo(AppOpsManager.OP_NONE);
    }

    @Test
    public void userSwitching_keyboardShortcutHelperDismissed() {
        mPhoneWindowManager.setSwitchingUser(true);

        verify(mStatusBarManagerInternal).dismissKeyboardShortcutsMenu();
    }

    @Test
    public void userNotSwitching_keyboardShortcutHelperDismissed() {
        mPhoneWindowManager.setSwitchingUser(false);

        verify(mStatusBarManagerInternal, never()).dismissKeyboardShortcutsMenu();
    }

    @Test
    public void powerPress_hubOrDreamOrSleep_goesToSleepFromDream() {
        when(mDisplayPolicy.isAwake()).thenReturn(true);
        when(mLockPatternUtils.isLockScreenDisabled(anyInt())).thenReturn(false);
        initPhoneWindowManager();

        // Set power button behavior.
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.POWER_BUTTON_SHORT_PRESS, SHORT_PRESS_POWER_HUB_OR_DREAM_OR_SLEEP);
        mPhoneWindowManager.updateSettings(null);

        // Device is dreaming.
        when(mDreamManagerInternal.isDreaming()).thenReturn(true);

        // Power button pressed.
        int eventTime = 0;
        mPhoneWindowManager.powerPress(eventTime, 1, 0);

        // Device goes to sleep.
        verify(mPowerManager).goToSleep(eventTime, PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON, 0);
    }

    @Test
    public void powerPress_hubOrDreamOrSleep_hubAvailableLocks() {
        when(mDisplayPolicy.isAwake()).thenReturn(true);
        when(mLockPatternUtils.isLockScreenDisabled(anyInt())).thenReturn(false);
        mContext.getTestablePermissions().setPermission(android.Manifest.permission.DEVICE_POWER,
                PERMISSION_GRANTED);
        initPhoneWindowManager();

        // Set power button behavior.
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.POWER_BUTTON_SHORT_PRESS, SHORT_PRESS_POWER_HUB_OR_DREAM_OR_SLEEP);
        mPhoneWindowManager.updateSettings(null);

        // Set up hub prerequisites.
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.GLANCEABLE_HUB_ENABLED, 1);
        when(mUserManagerInternal.isUserUnlocked(any(Integer.class))).thenReturn(true);
        when(mDreamManagerInternal.dreamConditionActive()).thenReturn(true);

        // Power button pressed.
        int eventTime = 0;
        mPhoneWindowManager.powerPress(eventTime, 1, 0);

        // Lock requested with the proper bundle options.
        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(mPhoneWindowManager).lockNow(bundleCaptor.capture());
        assertThat(bundleCaptor.getValue().getBoolean(EXTRA_TRIGGER_HUB)).isTrue();
    }

    @Test
    public void powerPress_hubOrDreamOrSleep_hubNotAvailableDreams() {
        when(mDisplayPolicy.isAwake()).thenReturn(true);
        when(mLockPatternUtils.isLockScreenDisabled(anyInt())).thenReturn(false);
        initPhoneWindowManager();

        // Set power button behavior.
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.POWER_BUTTON_SHORT_PRESS, SHORT_PRESS_POWER_HUB_OR_DREAM_OR_SLEEP);
        mPhoneWindowManager.updateSettings(null);

        // Hub is not available.
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.GLANCEABLE_HUB_ENABLED, 0);
        when(mDreamManagerInternal.canStartDreaming(any(Boolean.class))).thenReturn(true);

        // Power button pressed.
        int eventTime = 0;
        mPhoneWindowManager.powerPress(eventTime, 1, 0);

        // Dream is requested.
        verify(mDreamManagerInternal).requestDream();
    }

    @Test
    public void powerPress_dreamOrAwakeOrSleep_awakeFromDream() {
        when(mDisplayPolicy.isAwake()).thenReturn(true);
        initPhoneWindowManager();

        // Set power button behavior.
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.POWER_BUTTON_SHORT_PRESS,
                SHORT_PRESS_POWER_DREAM_OR_AWAKE_OR_SLEEP);
        mPhoneWindowManager.updateSettings(null);

        // Can not dream when device is dreaming.
        when(mDreamManagerInternal.canStartDreaming(any(Boolean.class))).thenReturn(false);
        // Device is dreaming.
        when(mDreamManagerInternal.isDreaming()).thenReturn(true);

        // Power button pressed.
        int eventTime = 0;
        mPhoneWindowManager.powerPress(eventTime, 1, 0);

        // Dream is stopped.
        verify(mDreamManagerInternal)
                .stopDream(false /*immediate*/, "short press power" /*reason*/);
    }

    @Test
    public void powerPress_dreamOrAwakeOrSleep_canNotDreamGoToSleep() {
        when(mDisplayPolicy.isAwake()).thenReturn(true);
        initPhoneWindowManager();

        // Set power button behavior.
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.POWER_BUTTON_SHORT_PRESS,
                SHORT_PRESS_POWER_DREAM_OR_AWAKE_OR_SLEEP);
        mPhoneWindowManager.updateSettings(null);

        // Can not dream for other reasons.
        when(mDreamManagerInternal.canStartDreaming(any(Boolean.class))).thenReturn(false);
        // Device is not dreaming.
        when(mDreamManagerInternal.isDreaming()).thenReturn(false);

        // Power button pressed.
        int eventTime = 0;
        mPhoneWindowManager.powerPress(eventTime, 1, 0);

        // Device goes to sleep.
        verify(mPowerManager).goToSleep(eventTime, PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON, 0);
    }

    @Test
    public void powerPress_dreamOrAwakeOrSleep_dreamFromActive() {
        when(mDisplayPolicy.isAwake()).thenReturn(true);
        initPhoneWindowManager();

        // Set power button behavior.
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.POWER_BUTTON_SHORT_PRESS,
                SHORT_PRESS_POWER_DREAM_OR_AWAKE_OR_SLEEP);
        mPhoneWindowManager.updateSettings(null);

        // Can dream when active.
        when(mDreamManagerInternal.canStartDreaming(any(Boolean.class))).thenReturn(true);

        // Power button pressed.
        int eventTime = 0;
        mPhoneWindowManager.powerPress(eventTime, 1, 0);

        // Dream is requested.
        verify(mDreamManagerInternal).requestDream();
    }

    @EnableFlags(com.android.hardware.input.Flags.FLAG_FIX_SEARCH_MODIFIER_FALLBACKS)
    public void testInterceptKeyBeforeDispatching() {
        // Handle sub-tasks of init().
        doNothing().when(mPhoneWindowManager).updateSettings(any());
        doNothing().when(mPhoneWindowManager).initializeHdmiState();
        final DisplayPolicy displayPolicy = mock(DisplayPolicy.class);
        mPhoneWindowManager.mDefaultDisplayPolicy = displayPolicy;
        mPhoneWindowManager.mDefaultDisplayRotation = mock(DisplayRotation.class);
        final PowerManager pm = mock(PowerManager.class);
        doReturn(true).when(pm).isInteractive();
        doReturn(pm).when(mContext).getSystemService(eq(Context.POWER_SERVICE));

        mContext.getMainThreadHandler().runWithScissors(() -> mPhoneWindowManager.init(
                new PhoneWindowManager.Injector(mContext,
                        mock(WindowManagerPolicy.WindowManagerFuncs.class))), 0);

        // Case: KeyNotConsumed with meta key.
        KeyEvent keyEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_A, 0, KeyEvent.META_META_ON);
        long result = mPhoneWindowManager.interceptKeyBeforeDispatching(mInputToken, keyEvent, 0);
        assertEquals(INTERCEPT_SYSTEM_KEY_NOT_CONSUMED_DELAY, result);

        // Case: KeyNotConsumed without meta key.
        KeyEvent keyEvent1 = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_ESCAPE, 0, 0);
        long result1 = mPhoneWindowManager.interceptKeyBeforeDispatching(mInputToken, keyEvent1, 0);
        assertEquals(INTERCEPT_SYSTEM_KEY_NOT_CONSUMED_DELAY, result1);
    }

    private void initPhoneWindowManager() {
        mPhoneWindowManager.mDefaultDisplayPolicy = mDisplayPolicy;
        mPhoneWindowManager.mDefaultDisplayRotation = mock(DisplayRotation.class);
        mContext.getMainThreadHandler().runWithScissors(() -> mPhoneWindowManager.init(
                new TestInjector(mContext, mock(WindowManagerPolicy.WindowManagerFuncs.class))), 0);
    }

    private void mockStartDockOrHome() throws Exception {
        doNothing().when(ActivityManager.getService()).stopAppSwitches();
        when(mAtmInternal.startHomeOnDisplay(
                anyInt(), anyString(), anyInt(), anyBoolean(), anyBoolean())).thenReturn(false);
        mPhoneWindowManager.mUserManagerInternal = mock(UserManagerInternal.class);
    }

    private class TestInjector extends PhoneWindowManager.Injector {
        TestInjector(Context context, WindowManagerPolicy.WindowManagerFuncs funcs) {
            super(context, funcs);
        }

        KeyguardServiceDelegate getKeyguardServiceDelegate() {
            return mKeyguardServiceDelegate;
        }

        @Override
        LockPatternUtils getLockPatternUtils() {
            return mLockPatternUtils;
        }

        /**
         * {@code WindowWakeUpPolicy} registers a local service in its constructor, easier to just
         * mock it out so we don't have to unregister it after every test.
         */
        WindowWakeUpPolicy getWindowWakeUpPolicy() {
            return mock(WindowWakeUpPolicy.class);
        }
    }
}
