/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.binder

import android.app.IActivityTaskManager
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.IRemoteAnimationFinishedCallback
import android.view.RemoteAnimationTarget
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.widget.LockPatternUtils
import com.android.systemui.SysuiTestCase
import com.android.systemui.keyguard.WindowManagerLockscreenVisibilityManager
import com.android.systemui.keyguard.domain.interactor.KeyguardDismissTransitionInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardShowWhileAwakeInteractor
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.time.FakeSystemClock
import com.android.window.flags.Flags
import com.android.wm.shell.keyguard.KeyguardTransitions
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class WindowManagerLockscreenVisibilityManagerTest : SysuiTestCase() {

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private lateinit var underTest: WindowManagerLockscreenVisibilityManager
    private lateinit var executor: FakeExecutor
    private lateinit var uiBgExecutor: FakeExecutor

    @Mock private lateinit var activityTaskManagerService: IActivityTaskManager
    @Mock private lateinit var keyguardStateController: KeyguardStateController
    @Mock private lateinit var keyguardSurfaceBehindAnimator: KeyguardSurfaceBehindParamsApplier
    @Mock
    private lateinit var keyguardDismissTransitionInteractor: KeyguardDismissTransitionInteractor
    @Mock private lateinit var keyguardTransitions: KeyguardTransitions
    @Mock private lateinit var lockPatternUtils: LockPatternUtils
    @Mock private lateinit var keyguardShowWhileAwakeInteractor: KeyguardShowWhileAwakeInteractor
    @Mock private lateinit var selectedUserInteractor: SelectedUserInteractor

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        executor = FakeExecutor(FakeSystemClock())
        uiBgExecutor = FakeExecutor(FakeSystemClock())

        underTest =
            WindowManagerLockscreenVisibilityManager(
                executor = executor,
                uiBgExecutor = uiBgExecutor,
                activityTaskManagerService = activityTaskManagerService,
                keyguardStateController = keyguardStateController,
                keyguardSurfaceBehindAnimator = keyguardSurfaceBehindAnimator,
                keyguardDismissTransitionInteractor = keyguardDismissTransitionInteractor,
                keyguardTransitions = keyguardTransitions,
                selectedUserInteractor = selectedUserInteractor,
                lockPatternUtils = lockPatternUtils,
                keyguardShowWhileAwakeInteractor = keyguardShowWhileAwakeInteractor,
            )
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING)
    fun testLockscreenVisible_andAodVisible_without_keyguard_shell_transitions() {
        underTest.setLockscreenShown(true)
        uiBgExecutor.runAllReady()
        verify(activityTaskManagerService).setLockScreenShown(true, false)
        underTest.setAodVisible(true)
        uiBgExecutor.runAllReady()
        verify(activityTaskManagerService).setLockScreenShown(true, true)

        verifyNoMoreInteractions(activityTaskManagerService)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING)
    fun testLockscreenVisible_andAodVisible_with_keyguard_shell_transitions() {
        underTest.setLockscreenShown(true)
        uiBgExecutor.runAllReady()
        verify(keyguardTransitions).startKeyguardTransition(true, false)
        underTest.setAodVisible(true)
        uiBgExecutor.runAllReady()
        verify(keyguardTransitions).startKeyguardTransition(true, true)

        verifyNoMoreInteractions(keyguardTransitions)
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING)
    fun testGoingAway_whenLockscreenVisible_thenSurfaceMadeVisible_without_keyguard_shell_transitions() {
        underTest.setLockscreenShown(true)
        uiBgExecutor.runAllReady()
        verify(activityTaskManagerService).setLockScreenShown(true, false)
        underTest.setAodVisible(true)
        uiBgExecutor.runAllReady()
        verify(activityTaskManagerService).setLockScreenShown(true, true)

        verifyNoMoreInteractions(activityTaskManagerService)

        underTest.setSurfaceBehindVisibility(true)
        uiBgExecutor.runAllReady()
        verify(activityTaskManagerService).keyguardGoingAway(anyInt())

        verifyNoMoreInteractions(activityTaskManagerService)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING)
    fun testGoingAway_whenLockscreenVisible_thenSurfaceMadeVisible_with_keyguard_shell_transitions() {
        underTest.setLockscreenShown(true)
        uiBgExecutor.runAllReady()
        verify(keyguardTransitions).startKeyguardTransition(true, false)
        underTest.setAodVisible(true)
        uiBgExecutor.runAllReady()
        verify(keyguardTransitions).startKeyguardTransition(true, true)

        verifyNoMoreInteractions(keyguardTransitions)

        underTest.setSurfaceBehindVisibility(true)
        uiBgExecutor.runAllReady()
        verify(keyguardTransitions).startKeyguardTransition(false, false)

        verifyNoMoreInteractions(keyguardTransitions)
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING)
    fun testSurfaceVisible_whenLockscreenNotShowing_doesNotTriggerGoingAway_without_keyguard_shell_transitions() {
        underTest.setLockscreenShown(false)
        underTest.setAodVisible(false)
        uiBgExecutor.runAllReady()

        verify(activityTaskManagerService).setLockScreenShown(false, false)
        verifyNoMoreInteractions(activityTaskManagerService)

        underTest.setSurfaceBehindVisibility(true)
        uiBgExecutor.runAllReady()

        verifyNoMoreInteractions(activityTaskManagerService)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING)
    fun testSurfaceVisible_whenLockscreenNotShowing_doesNotTriggerGoingAway_with_keyguard_shell_transitions() {
        underTest.setLockscreenShown(false)
        underTest.setAodVisible(false)
        uiBgExecutor.runAllReady()

        verify(keyguardTransitions).startKeyguardTransition(false, false)
        verifyNoMoreInteractions(keyguardTransitions)

        underTest.setSurfaceBehindVisibility(true)
        uiBgExecutor.runAllReady()

        verifyNoMoreInteractions(keyguardTransitions)
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING)
    fun testAodVisible_noLockscreenShownCallYet_doesNotShowLockscreenUntilLater_without_keyguard_shell_transitions() {
        underTest.setAodVisible(false)
        uiBgExecutor.runAllReady()
        verifyNoMoreInteractions(activityTaskManagerService)

        underTest.setLockscreenShown(true)
        uiBgExecutor.runAllReady()
        verify(activityTaskManagerService).setLockScreenShown(true, false)
        verifyNoMoreInteractions(activityTaskManagerService)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING)
    fun testAodVisible_noLockscreenShownCallYet_doesNotShowLockscreenUntilLater_with_keyguard_shell_transitions() {
        underTest.setAodVisible(false)
        uiBgExecutor.runAllReady()
        verifyNoMoreInteractions(keyguardTransitions)

        underTest.setLockscreenShown(true)
        uiBgExecutor.runAllReady()
        verify(keyguardTransitions).startKeyguardTransition(true, false)
        verifyNoMoreInteractions(activityTaskManagerService)
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING)
    fun setSurfaceBehindVisibility_goesAwayFirst_andIgnoresSecondCall_without_keyguard_shell_transitions() {
        underTest.setLockscreenShown(true)
        uiBgExecutor.runAllReady()
        underTest.setSurfaceBehindVisibility(true)
        uiBgExecutor.runAllReady()
        verify(activityTaskManagerService).keyguardGoingAway(0)

        underTest.setSurfaceBehindVisibility(true)
        uiBgExecutor.runAllReady()
        verifyNoMoreInteractions(keyguardTransitions)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING)
    fun setSurfaceBehindVisibility_goesAwayFirst_andIgnoresSecondCall_with_keyguard_shell_transitions() {
        underTest.setLockscreenShown(true)
        uiBgExecutor.runAllReady()
        verify(keyguardTransitions).startKeyguardTransition(true, false)
        underTest.setSurfaceBehindVisibility(true)
        uiBgExecutor.runAllReady()
        verify(keyguardTransitions).startKeyguardTransition(false, false)

        underTest.setSurfaceBehindVisibility(true)
        uiBgExecutor.runAllReady()
        verifyNoMoreInteractions(keyguardTransitions)
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING)
    fun setSurfaceBehindVisibility_falseSetsLockscreenVisibility_without_keyguard_shell_transitions() {
        underTest.setLockscreenShown(true)
        uiBgExecutor.runAllReady()
        verify(activityTaskManagerService).setLockScreenShown(eq(true), any())

        // Show the surface behind, then hide it.
        underTest.setSurfaceBehindVisibility(true)
        uiBgExecutor.runAllReady()
        underTest.setSurfaceBehindVisibility(false)
        uiBgExecutor.runAllReady()

        verify(activityTaskManagerService, times(2)).setLockScreenShown(eq(true), any())
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING)
    fun setSurfaceBehindVisibility_falseSetsLockscreenVisibility_with_keyguard_shell_transitions() {
        // Show the surface behind, then hide it.
        underTest.setLockscreenShown(true)
        underTest.setSurfaceBehindVisibility(true)
        underTest.setSurfaceBehindVisibility(false)
        uiBgExecutor.runAllReady()
        verify(keyguardTransitions).startKeyguardTransition(eq(true), any())
    }

    @Test
    fun remoteAnimationInstantlyFinished_ifDismissTransitionNotStarted() {
        val mockedCallback = mock<IRemoteAnimationFinishedCallback>()

        // Call the onAlreadyGone callback immediately.
        doAnswer { invocation -> (invocation.getArgument(1) as (() -> Unit)).invoke() }
            .whenever(keyguardDismissTransitionInteractor)
            .startDismissKeyguardTransition(any(), any())

        whenever(selectedUserInteractor.getSelectedUserId()).thenReturn(-1)

        underTest.onKeyguardGoingAwayRemoteAnimationStart(
            transit = 0,
            apps = arrayOf(mock<RemoteAnimationTarget>()),
            wallpapers = emptyArray(),
            nonApps = emptyArray(),
            finishedCallback = mockedCallback,
        )

        verify(mockedCallback).onAnimationFinished()
        verifyNoMoreInteractions(mockedCallback)
    }

    @Test
    fun lockscreenEventuallyShown_ifReshown_afterGoingAwayExecutionDelayed() {
        underTest.setLockscreenShown(true)
        uiBgExecutor.runAllReady()
        clearInvocations(activityTaskManagerService)

        // Trigger keyguardGoingAway, then immediately setLockScreenShown before going away runs on
        // the uiBgExecutor.
        underTest.setSurfaceBehindVisibility(true)
        underTest.setLockscreenShown(true)

        // Next ready should be the keyguardGoingAway call.
        uiBgExecutor.runNextReady()
        verify(activityTaskManagerService).keyguardGoingAway(anyInt())
        verify(activityTaskManagerService, never()).setLockScreenShown(any(), any())
        clearInvocations(activityTaskManagerService)

        // Then, the setLockScreenShown call, which should have been enqueued when we called
        // setLockScreenShown(true) even though keyguardGoingAway() hadn't yet been called.
        uiBgExecutor.runNextReady()
        verify(activityTaskManagerService).setLockScreenShown(eq(true), any())
        verify(activityTaskManagerService, never()).keyguardGoingAway(anyInt())
        clearInvocations(activityTaskManagerService)

        // Shouldn't be anything left in the queue.
        uiBgExecutor.runAllReady()
        verifyNoMoreInteractions(activityTaskManagerService)
    }
}
