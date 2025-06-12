/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.systemui.statusbar.notification.collection.coordinator

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.testing.TestableLooper.RunWithLooper
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.flags.BrokenWithSceneContainer
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.WakefulnessLifecycle
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.keyguard.wakefulnessLifecycle
import com.android.systemui.kosmos.testCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.plugins.statusbar.statusBarStateController
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.shade.data.repository.ShadeAnimationRepository
import com.android.systemui.shade.data.repository.fakeShadeRepository
import com.android.systemui.shade.data.repository.shadeRepository
import com.android.systemui.shade.domain.interactor.ShadeAnimationInteractorLegacyImpl
import com.android.systemui.shade.domain.interactor.shadeAnimationInteractor
import com.android.systemui.statusbar.notification.VisibilityLocationProvider
import com.android.systemui.statusbar.notification.collection.GroupEntry
import com.android.systemui.statusbar.notification.collection.GroupEntryBuilder
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder
import com.android.systemui.statusbar.notification.collection.UseElapsedRealtimeForCreationTime
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifStabilityManager
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.Pluggable.PluggableListener
import com.android.systemui.statusbar.notification.collection.notifPipeline
import com.android.systemui.statusbar.notification.data.repository.FakeHeadsUpRowRepository
import com.android.systemui.statusbar.notification.stack.data.repository.FakeHeadsUpNotificationRepository
import com.android.systemui.statusbar.notification.stack.data.repository.headsUpNotificationRepository
import com.android.systemui.statusbar.notification.visibilityLocationProvider
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.statusbar.policy.keyguardStateController
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.withArgCaptor
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.verification.VerificationMode
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@RunWithLooper
class VisualStabilityCoordinatorTest(flags: FlagsParameterization) : SysuiTestCase() {
    private val kosmos = testKosmos().apply { testCase = this@VisualStabilityCoordinatorTest }

    private val invalidateListener: PluggableListener<NotifStabilityManager> = mock()
    private val headsUpRepository = kosmos.headsUpNotificationRepository
    private val visibilityLocationProvider: VisibilityLocationProvider =
        kosmos.visibilityLocationProvider
    private val keyguardStateController: KeyguardStateController = kosmos.keyguardStateController

    private val fakeSystemClock = kosmos.fakeSystemClock
    private val fakeBackgroundExecutor = kosmos.fakeExecutor
    private val testScope = kosmos.testScope

    private val shadeRepository = kosmos.fakeShadeRepository
    private lateinit var wakefulnessObserver: WakefulnessLifecycle.Observer
    private lateinit var statusBarStateListener: StatusBarStateController.StateListener
    private lateinit var notifStabilityManager: NotifStabilityManager
    private lateinit var entry: NotificationEntry
    private lateinit var groupEntry: GroupEntry

    private val underTest: VisualStabilityCoordinator by lazy { kosmos.visualStabilityCoordinator }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(Flags.FLAG_STABILIZE_HEADS_UP_GROUP_V2)
                .andSceneContainer()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp() {
        configureKosmos()

        underTest.attach(kosmos.notifPipeline)
        testScope.testScheduler.runCurrent()

        // capture arguments:
        wakefulnessObserver = withArgCaptor {
            verify(kosmos.wakefulnessLifecycle).addObserver(capture())
        }
        statusBarStateListener = withArgCaptor {
            verify(kosmos.statusBarStateController).addCallback(capture())
        }
        notifStabilityManager = withArgCaptor {
            verify(kosmos.notifPipeline).setVisualStabilityManager(capture())
        }
        notifStabilityManager.setInvalidationListener(invalidateListener)

        entry = NotificationEntryBuilder().setPkg("testPkg1").build()

        groupEntry = GroupEntryBuilder().setSummary(entry).build()

        // Whenever we invalidate, the pipeline runs again, so we invalidate the state
        whenever(invalidateListener.onPluggableInvalidated(eq(notifStabilityManager), any()))
            .doAnswer { _ ->
                notifStabilityManager.onBeginRun()
                null
            }
    }

    private fun configureKosmos() {
        kosmos.statusBarStateController = mock()
        // TODO(377868472) only override this when SceneContainer is disabled
        kosmos.shadeAnimationInteractor =
            ShadeAnimationInteractorLegacyImpl(ShadeAnimationRepository(), shadeRepository)
    }

    @Test
    fun testScreenOff_groupAndSectionChangesAllowed() =
        testScope.runTest {
            // GIVEN screen is off, panel isn't expanded and device isn't pulsing
            setFullyDozed(true)
            setSleepy(true)
            setPanelExpanded(false)
            setPulsing(false)

            // THEN group changes are allowed
            assertThat(notifStabilityManager.isGroupChangeAllowed(entry)).isTrue()
            assertThat(notifStabilityManager.isGroupPruneAllowed(groupEntry)).isTrue()

            // THEN section changes are allowed
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isTrue()
        }

    @Test
    fun testScreenTurningOff_groupAndSectionChangesNotAllowed() =
        testScope.runTest {
            // GIVEN the screen is turning off (sleepy but partially dozed)
            setFullyDozed(false)
            setSleepy(true)
            setPanelExpanded(true)
            setPulsing(false)

            // THEN group changes are NOT allowed
            assertThat(notifStabilityManager.isGroupChangeAllowed(entry)).isFalse()
            assertThat(notifStabilityManager.isGroupPruneAllowed(groupEntry)).isFalse()

            // THEN section changes are NOT allowed
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isFalse()
        }

    @Test
    fun testScreenTurningOn_groupAndSectionChangesNotAllowed() =
        testScope.runTest {
            // GIVEN the screen is turning on (still fully dozed, not sleepy)
            setFullyDozed(true)
            setSleepy(false)
            setPanelExpanded(true)
            setPulsing(false)

            // THEN group changes are NOT allowed
            assertThat(notifStabilityManager.isGroupChangeAllowed(entry)).isFalse()
            assertThat(notifStabilityManager.isGroupPruneAllowed(groupEntry)).isFalse()

            // THEN section changes are NOT allowed
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isFalse()
        }

    @Test
    fun testPanelNotExpanded_groupAndSectionChangesAllowed() =
        testScope.runTest {
            // GIVEN screen is on but the panel isn't expanded and device isn't pulsing
            setFullyDozed(false)
            setSleepy(false)
            setPanelExpanded(false)
            setPulsing(false)

            // THEN group changes are allowed
            assertThat(notifStabilityManager.isGroupChangeAllowed(entry)).isTrue()
            assertThat(notifStabilityManager.isGroupPruneAllowed(groupEntry)).isTrue()

            // THEN section changes are allowed
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isTrue()
        }

    @Test
    fun testPanelExpanded_groupAndSectionChangesNotAllowed() =
        testScope.runTest {
            // GIVEN the panel true expanded and device isn't pulsing
            setFullyDozed(false)
            setSleepy(false)
            setPanelExpanded(true)
            setPulsing(false)

            // THEN group changes are NOT allowed
            assertThat(notifStabilityManager.isGroupChangeAllowed(entry)).isFalse()
            assertThat(notifStabilityManager.isGroupPruneAllowed(groupEntry)).isFalse()

            // THEN section changes are NOT allowed
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isFalse()
        }

    @Test
    @BrokenWithSceneContainer(bugId = 377868472) // mReorderingAllowed is broken with SceneContainer
    fun testLockscreenPartlyShowing_groupAndSectionChangesNotAllowed() =
        testScope.runTest {
            // GIVEN the panel true expanded and device isn't pulsing
            setFullyDozed(false)
            setSleepy(false)
            setLockscreenShowing(0.5f)
            setPulsing(false)

            // THEN group changes are NOT allowed
            assertThat(notifStabilityManager.isGroupChangeAllowed(entry)).isFalse()
            assertThat(notifStabilityManager.isGroupPruneAllowed(groupEntry)).isFalse()

            // THEN section changes are NOT allowed
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isFalse()
        }

    @Test
    @BrokenWithSceneContainer(bugId = 377868472) // mReorderingAllowed is broken with SceneContainer
    fun testLockscreenFullyShowing_groupAndSectionChangesNotAllowed() =
        testScope.runTest {
            // GIVEN the panel true expanded and device isn't pulsing
            setFullyDozed(false)
            setSleepy(false)
            setLockscreenShowing(1.0f)
            setPulsing(false)

            // THEN group changes are NOT allowed
            assertThat(notifStabilityManager.isGroupChangeAllowed(entry)).isFalse()
            assertThat(notifStabilityManager.isGroupPruneAllowed(groupEntry)).isFalse()

            // THEN section changes are NOT allowed
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isFalse()
        }

    @Test
    fun testPulsing_screenOff_groupAndSectionChangesNotAllowed() =
        testScope.runTest {
            // GIVEN the device is pulsing and screen is off
            setFullyDozed(true)
            setSleepy(true)
            setPulsing(true)

            // THEN group changes are NOT allowed
            assertThat(notifStabilityManager.isGroupChangeAllowed(entry)).isFalse()
            assertThat(notifStabilityManager.isGroupPruneAllowed(groupEntry)).isFalse()

            // THEN section changes are NOT allowed
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isFalse()
        }

    @Test
    fun testPulsing_panelNotExpanded_groupAndSectionChangesNotAllowed() =
        testScope.runTest {
            // GIVEN the device is pulsing and screen is off with the panel not expanded
            setFullyDozed(true)
            setSleepy(true)
            setPanelExpanded(false)
            setPulsing(true)

            // THEN group changes are NOT allowed
            assertThat(notifStabilityManager.isGroupChangeAllowed(entry)).isFalse()
            assertThat(notifStabilityManager.isGroupPruneAllowed(groupEntry)).isFalse()

            // THEN section changes are NOT allowed
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isFalse()
        }

    @Test
    fun testOverrideReorderingSuppression_onlySectionChangesAllowed() =
        testScope.runTest {
            // GIVEN section changes typically wouldn't be allowed because the panel is expanded and
            // we're not pulsing
            setFullyDozed(false)
            setSleepy(false)
            setPanelExpanded(true)
            setPulsing(true)

            // WHEN we temporarily allow section changes for this notification entry
            underTest.temporarilyAllowSectionChanges(
                entry,
                UseElapsedRealtimeForCreationTime.getCurrentTime(fakeSystemClock),
            )

            // THEN group changes aren't allowed
            assertThat(notifStabilityManager.isGroupChangeAllowed(entry)).isFalse()
            assertThat(notifStabilityManager.isGroupPruneAllowed(groupEntry)).isFalse()

            // THEN section changes are allowed for this notification but not other notifications
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isTrue()
            assertThat(
                    notifStabilityManager.isSectionChangeAllowed(
                        NotificationEntryBuilder().setPkg("testPkg2").build()
                    )
                )
                .isFalse()
        }

    @Test
    fun testTemporarilyAllowSectionChanges_callsInvalidate() =
        testScope.runTest {
            // GIVEN section changes typically wouldn't be allowed because the panel is expanded
            setFullyDozed(false)
            setSleepy(false)
            setPanelExpanded(true)
            setPulsing(false)

            // WHEN we temporarily allow section changes for this notification entry
            underTest.temporarilyAllowSectionChanges(
                entry,
                UseElapsedRealtimeForCreationTime.getCurrentTime(fakeSystemClock),
            )

            // THEN the notification list is invalidated
            verifyStabilityManagerWasInvalidated(times(1))
        }

    @Test
    fun testTemporarilyAllowSectionChanges_noInvalidationCalled() =
        testScope.runTest {
            // GIVEN section changes typically WOULD be allowed
            setFullyDozed(true)
            setSleepy(true)
            setPanelExpanded(false)
            setPulsing(false)

            // WHEN we temporarily allow section changes for this notification entry
            underTest.temporarilyAllowSectionChanges(
                entry,
                UseElapsedRealtimeForCreationTime.getCurrentTime(fakeSystemClock),
            )

            // THEN invalidate is not called because this entry was never suppressed from reordering
            verifyStabilityManagerWasInvalidated(never())
        }

    @Test
    fun testTemporarilyAllowSectionChangesTimeout() =
        testScope.runTest {
            // GIVEN section changes typically WOULD be allowed
            setFullyDozed(true)
            setSleepy(true)
            setPanelExpanded(false)
            setPulsing(false)
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isTrue()

            // WHEN we temporarily allow section changes for this notification entry
            underTest.temporarilyAllowSectionChanges(
                entry,
                UseElapsedRealtimeForCreationTime.getCurrentTime(fakeSystemClock),
            )

            // THEN invalidate is not called because this entry was never suppressed from
            // reordering;
            // THEN section changes are allowed for this notification
            verifyStabilityManagerWasInvalidated(never())
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isTrue()

            // WHEN we're pulsing (now disallowing reordering)
            setPulsing(true)

            // THEN we're still allowed to reorder this section because it's still in the list of
            // notifications to allow section changes
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isTrue()

            // WHEN the timeout for the temporarily allow section reordering runnable is finsihed
            fakeBackgroundExecutor.advanceClockToNext()
            fakeBackgroundExecutor.runNextReady()

            // THEN section changes aren't allowed anymore
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isFalse()
        }

    @Test
    fun testTemporarilyAllowSectionChanges_isPulsingChangeBeforeTimeout() =
        testScope.runTest {
            // GIVEN section changes typically wouldn't be allowed because the device is pulsing
            setFullyDozed(true)
            setSleepy(true)
            setPanelExpanded(false)
            setPulsing(true)

            // WHEN we temporarily allow section changes for this notification entry
            underTest.temporarilyAllowSectionChanges(
                entry,
                UseElapsedRealtimeForCreationTime.getCurrentTime(fakeSystemClock),
            )
            // can now reorder, so invalidates
            verifyStabilityManagerWasInvalidated(times(1))

            // WHEN reordering is now allowed because device isn't pulsing anymore
            setPulsing(false)

            // THEN invalidate isn't called a second time since reordering was already allowed
            verifyStabilityManagerWasInvalidated(times(1))
        }

    @Test
    fun testMovingVisibleHeadsUpNotAllowed() =
        testScope.runTest {
            // GIVEN stability enforcing conditions
            setPanelExpanded(true)
            setSleepy(false)

            // WHEN a notification is alerting and visible
            headsUpRepository.setHeadsUpKeys(entry.key)
            whenever(visibilityLocationProvider.isInVisibleLocation(any())).thenReturn(true)

            // THEN the notification cannot be reordered
            assertThat(notifStabilityManager.isEntryReorderingAllowed(entry)).isFalse()
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isFalse()
        }

    @Test
    fun testMovingInvisibleHeadsUpAllowed() =
        testScope.runTest {
            // GIVEN stability enforcing conditions
            setPanelExpanded(true)
            setSleepy(false)

            // WHEN a notification is alerting but not visible
            headsUpRepository.setHeadsUpKeys(entry.key)
            whenever(visibilityLocationProvider.isInVisibleLocation(any())).thenReturn(false)

            // THEN the notification can be reordered
            assertThat(notifStabilityManager.isEntryReorderingAllowed(entry)).isTrue()
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isTrue()
        }

    @Test
    fun testNeverSuppressedChanges_noInvalidationCalled() =
        testScope.runTest {
            // GIVEN no notifications are currently being suppressed from grouping nor being sorted

            // WHEN device isn't pulsing anymore

            setPulsing(false)

            // WHEN fully dozed
            setFullyDozed(true)

            // WHEN sleepy
            setSleepy(true)

            // WHEN panel isn't expanded
            setPanelExpanded(false)

            // THEN we never see any calls to invalidate since there weren't any notifications that
            // were being suppressed from grouping or section changes
            verifyStabilityManagerWasInvalidated(never())
        }

    @Test
    fun testNotSuppressingGroupChangesAnymore_invalidationCalled() =
        testScope.runTest {
            // GIVEN visual stability is being maintained b/c panel is expanded
            setPulsing(false)
            setFullyDozed(false)
            setSleepy(false)
            setPanelExpanded(true)

            assertThat(notifStabilityManager.isGroupChangeAllowed(entry)).isFalse()
            assertThat(notifStabilityManager.isGroupPruneAllowed(groupEntry)).isFalse()

            // WHEN the panel isn't expanded anymore
            setPanelExpanded(false)

            // THEN invalidate is called because we were previously suppressing a group change
            verifyStabilityManagerWasInvalidated(times(1))
        }

    @Test
    fun testNotLaunchingActivityAnymore_invalidationCalled() {
        // GIVEN visual stability is being maintained b/c animation is playing
        setActivityLaunching(true)

        assertThat(notifStabilityManager.isPipelineRunAllowed()).isFalse()

        // WHEN the animation has stopped playing
        setActivityLaunching(false)

        // THEN invalidate is called, b/c we were previously suppressing the pipeline from running
        verifyStabilityManagerWasInvalidated(times(1))
    }

    @Test
    fun testNotCollapsingPanelAnymore_invalidationCalled() {
        // GIVEN visual stability is being maintained b/c animation is playing
        setPanelCollapsing(true)

        assertThat(notifStabilityManager.isPipelineRunAllowed()).isFalse()

        // WHEN the animation has stopped playing
        setPanelCollapsing(false)

        // THEN invalidate is called, b/c we were previously suppressing the pipeline from running
        verifyStabilityManagerWasInvalidated(times(1))
    }

    @Test
    @EnableFlags(Flags.FLAG_CHECK_LOCKSCREEN_GONE_TRANSITION)
    @DisableSceneContainer
    fun testNotLockscreenInGoneTransitionLegacy_invalidationCalled() {
        // GIVEN visual stability is being maintained b/c animation is playing
        whenever(keyguardStateController.isKeyguardFadingAway).thenReturn(true)
        underTest.mKeyguardFadeAwayAnimationCallback.onKeyguardFadingAwayChanged()

        assertThat(notifStabilityManager.isPipelineRunAllowed()).isFalse()

        // WHEN the animation has stopped playing
        whenever(keyguardStateController.isKeyguardFadingAway).thenReturn(false)
        underTest.mKeyguardFadeAwayAnimationCallback.onKeyguardFadingAwayChanged()

        // THEN invalidate is called, b/c we were previously suppressing the pipeline from running
        verifyStabilityManagerWasInvalidated(times(1))
    }

    @Test
    @EnableFlags(Flags.FLAG_CHECK_LOCKSCREEN_GONE_TRANSITION)
    @EnableSceneContainer
    @BrokenWithSceneContainer(bugId = 377868472) // mReorderingAllowed is broken with SceneContainer
    fun testNotLockscreenInGoneTransition_invalidationCalled() =
        testScope.runTest {
            // GIVEN visual stability is being maintained b/c animation is playing
            kosmos.fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.GONE,
                    1f,
                    TransitionState.RUNNING,
                ),
                /* validateStep = */ false,
            )
            testScope.testScheduler.runCurrent()
            assertThat(notifStabilityManager.isPipelineRunAllowed()).isFalse()

            // WHEN the animation has stopped playing
            kosmos.fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.GONE,
                    1f,
                    TransitionState.FINISHED,
                ),
                /* validateStep = */ false,
            )
            testScope.testScheduler.runCurrent()

            // THEN invalidate is called, b/c we were previously suppressing the pipeline from
            // running
            verifyStabilityManagerWasInvalidated(times(1))
        }

    @Test
    fun testNeverSuppressPipelineRunFromPanelCollapse_noInvalidationCalled() {
        // GIVEN animation is playing
        setPanelCollapsing(true)

        // WHEN the animation has stopped playing
        setPanelCollapsing(false)

        // THEN invalidate is not called, b/c nothing has been suppressed
        verifyStabilityManagerWasInvalidated(never())
    }

    @Test
    fun testNeverSuppressPipelineRunFromLaunchActivity_noInvalidationCalled() {
        // GIVEN animation is playing
        setActivityLaunching(true)

        // WHEN the animation has stopped playing
        setActivityLaunching(false)

        // THEN invalidate is not called, b/c nothing has been suppressed
        verifyStabilityManagerWasInvalidated(never())
    }

    @Test
    fun testNotSuppressingEntryReorderingAnymoreWillInvalidate() =
        testScope.runTest {
            // GIVEN visual stability is being maintained b/c panel is expanded
            setPulsing(false)
            setFullyDozed(false)
            setSleepy(false)
            setPanelExpanded(true)
            setCommunalShowing(false)

            assertThat(notifStabilityManager.isEntryReorderingAllowed(entry)).isFalse()
            // The pipeline still has to report back that entry reordering was suppressed
            notifStabilityManager.onEntryReorderSuppressed()

            // WHEN the panel isn't expanded anymore
            setPanelExpanded(false)

            // THEN invalidate is called because we were previously suppressing an entry reorder
            verifyStabilityManagerWasInvalidated(times(1))
        }

    @Test
    @BrokenWithSceneContainer(bugId = 377868472) // mReorderingAllowed is broken with SceneContainer
    fun testCommunalShowingWillNotSuppressReordering() =
        testScope.runTest {
            // GIVEN panel is expanded, communal is showing, and QS is collapsed
            setPulsing(false)
            setFullyDozed(false)
            setSleepy(false)
            setPanelExpanded(true)
            setQsExpanded(false)
            setCommunalShowing(true)

            // THEN Reordering should be allowed
            assertThat(notifStabilityManager.isEntryReorderingAllowed(entry)).isTrue()
        }

    @Test
    fun testQsExpandedOverCommunalWillSuppressReordering() =
        testScope.runTest {
            // GIVEN panel is expanded and communal is showing, but QS is expanded
            setPulsing(false)
            setFullyDozed(false)
            setSleepy(false)
            setPanelExpanded(true)
            setQsExpanded(true)
            setCommunalShowing(true)

            // THEN Reordering should not be allowed
            assertThat(notifStabilityManager.isEntryReorderingAllowed(entry)).isFalse()
        }

    @Test
    fun testQueryingEntryReorderingButNotReportingReorderSuppressedDoesNotInvalidate() =
        testScope.runTest {
            // GIVEN visual stability is being maintained b/c panel is expanded
            setPulsing(false)
            setFullyDozed(false)
            setSleepy(false)
            setPanelExpanded(true)

            assertThat(notifStabilityManager.isEntryReorderingAllowed(entry)).isFalse()

            // WHEN the panel isn't expanded anymore
            setPanelExpanded(false)

            // THEN invalidate is not called because we were not told that an entry reorder was
            // suppressed
            verifyStabilityManagerWasInvalidated(never())
        }

    @Test
    fun testHeadsUp_allowedToChangeGroupAndSection() =
        testScope.runTest {
            // GIVEN group + section changes disallowed
            setFullyDozed(false)
            setSleepy(false)
            setPanelExpanded(true)
            setPulsing(true)
            assertThat(notifStabilityManager.isGroupChangeAllowed(entry)).isFalse()
            assertThat(notifStabilityManager.isGroupPruneAllowed(groupEntry)).isFalse()
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isFalse()

            // GIVEN mEntry is a HUN
            headsUpRepository.setHeadsUpKeys(entry.key)

            // THEN group + section changes are allowed
            assertThat(notifStabilityManager.isGroupChangeAllowed(entry)).isTrue()
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isTrue()

            // BUT pruning the group for which this is the summary would still NOT be allowed.
            assertThat(notifStabilityManager.isGroupPruneAllowed(groupEntry)).isFalse()
        }

    @Test
    fun everyChangeAllowed_onReorderingEnabled_legacy() =
        testScope.runTest {
            assumeFalse(StabilizeHeadsUpGroup.isEnabled)
            // GIVEN - reordering is allowed.
            setPulsing(false)
            setPanelExpanded(false)

            // THEN
            assertThat(notifStabilityManager.isEveryChangeAllowed()).isTrue()
            assertThat(notifStabilityManager.isGroupChangeAllowed(entry)).isTrue()
            assertThat(notifStabilityManager.isGroupPruneAllowed(groupEntry)).isTrue()
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isTrue()
            assertThat(notifStabilityManager.isEntryReorderingAllowed(entry)).isTrue()
        }

    @Test
    fun everyChangeAllowed_noActiveHeadsUpGroup_onReorderingEnabled() =
        testScope.runTest {
            assumeTrue(StabilizeHeadsUpGroup.isEnabled)
            // GIVEN - reordering is allowed.
            setPulsing(false)
            setPanelExpanded(false)

            // GIVEN - empty heads-up-group keys
            underTest.setHeadsUpGroupKeys(setOf())

            // THEN
            assertThat(notifStabilityManager.isEveryChangeAllowed()).isTrue()
            assertThat(notifStabilityManager.isGroupChangeAllowed(entry)).isTrue()
            assertThat(notifStabilityManager.isGroupPruneAllowed(groupEntry)).isTrue()
            assertThat(notifStabilityManager.isSectionChangeAllowed(entry)).isTrue()
            assertThat(notifStabilityManager.isEntryReorderingAllowed(entry)).isTrue()
        }

    @Test
    fun everyChangeDisallowed_activeHeadsUpGroup_onReorderingEnabled() =
        testScope.runTest {
            assumeTrue(StabilizeHeadsUpGroup.isEnabled)
            // GIVEN - reordering is allowed.
            setPulsing(false)
            setPanelExpanded(false)

            // GIVEN - there is a group heads-up.
            underTest.setHeadsUpGroupKeys(setOf("heads_up_group_key"))

            // THEN
            assertThat(notifStabilityManager.isEveryChangeAllowed()).isFalse()
        }

    @Test
    fun nonHeadsUpGroup_changesAllowed_onReorderingEnabled() =
        testScope.runTest {
            assumeTrue(StabilizeHeadsUpGroup.isEnabled)
            // GIVEN - reordering is allowed.
            setPulsing(false)
            setPanelExpanded(false)

            //  GIVEN - there is a group heads-up.
            val headsUpGroupKey = "heads_up_group_key"
            underTest.setHeadsUpGroupKeys(setOf(headsUpGroupKey))
            headsUpRepository.setHeadsUpKeys(headsUpGroupKey)

            // GIVEN - HUN Group Summary
            val nonHeadsUpGroupSummary: NotificationEntry = mock()
            whenever(nonHeadsUpGroupSummary.key).thenReturn("non_heads_up_group_key")
            whenever(nonHeadsUpGroupSummary.isSummaryWithChildren).thenReturn(true)
            val nonHeadsUpGroupEntry: GroupEntry = mock()
            whenever(nonHeadsUpGroupEntry.summary).thenReturn(nonHeadsUpGroupSummary)
            whenever(nonHeadsUpGroupEntry.representativeEntry).thenReturn(nonHeadsUpGroupSummary)

            // THEN
            assertThat(notifStabilityManager.isGroupPruneAllowed(nonHeadsUpGroupEntry)).isTrue()
            assertThat(notifStabilityManager.isEntryReorderingAllowed(nonHeadsUpGroupEntry))
                .isTrue()
        }

    @Test
    fun headsUpGroup_changesDisallowed_onReorderingEnabled() =
        testScope.runTest {
            assumeTrue(StabilizeHeadsUpGroup.isEnabled)
            // GIVEN - reordering is allowed.
            setPulsing(false)
            setPanelExpanded(false)

            //  GIVEN - there is a group heads-up.
            val headsUpGroupKey = "heads_up_group_key"
            underTest.setHeadsUpGroupKeys(setOf(headsUpGroupKey))
            headsUpRepository.setHeadsUpKeys(headsUpGroupKey)

            // GIVEN - HUN Group
            val headsUpGroupSummary: NotificationEntry = mock()
            whenever(headsUpGroupSummary.rowIsChildInGroup()).thenReturn(false)
            whenever(headsUpGroupSummary.key).thenReturn(headsUpGroupKey)
            whenever(headsUpGroupSummary.isSummaryWithChildren).thenReturn(true)

            val headsUpGroupEntry: GroupEntry = mock()
            whenever(headsUpGroupEntry.summary).thenReturn(headsUpGroupSummary)
            whenever(headsUpGroupEntry.representativeEntry).thenReturn(headsUpGroupSummary)

            whenever(headsUpGroupSummary.parent).thenReturn(headsUpGroupEntry)

            // GIVEN - HUN is in visible location
            whenever(visibilityLocationProvider.isInVisibleLocation(headsUpGroupSummary))
                .thenReturn(true)

            // THEN
            assertThat(notifStabilityManager.isGroupPruneAllowed(headsUpGroupEntry)).isFalse()
            assertThat(notifStabilityManager.isEntryReorderingAllowed(headsUpGroupEntry)).isFalse()
        }

    @Test
    fun headsUpGroupSummaries_changesDisallowed_onReorderingEnabled() =
        testScope.runTest {
            assumeTrue(StabilizeHeadsUpGroup.isEnabled)
            // GIVEN - reordering is allowed.
            setPulsing(false)
            setPanelExpanded(false)

            //  GIVEN - there is a group heads-up.
            val headsUpGroupKey = "heads_up_group_key"
            underTest.setHeadsUpGroupKeys(setOf(headsUpGroupKey))
            headsUpRepository.setHeadsUpKeys(headsUpGroupKey)

            // GIVEN - HUN Group
            val headsUpGroupSummary: NotificationEntry = mock()
            whenever(headsUpGroupSummary.rowIsChildInGroup()).thenReturn(false)
            whenever(headsUpGroupSummary.key).thenReturn(headsUpGroupKey)
            whenever(headsUpGroupSummary.isSummaryWithChildren).thenReturn(true)

            val headsUpGroupEntry: GroupEntry = mock()
            whenever(headsUpGroupEntry.summary).thenReturn(headsUpGroupSummary)
            whenever(headsUpGroupEntry.representativeEntry).thenReturn(headsUpGroupSummary)

            whenever(headsUpGroupSummary.parent).thenReturn(headsUpGroupEntry)

            // GIVEN - HUN is in visible location
            whenever(visibilityLocationProvider.isInVisibleLocation(headsUpGroupSummary))
                .thenReturn(true)

            // THEN
            assertThat(notifStabilityManager.isGroupChangeAllowed(headsUpGroupSummary)).isFalse()
            assertThat(notifStabilityManager.isEntryReorderingAllowed(headsUpGroupSummary))
                .isFalse()
            assertThat(notifStabilityManager.isSectionChangeAllowed(headsUpGroupSummary)).isFalse()
        }

    @Test
    fun notificationInNonHUNGroup_changesAllowed_onReorderingEnabled() =
        testScope.runTest {
            assumeTrue(StabilizeHeadsUpGroup.isEnabled)
            // GIVEN - reordering is allowed.
            setPulsing(false)
            setPanelExpanded(false)

            //  GIVEN - there is a group heads-up.
            val headsUpGroupKey = "heads_up_group_key"
            underTest.setHeadsUpGroupKeys(setOf(headsUpGroupKey))
            headsUpRepository.setHeadsUpKeys(headsUpGroupKey)

            // GIVEN - non HUN parent Group Summary
            val groupSummary: NotificationEntry = mock()
            whenever(groupSummary.key).thenReturn("non_heads_up_group_key")
            whenever(groupSummary.isSummaryWithChildren).thenReturn(true)

            val nonHeadsUpGroupEntry: GroupEntry = mock()
            whenever(nonHeadsUpGroupEntry.summary).thenReturn(groupSummary)
            whenever(nonHeadsUpGroupEntry.representativeEntry).thenReturn(groupSummary)

            // GIVEN - child entry in a non heads-up group.
            val childEntry: NotificationEntry = mock()
            whenever(childEntry.rowIsChildInGroup()).thenReturn(true)
            whenever(childEntry.parent).thenReturn(nonHeadsUpGroupEntry)
            whenever(childEntry.parent).thenReturn(nonHeadsUpGroupEntry)

            // THEN
            assertThat(notifStabilityManager.isGroupChangeAllowed(childEntry)).isTrue()
            assertThat(notifStabilityManager.isSectionChangeAllowed(childEntry)).isTrue()
            assertThat(notifStabilityManager.isEntryReorderingAllowed(nonHeadsUpGroupEntry))
                .isTrue()
        }

    @Test
    fun notificationInHUNGroup_changesDisallowed_reorderingEnabled() =
        testScope.runTest {
            assumeTrue(StabilizeHeadsUpGroup.isEnabled)
            // GIVEN - reordering is allowed.
            setPulsing(false)
            setPanelExpanded(false)

            // GIVEN - there is a group heads-up.
            val headsUpGroupKey = "heads_up_group_key"
            underTest.setHeadsUpGroupKeys(setOf(headsUpGroupKey))
            headsUpRepository.setHeadsUpKeys(headsUpGroupKey)

            // GIVEN - HUN Group Summary
            val headsUpGroupSummary: NotificationEntry = mock()
            whenever(headsUpGroupSummary.rowIsChildInGroup()).thenReturn(false)
            whenever(headsUpGroupSummary.key).thenReturn(headsUpGroupKey)
            whenever(headsUpGroupSummary.isSummaryWithChildren).thenReturn(true)

            val nonHeadsUpGroupEntry: GroupEntry = mock()
            whenever(nonHeadsUpGroupEntry.summary).thenReturn(headsUpGroupSummary)
            whenever(nonHeadsUpGroupEntry.representativeEntry).thenReturn(headsUpGroupSummary)

            // GIVEN - child entry in a non heads-up group.
            val childEntry: NotificationEntry =
                mock<NotificationEntry>().apply { whenever(key).thenReturn("child") }
            whenever(childEntry.rowIsChildInGroup()).thenReturn(true)
            whenever(childEntry.parent).thenReturn(nonHeadsUpGroupEntry)

            // GIVEN - HUN is in visible location
            whenever(visibilityLocationProvider.isInVisibleLocation(headsUpGroupSummary))
                .thenReturn(true)

            // THEN
            assertThat(notifStabilityManager.isGroupChangeAllowed(childEntry)).isFalse()
            assertThat(notifStabilityManager.isSectionChangeAllowed(childEntry)).isFalse()
            assertThat(notifStabilityManager.isEntryReorderingAllowed(childEntry)).isFalse()
        }

    private fun verifyStabilityManagerWasInvalidated(mode: VerificationMode) {
        verify(invalidateListener, mode).onPluggableInvalidated(eq(notifStabilityManager), any())
    }

    private fun setActivityLaunching(activityLaunching: Boolean) {
        kosmos.shadeAnimationInteractor.setIsLaunchingActivity(activityLaunching)
        testScope.testScheduler.runCurrent()
    }

    private fun setPanelCollapsing(collapsing: Boolean) {
        shadeRepository.setLegacyIsClosing(collapsing)
        testScope.testScheduler.runCurrent()
    }

    private fun setCommunalShowing(isShowing: Boolean) {
        val showingFlow =
            MutableStateFlow<ObservableTransitionState>(
                ObservableTransitionState.Idle(
                    if (isShowing) CommunalScenes.Communal else CommunalScenes.Blank
                )
            )
        kosmos.communalSceneInteractor.setTransitionState(showingFlow)
        testScope.testScheduler.runCurrent()
    }

    private fun setQsExpanded(isExpanded: Boolean) {
        kosmos.shadeRepository.setQsExpansion(if (isExpanded) 1.0f else 0.0f)
        testScope.testScheduler.runCurrent()
    }

    private fun setPulsing(pulsing: Boolean) = statusBarStateListener.onPulsingChanged(pulsing)

    private fun setFullyDozed(fullyDozed: Boolean) {
        val dozeAmount = if (fullyDozed) 1f else 0f
        statusBarStateListener.onDozeAmountChanged(dozeAmount, dozeAmount)
    }

    private fun setSleepy(sleepy: Boolean) {
        if (sleepy) {
            wakefulnessObserver.onFinishedGoingToSleep()
        } else {
            wakefulnessObserver.onStartedWakingUp()
        }
    }

    private suspend fun setPanelExpanded(expanded: Boolean) =
        setPanelExpandedAndLockscreenShowing(expanded, /* lockscreenShowing= */ 0.0f)

    private suspend fun setLockscreenShowing(lockscreenShowing: Float) =
        setPanelExpandedAndLockscreenShowing(/* panelExpanded= */ false, lockscreenShowing)

    private suspend fun setPanelExpandedAndLockscreenShowing(
        panelExpanded: Boolean,
        lockscreenShowing: Float,
    ) {
        if (SceneContainerFlag.isEnabled) {
            statusBarStateListener.onExpandedChanged(panelExpanded)
            kosmos.fakeKeyguardTransitionRepository.sendTransitionStep(
                makeLockscreenTransitionStep(lockscreenShowing),
                /* validateStep = */ false,
            )
        } else {
            statusBarStateListener.onExpandedChanged(panelExpanded || lockscreenShowing > 0.0f)
        }
    }

    private fun makeLockscreenTransitionStep(value: Float): TransitionStep {
        return when (value) {
            0.0f -> TransitionStep(KeyguardState.GONE)
            1.0f -> TransitionStep(KeyguardState.LOCKSCREEN)
            else ->
                TransitionStep(
                    KeyguardState.GONE,
                    KeyguardState.LOCKSCREEN,
                    value,
                    TransitionState.RUNNING,
                )
        }
    }
}

private fun FakeHeadsUpNotificationRepository.setHeadsUpKeys(vararg keys: String) {
    setNotifications(keys.map { FakeHeadsUpRowRepository(key = it) })
}
