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

package com.android.systemui.statusbar.chips.call.ui.viewmodel

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.view.View
import androidx.test.filters.SmallTest
import com.android.internal.logging.InstanceId
import com.android.systemui.SysuiTestCase
import com.android.systemui.activity.data.repository.activityManagerRepository
import com.android.systemui.activity.data.repository.fake
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.ContentDescription.Companion.loadContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.plugins.activityStarter
import com.android.systemui.res.R
import com.android.systemui.statusbar.StatusBarIconView
import com.android.systemui.statusbar.chips.StatusBarChipsReturnAnimations
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.view.ChipBackgroundContainer
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.core.StatusBarRootModernization
import com.android.systemui.statusbar.phone.ongoingcall.DisableChipsModernization
import com.android.systemui.statusbar.phone.ongoingcall.EnableChipsModernization
import com.android.systemui.statusbar.phone.ongoingcall.StatusBarChipsModernization
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper.addOngoingCallState
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper.removeOngoingCallState
import com.android.systemui.testKosmos
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class CallChipViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {
    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val chipBackgroundView = mock<ChipBackgroundContainer>()
    private val chipView =
        mock<View>().apply {
            whenever(
                    this.requireViewById<ChipBackgroundContainer>(
                        R.id.ongoing_activity_chip_background
                    )
                )
                .thenReturn(chipBackgroundView)
        }
    private val mockExpandable: Expandable =
        mock<Expandable>().apply { whenever(dialogTransitionController(any())).thenReturn(mock()) }

    private val Kosmos.underTest by Kosmos.Fixture { callChipViewModel }

    @Test
    fun chip_noCall_isHidden() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            removeOngoingCallState("testKey")

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
        }

    @Test
    fun chip_inCall_hasKeyWithPrefix() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 0, isAppVisible = false)

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).key)
                .startsWith(CallChipViewModel.KEY_PREFIX)
        }

    @Test
    fun chip_inCall_zeroStartTime_isShownAsIconOnly_withData() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            val instanceId = InstanceId.fakeInstanceId(10)
            addOngoingCallState(startTimeMs = 0, isAppVisible = false, instanceId = instanceId)

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active.IconOnly::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat((latest as OngoingActivityChipModel.Active).isImportantForPrivacy).isFalse()
            assertThat((latest as OngoingActivityChipModel.Active).instanceId).isEqualTo(instanceId)
        }

    @Test
    fun chip_inCall_negativeStartTime_isShownAsIconOnly_withData() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            val instanceId = InstanceId.fakeInstanceId(10)
            addOngoingCallState(startTimeMs = -2, isAppVisible = false, instanceId = instanceId)

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active.IconOnly::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat((latest as OngoingActivityChipModel.Active).isImportantForPrivacy).isFalse()
            assertThat((latest as OngoingActivityChipModel.Active).instanceId).isEqualTo(instanceId)
        }

    @Test
    fun chip_inCall_positiveStartTime_isShownAsTimer_withData() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            val instanceId = InstanceId.fakeInstanceId(10)
            addOngoingCallState(startTimeMs = 345, isAppVisible = false, instanceId = instanceId)

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active.Timer::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat((latest as OngoingActivityChipModel.Active).isImportantForPrivacy).isFalse()
            assertThat((latest as OngoingActivityChipModel.Active).instanceId).isEqualTo(instanceId)
        }

    @Test
    @DisableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipLegacy_inCallWithVisibleApp_zeroStartTime_isHiddenAsInactive() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 0, isAppVisible = true)

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    @EnableChipsModernization
    fun chipWithReturnAnimation_inCallWithVisibleApp_zeroStartTime_isHiddenAsIconOnly() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 0, isAppVisible = true)

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active.IconOnly::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isTrue()
            assertThat((latest as OngoingActivityChipModel.Active).isImportantForPrivacy).isFalse()
        }

    @Test
    @DisableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipLegacy_inCallWithVisibleApp_negativeStartTime_isHiddenAsInactive() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = -2, isAppVisible = true)

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    @EnableChipsModernization
    fun chipWithReturnAnimation_inCallWithVisibleApp_negativeStartTime_isHiddenAsIconOnly() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = -2, isAppVisible = true)

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active.IconOnly::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isTrue()
            assertThat((latest as OngoingActivityChipModel.Active).isImportantForPrivacy).isFalse()
        }

    @Test
    @DisableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipLegacy_inCallWithVisibleApp_positiveStartTime_isHiddenAsInactive() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 345, isAppVisible = true)

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    @EnableChipsModernization
    fun chipWithReturnAnimation_inCallWithVisibleApp_positiveStartTime_isHiddenAsTimer() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 345, isAppVisible = true)

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active.Timer::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isTrue()
            assertThat((latest as OngoingActivityChipModel.Active).isImportantForPrivacy).isFalse()
        }

    @Test
    fun chip_inCall_startTimeConvertedToElapsedRealtime() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            kosmos.fakeSystemClock.setCurrentTimeMillis(3000)
            kosmos.fakeSystemClock.setElapsedRealtime(400_000)

            addOngoingCallState(startTimeMs = 1000)

            // The OngoingCallModel start time is relative to currentTimeMillis, so this call
            // started 2000ms ago (1000 - 3000). The OngoingActivityChipModel start time needs to be
            // relative to elapsedRealtime, so it should be 2000ms before the elapsed realtime set
            // on the clock.
            assertThat((latest as OngoingActivityChipModel.Active.Timer).startTimeMs)
                .isEqualTo(398_000)
        }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun chip_positiveStartTime_connectedDisplaysFlagOn_iconIsNotifIcon() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            val notifKey = "testNotifKey"
            addOngoingCallState(startTimeMs = 1000, statusBarChipIconView = null, key = notifKey)

            assertThat((latest as OngoingActivityChipModel.Active).icon)
                .isInstanceOf(
                    OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon::class.java
                )
            val actualNotifKey =
                (((latest as OngoingActivityChipModel.Active).icon)
                        as OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon)
                    .notificationKey
            assertThat(actualNotifKey).isEqualTo(notifKey)
        }

    @Test
    @DisableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun chip_zeroStartTime_cdFlagOff_iconIsNotifIcon_withContentDescription() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            val notifIcon = createStatusBarIconViewOrNull()
            addOngoingCallState(
                startTimeMs = 0,
                statusBarChipIconView = notifIcon,
                appName = "Fake app name",
            )

            assertThat((latest as OngoingActivityChipModel.Active).icon)
                .isInstanceOf(OngoingActivityChipModel.ChipIcon.StatusBarView::class.java)
            val actualIcon =
                (latest as OngoingActivityChipModel.Active).icon
                    as OngoingActivityChipModel.ChipIcon.StatusBarView
            assertThat(actualIcon.impl).isEqualTo(notifIcon)
            assertThat(actualIcon.contentDescription.loadContentDescription(context))
                .contains("Ongoing call")
            assertThat(actualIcon.contentDescription.loadContentDescription(context))
                .contains("Fake app name")
        }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun chip_zeroStartTime_cdFlagOn_iconIsNotifKeyIcon_withContentDescription() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(
                key = "notifKey",
                statusBarChipIconView = createStatusBarIconViewOrNull(),
                appName = "Fake app name",
            )

            assertThat((latest as OngoingActivityChipModel.Active).icon)
                .isInstanceOf(
                    OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon::class.java
                )
            val actualIcon =
                (latest as OngoingActivityChipModel.Active).icon
                    as OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon
            assertThat(actualIcon.notificationKey).isEqualTo("notifKey")
            assertThat(actualIcon.contentDescription.loadContentDescription(context))
                .contains("Ongoing call")
            assertThat(actualIcon.contentDescription.loadContentDescription(context))
                .contains("Fake app name")
        }

    @Test
    @DisableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun chip_notifIconFlagOn_butNullNotifIcon_cdFlagOff_iconIsPhone() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(statusBarChipIconView = null)

            assertThat((latest as OngoingActivityChipModel.Active).icon)
                .isInstanceOf(OngoingActivityChipModel.ChipIcon.SingleColorIcon::class.java)
            val icon =
                (((latest as OngoingActivityChipModel.Active).icon)
                        as OngoingActivityChipModel.ChipIcon.SingleColorIcon)
                    .impl as Icon.Resource
            assertThat(icon.res).isEqualTo(com.android.internal.R.drawable.ic_phone)
            assertThat(icon.contentDescription).isNotNull()
        }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun chip_notifIconFlagOn_butNullNotifIcon_cdFlagOn_iconIsNotifKeyIcon_withContentDescription() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(
                key = "notifKey",
                statusBarChipIconView = null,
                appName = "Fake app name",
            )

            assertThat((latest as OngoingActivityChipModel.Active).icon)
                .isInstanceOf(
                    OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon::class.java
                )
            val actualIcon =
                (latest as OngoingActivityChipModel.Active).icon
                    as OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon
            assertThat(actualIcon.notificationKey).isEqualTo("notifKey")
            assertThat(actualIcon.contentDescription.loadContentDescription(context))
                .contains("Ongoing call")
            assertThat(actualIcon.contentDescription.loadContentDescription(context))
                .contains("Fake app name")
        }

    @Test
    fun chip_positiveStartTime_colorsAreAccentThemed() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 1000, promotedContent = null)

            assertThat((latest as OngoingActivityChipModel.Active).colors)
                .isEqualTo(ColorsModel.AccentThemed)
        }

    @Test
    fun chip_zeroStartTime_colorsAreAccentThemed() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 0, promotedContent = null)

            assertThat((latest as OngoingActivityChipModel.Active).colors)
                .isEqualTo(ColorsModel.AccentThemed)
        }

    @Test
    fun chip_resetsCorrectly() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)
            kosmos.fakeSystemClock.setCurrentTimeMillis(3000)
            kosmos.fakeSystemClock.setElapsedRealtime(400_000)

            // Start a call
            addOngoingCallState(key = "testKey", startTimeMs = 1000)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active.Timer).startTimeMs)
                .isEqualTo(398_000)

            // End the call
            removeOngoingCallState(key = "testKey")
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)

            // Let 100_000ms elapse
            kosmos.fakeSystemClock.setCurrentTimeMillis(103_000)
            kosmos.fakeSystemClock.setElapsedRealtime(500_000)

            // Start a new call, which started 1000ms ago
            addOngoingCallState(key = "testKey", startTimeMs = 102_000)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active.Timer).startTimeMs)
                .isEqualTo(499_000)
        }

    @Test
    @DisableChipsModernization
    fun chip_inCall_nullIntent_nullClickListener() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(contentIntent = null)

            assertThat((latest as OngoingActivityChipModel.Active).onClickListenerLegacy).isNull()
        }

    @Test
    @DisableChipsModernization
    fun chip_inCall_positiveStartTime_validIntent_clickListenerLaunchesIntent() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            val pendingIntent = mock<PendingIntent>()
            addOngoingCallState(startTimeMs = 1000, contentIntent = pendingIntent)
            val clickListener = (latest as OngoingActivityChipModel.Active).onClickListenerLegacy
            assertThat(clickListener).isNotNull()

            clickListener!!.onClick(chipView)

            // Ensure that the SysUI didn't modify the notification's intent by verifying it
            // directly matches the `PendingIntent` set -- see b/212467440.
            verify(kosmos.activityStarter).postStartActivityDismissingKeyguard(pendingIntent, null)
        }

    @Test
    @DisableChipsModernization
    fun chip_inCall_zeroStartTime_validIntent_clickListenerLaunchesIntent() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            val pendingIntent = mock<PendingIntent>()
            addOngoingCallState(startTimeMs = 0, contentIntent = pendingIntent)
            val clickListener = (latest as OngoingActivityChipModel.Active).onClickListenerLegacy

            assertThat(clickListener).isNotNull()

            clickListener!!.onClick(chipView)

            // Ensure that the SysUI didn't modify the notification's intent by verifying it
            // directly matches the `PendingIntent` set -- see b/212467440.
            verify(kosmos.activityStarter).postStartActivityDismissingKeyguard(pendingIntent, null)
        }

    @Test
    @EnableChipsModernization
    fun chip_inCall_nullIntent_noneClickBehavior() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            addOngoingCallState(startTimeMs = 1000, contentIntent = null)

            assertThat((latest as OngoingActivityChipModel.Active).clickBehavior)
                .isInstanceOf(OngoingActivityChipModel.ClickBehavior.None::class.java)
        }

    @Test
    @EnableChipsModernization
    fun chip_inCall_positiveStartTime_validIntent_clickBehaviorLaunchesIntent() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            val pendingIntent = mock<PendingIntent>()
            addOngoingCallState(startTimeMs = 1000, contentIntent = pendingIntent)

            val clickBehavior = (latest as OngoingActivityChipModel.Active).clickBehavior
            assertThat(clickBehavior)
                .isInstanceOf(OngoingActivityChipModel.ClickBehavior.ExpandAction::class.java)
            (clickBehavior as OngoingActivityChipModel.ClickBehavior.ExpandAction).onClick(
                mockExpandable
            )

            // Ensure that the SysUI didn't modify the notification's intent by verifying it
            // directly matches the `PendingIntent` set -- see b/212467440.
            verify(kosmos.activityStarter).postStartActivityDismissingKeyguard(pendingIntent, null)
        }

    @Test
    @EnableChipsModernization
    fun chip_inCall_zeroStartTime_validIntent_clickBehaviorLaunchesIntent() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            val pendingIntent = mock<PendingIntent>()
            addOngoingCallState(startTimeMs = 0, contentIntent = pendingIntent)

            val clickBehavior = (latest as OngoingActivityChipModel.Active).clickBehavior
            assertThat(clickBehavior)
                .isInstanceOf(OngoingActivityChipModel.ClickBehavior.ExpandAction::class.java)
            (clickBehavior as OngoingActivityChipModel.ClickBehavior.ExpandAction).onClick(
                mockExpandable
            )

            // Ensure that the SysUI didn't modify the notification's intent by verifying it
            // directly matches the `PendingIntent` set -- see b/212467440.
            verify(kosmos.activityStarter).postStartActivityDismissingKeyguard(pendingIntent, null)
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    @EnableChipsModernization
    fun chipWithReturnAnimation_updatesCorrectly_withStateAndTransitionState() =
        kosmos.runTest {
            val pendingIntent = mock<PendingIntent>()
            val intent = mock<Intent>()
            whenever(pendingIntent.intent).thenReturn(intent)
            val component = mock<ComponentName>()
            whenever(intent.component).thenReturn(component)

            val expandable = mock<Expandable>()
            val activityController = mock<ActivityTransitionAnimator.Controller>()
            whenever(
                    expandable.activityTransitionController(
                        anyOrNull(),
                        anyOrNull(),
                        any(),
                        anyOrNull(),
                        any(),
                    )
                )
                .thenReturn(activityController)

            val latest by collectLastValue(underTest.chip)

            // Start off with no call.
            removeOngoingCallState(key = NOTIFICATION_KEY)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
            assertThat(latest!!.transitionManager!!.controllerFactory).isNull()

            // Call starts [NoCall -> InCall(isAppVisible=true), NoTransition].
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 345,
                contentIntent = pendingIntent,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isTrue()
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isFalse()
            val factory = latest!!.transitionManager!!.controllerFactory
            assertThat(factory!!.component).isEqualTo(component)

            // Request a return transition [InCall(isAppVisible=true), NoTransition ->
            // ReturnRequested].
            factory.onCompose(expandable)
            var controller = factory.createController(forLaunch = false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat(latest!!.transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isTrue()

            // Start the return transition [InCall(isAppVisible=true), ReturnRequested ->
            // Returning].
            controller.onTransitionAnimationStart(isExpandingFullyAbove = false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat(latest!!.transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isFalse()

            // End the return transition [InCall(isAppVisible=true), Returning -> NoTransition].
            controller.onTransitionAnimationEnd(isExpandingFullyAbove = false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat(latest!!.transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isFalse()

            // Settle the return transition [InCall(isAppVisible=true) ->
            // InCall(isAppVisible=false), NoTransition].
            kosmos.activityManagerRepository.fake.setIsAppVisible(NOTIFICATION_UID, false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat(latest!!.transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isFalse()

            // Trigger a launch transition [InCall(isAppVisible=false) -> InCall(isAppVisible=true),
            // NoTransition].
            kosmos.activityManagerRepository.fake.setIsAppVisible(NOTIFICATION_UID, true)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat(latest!!.transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isFalse()

            // Request the return transition [InCall(isAppVisible=true), NoTransition ->
            // LaunchRequested].
            controller = factory.createController(forLaunch = true)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat(latest!!.transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isFalse()

            // Start the return transition [InCall(isAppVisible=true), LaunchRequested ->
            // Launching].
            controller.onTransitionAnimationStart(isExpandingFullyAbove = false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat(latest!!.transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isFalse()

            // End the return transition [InCall(isAppVisible=true), Launching -> NoTransition].
            controller.onTransitionAnimationStart(isExpandingFullyAbove = false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
            assertThat(latest!!.transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!!.transitionManager!!.hideChipForTransition).isFalse()

            // End the call with the app visible [InCall(isAppVisible=true) -> NoCall,
            // NoTransition].
            removeOngoingCallState(key = NOTIFICATION_KEY)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
            assertThat(latest!!.transitionManager!!.controllerFactory).isNull()

            // End the call with the app hidden [InCall(isAppVisible=false) -> NoCall,
            // NoTransition].
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 345,
                contentIntent = pendingIntent,
                isAppVisible = false,
            )
            removeOngoingCallState(key = NOTIFICATION_KEY)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
            assertThat(latest!!.transitionManager!!.controllerFactory).isNull()
        }

    @Test
    @DisableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipLegacy_hasNoTransitionAnimationInformation() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chip)

            // NoCall
            removeOngoingCallState(key = NOTIFICATION_KEY)
            assertThat(latest!!.transitionManager).isNull()

            // InCall with visible app
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 345,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            assertThat(latest!!.transitionManager).isNull()

            // InCall with hidden app
            kosmos.activityManagerRepository.fake.setIsAppVisible(NOTIFICATION_UID, false)
            assertThat(latest!!.transitionManager).isNull()
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    @EnableChipsModernization
    fun chipWithReturnAnimation_chipDataChangesMidTransition() =
        kosmos.runTest {
            val pendingIntent = mock<PendingIntent>()
            val intent = mock<Intent>()
            whenever(pendingIntent.intent).thenReturn(intent)
            val component = mock<ComponentName>()
            whenever(intent.component).thenReturn(component)

            val expandable = mock<Expandable>()
            val activityController = mock<ActivityTransitionAnimator.Controller>()
            whenever(
                    expandable.activityTransitionController(
                        anyOrNull(),
                        anyOrNull(),
                        any(),
                        anyOrNull(),
                        any(),
                    )
                )
                .thenReturn(activityController)

            val latest by collectLastValue(underTest.chip)

            // Start with the app visible and trigger a return animation.
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 345,
                contentIntent = pendingIntent,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            var factory = latest!!.transitionManager!!.controllerFactory!!
            factory.onCompose(expandable)
            var controller = factory.createController(forLaunch = false)
            controller.onTransitionAnimationStart(isExpandingFullyAbove = false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active.Timer::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()

            // The chip changes state.
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 0,
                contentIntent = pendingIntent,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active.IconOnly::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()

            // Reset the state and trigger a launch animation.
            controller.onTransitionAnimationEnd(isExpandingFullyAbove = false)
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 345,
                contentIntent = pendingIntent,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            factory = latest!!.transitionManager!!.controllerFactory!!
            factory.onCompose(expandable)
            controller = factory.createController(forLaunch = true)
            controller.onTransitionAnimationStart(isExpandingFullyAbove = false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active.Timer::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()

            // The chip changes state.
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = -2,
                contentIntent = pendingIntent,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active.IconOnly::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    @EnableChipsModernization
    fun chipWithReturnAnimation_chipDisappearsMidTransition() =
        kosmos.runTest {
            val pendingIntent = mock<PendingIntent>()
            val intent = mock<Intent>()
            whenever(pendingIntent.intent).thenReturn(intent)
            val component = mock<ComponentName>()
            whenever(intent.component).thenReturn(component)

            val expandable = mock<Expandable>()
            val activityController = mock<ActivityTransitionAnimator.Controller>()
            whenever(
                    expandable.activityTransitionController(
                        anyOrNull(),
                        anyOrNull(),
                        any(),
                        anyOrNull(),
                        any(),
                    )
                )
                .thenReturn(activityController)

            val latest by collectLastValue(underTest.chip)

            // Start with the app visible and trigger a return animation.
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 345,
                contentIntent = pendingIntent,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            var factory = latest!!.transitionManager!!.controllerFactory!!
            factory.onCompose(expandable)
            var controller = factory.createController(forLaunch = false)
            controller.onTransitionAnimationStart(isExpandingFullyAbove = false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active.Timer::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()

            // The chip disappears.
            removeOngoingCallState(key = NOTIFICATION_KEY)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)

            // Reset the state and trigger a launch animation.
            controller.onTransitionAnimationEnd(isExpandingFullyAbove = false)
            addOngoingCallState(
                key = NOTIFICATION_KEY,
                startTimeMs = 345,
                contentIntent = pendingIntent,
                uid = NOTIFICATION_UID,
                isAppVisible = true,
            )
            factory = latest!!.transitionManager!!.controllerFactory!!
            factory.onCompose(expandable)
            controller = factory.createController(forLaunch = true)
            controller.onTransitionAnimationStart(isExpandingFullyAbove = false)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active.Timer::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isHidden).isFalse()

            // The chip disappears.
            removeOngoingCallState(key = NOTIFICATION_KEY)
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
        }

    companion object {
        fun createStatusBarIconViewOrNull(): StatusBarIconView? =
            if (StatusBarConnectedDisplays.isEnabled) {
                null
            } else {
                mock<StatusBarIconView>()
            }

        private const val NOTIFICATION_KEY = "testKey"
        private const val NOTIFICATION_UID = 12345
        private const val PROMOTED_BACKGROUND_COLOR = 65
        private const val PROMOTED_PRIMARY_TEXT_COLOR = 98

        @get:Parameters(name = "{0}")
        @JvmStatic
        val flags: List<FlagsParameterization>
            get() = buildList {
                addAll(
                    FlagsParameterization.allCombinationsOf(
                        StatusBarRootModernization.FLAG_NAME,
                        StatusBarChipsModernization.FLAG_NAME,
                        StatusBarChipsReturnAnimations.FLAG_NAME,
                    )
                )
            }
    }
}
