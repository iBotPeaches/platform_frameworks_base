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

package com.android.systemui.statusbar.phone.ongoingcall.domain.interactor

import android.app.PendingIntent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.activity.data.repository.activityManagerRepository
import com.android.systemui.activity.data.repository.fake
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.StatusBarIconView
import com.android.systemui.statusbar.data.repository.fakeStatusBarModeRepository
import com.android.systemui.statusbar.gesture.swipeStatusBarAwayGestureHandler
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentBuilder
import com.android.systemui.statusbar.phone.ongoingcall.EnableChipsModernization
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallModel
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper.addOngoingCallState
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper.removeOngoingCallState
import com.android.systemui.statusbar.window.fakeStatusBarWindowControllerStore
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableChipsModernization
class OngoingCallInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val underTest = kosmos.ongoingCallInteractor

    @Before
    fun setUp() {
        underTest.start()
    }

    @Test
    fun noNotification_emitsNoCall() = runTest {
        val state by collectLastValue(underTest.ongoingCallState)
        assertThat(state).isInstanceOf(OngoingCallModel.NoCall::class.java)
    }

    @Test
    fun ongoingCallNotification_setsAllFields_withAppHidden() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.ongoingCallState)

            // Set up notification with icon view and intent
            val key = "promotedCall"
            val startTimeMs = 1000L
            val testIconView: StatusBarIconView = mock()
            val testIntent: PendingIntent = mock()
            val testPromotedContent = PromotedNotificationContentBuilder(key).build()
            addOngoingCallState(
                key = key,
                startTimeMs = startTimeMs,
                statusBarChipIconView = testIconView,
                contentIntent = testIntent,
                promotedContent = testPromotedContent,
                isAppVisible = false,
            )

            // Verify model is InCall and has the correct icon, intent, and promoted content.
            assertThat(latest).isInstanceOf(OngoingCallModel.InCall::class.java)
            val model = latest as OngoingCallModel.InCall
            assertThat(model.startTimeMs).isEqualTo(startTimeMs)
            assertThat(model.notificationIconView).isSameInstanceAs(testIconView)
            assertThat(model.intent).isSameInstanceAs(testIntent)
            assertThat(model.notificationKey).isEqualTo(key)
            assertThat(model.promotedContent).isSameInstanceAs(testPromotedContent)
            assertThat(model.isAppVisible).isFalse()
        }

    @Test
    fun ongoingCallNotification_setsAllFields_withAppVisible() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.ongoingCallState)

            // Set up notification with icon view and intent
            val key = "promotedCall"
            val startTimeMs = 1000L
            val testIconView: StatusBarIconView = mock()
            val testIntent: PendingIntent = mock()
            val testPromotedContent = PromotedNotificationContentBuilder(key).build()
            addOngoingCallState(
                key = key,
                startTimeMs = startTimeMs,
                statusBarChipIconView = testIconView,
                contentIntent = testIntent,
                promotedContent = testPromotedContent,
                isAppVisible = true,
            )

            // Verify model is InCall with visible app and has the correct icon, intent, and
            // promoted content.
            assertThat(latest).isInstanceOf(OngoingCallModel.InCall::class.java)
            val model = latest as OngoingCallModel.InCall
            assertThat(model.startTimeMs).isEqualTo(startTimeMs)
            assertThat(model.notificationIconView).isSameInstanceAs(testIconView)
            assertThat(model.intent).isSameInstanceAs(testIntent)
            assertThat(model.notificationKey).isEqualTo(key)
            assertThat(model.promotedContent).isSameInstanceAs(testPromotedContent)
            assertThat(model.isAppVisible).isTrue()
        }

    @Test
    fun notificationRemoved_emitsNoCall() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.ongoingCallState)

            addOngoingCallState(key = "testKey")
            removeOngoingCallState(key = "testKey")

            assertThat(latest).isInstanceOf(OngoingCallModel.NoCall::class.java)
        }

    @Test
    fun ongoingCallNotification_appVisibleInitially_emitsInCallWithVisibleApp() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.ongoingCallState)

            addOngoingCallState(uid = UID, isAppVisible = true)

            assertThat(latest).isInstanceOf(OngoingCallModel.InCall::class.java)
            assertThat((latest as OngoingCallModel.InCall).isAppVisible).isTrue()
        }

    @Test
    fun ongoingCallNotification_appNotVisibleInitially_emitsInCall() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.ongoingCallState)

            addOngoingCallState(uid = UID, isAppVisible = false)

            assertThat(latest).isInstanceOf(OngoingCallModel.InCall::class.java)
            assertThat((latest as OngoingCallModel.InCall).isAppVisible).isFalse()
        }

    @Test
    fun ongoingCallNotification_visibilityChanges_updatesState() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.ongoingCallState)

            // Start with notification and app not visible
            addOngoingCallState(uid = UID, isAppVisible = false)
            assertThat(latest).isInstanceOf(OngoingCallModel.InCall::class.java)
            assertThat((latest as OngoingCallModel.InCall).isAppVisible).isFalse()

            // App becomes visible
            kosmos.activityManagerRepository.fake.setIsAppVisible(UID, true)
            assertThat(latest).isInstanceOf(OngoingCallModel.InCall::class.java)
            assertThat((latest as OngoingCallModel.InCall).isAppVisible).isTrue()

            // App becomes invisible again
            kosmos.activityManagerRepository.fake.setIsAppVisible(UID, false)
            assertThat(latest).isInstanceOf(OngoingCallModel.InCall::class.java)
            assertThat((latest as OngoingCallModel.InCall).isAppVisible).isFalse()
        }

    @Test
    fun ongoingCallNotification_setsRequiresStatusBarVisibleTrue() =
        kosmos.runTest {
            val isStatusBarRequired by collectLastValue(underTest.isStatusBarRequiredForOngoingCall)
            val requiresStatusBarVisibleInRepository by
                collectLastValue(
                    kosmos.fakeStatusBarModeRepository.defaultDisplay
                        .ongoingProcessRequiresStatusBarVisible
                )
            val requiresStatusBarVisibleInWindowController by
                collectLastValue(
                    kosmos.fakeStatusBarWindowControllerStore.defaultDisplay
                        .ongoingProcessRequiresStatusBarVisible
                )
            addOngoingCallState()

            assertThat(isStatusBarRequired).isTrue()
            assertThat(requiresStatusBarVisibleInRepository).isTrue()
            assertThat(requiresStatusBarVisibleInWindowController).isTrue()
        }

    @Test
    fun notificationRemoved_setsRequiresStatusBarVisibleFalse() =
        kosmos.runTest {
            val isStatusBarRequired by collectLastValue(underTest.isStatusBarRequiredForOngoingCall)
            val requiresStatusBarVisibleInRepository by
                collectLastValue(
                    kosmos.fakeStatusBarModeRepository.defaultDisplay
                        .ongoingProcessRequiresStatusBarVisible
                )
            val requiresStatusBarVisibleInWindowController by
                collectLastValue(
                    kosmos.fakeStatusBarWindowControllerStore.defaultDisplay
                        .ongoingProcessRequiresStatusBarVisible
                )

            addOngoingCallState(key = "testKey")

            removeOngoingCallState(key = "testKey")

            assertThat(isStatusBarRequired).isFalse()
            assertThat(requiresStatusBarVisibleInRepository).isFalse()
            assertThat(requiresStatusBarVisibleInWindowController).isFalse()
        }

    @Test
    fun ongoingCallNotification_appBecomesVisible_setsRequiresStatusBarVisibleFalse() =
        kosmos.runTest {
            val ongoingCallState by collectLastValue(underTest.ongoingCallState)

            val requiresStatusBarVisibleInRepository by
                collectLastValue(
                    kosmos.fakeStatusBarModeRepository.defaultDisplay
                        .ongoingProcessRequiresStatusBarVisible
                )
            val requiresStatusBarVisibleInWindowController by
                collectLastValue(
                    kosmos.fakeStatusBarWindowControllerStore.defaultDisplay
                        .ongoingProcessRequiresStatusBarVisible
                )

            addOngoingCallState(uid = UID, isAppVisible = false)

            assertThat(ongoingCallState).isInstanceOf(OngoingCallModel.InCall::class.java)
            assertThat((ongoingCallState as OngoingCallModel.InCall).isAppVisible).isFalse()
            assertThat(requiresStatusBarVisibleInRepository).isTrue()
            assertThat(requiresStatusBarVisibleInWindowController).isTrue()

            kosmos.activityManagerRepository.fake.setIsAppVisible(UID, true)

            assertThat(ongoingCallState).isInstanceOf(OngoingCallModel.InCall::class.java)
            assertThat((ongoingCallState as OngoingCallModel.InCall).isAppVisible).isTrue()
            assertThat(requiresStatusBarVisibleInRepository).isFalse()
            assertThat(requiresStatusBarVisibleInWindowController).isFalse()
        }

    @Test
    fun gestureHandler_inCall_notFullscreen_doesNotListen() =
        kosmos.runTest {
            val ongoingCallState by collectLastValue(underTest.ongoingCallState)

            clearInvocations(kosmos.swipeStatusBarAwayGestureHandler)
            // Set up notification but not in fullscreen
            kosmos.fakeStatusBarModeRepository.defaultDisplay.isInFullscreenMode.value = false
            addOngoingCallState()

            assertThat(ongoingCallState).isInstanceOf(OngoingCallModel.InCall::class.java)
            assertThat((ongoingCallState as OngoingCallModel.InCall).isAppVisible).isFalse()
            verify(kosmos.swipeStatusBarAwayGestureHandler, never())
                .addOnGestureDetectedCallback(any(), any())
        }

    @Test
    fun gestureHandler_inCall_fullscreen_addsListener() =
        kosmos.runTest {
            val isGestureListeningEnabled by collectLastValue(underTest.isGestureListeningEnabled)

            // Set up notification and fullscreen mode
            kosmos.fakeStatusBarModeRepository.defaultDisplay.isInFullscreenMode.value = true
            addOngoingCallState()

            assertThat(isGestureListeningEnabled).isTrue()
            verify(kosmos.swipeStatusBarAwayGestureHandler)
                .addOnGestureDetectedCallback(any(), any())
        }

    @Test
    fun gestureHandler_inCall_fullscreen_chipSwiped_removesListener() =
        kosmos.runTest {
            val swipeAwayState by collectLastValue(underTest.isChipSwipedAway)

            // Set up notification and fullscreen mode
            kosmos.fakeStatusBarModeRepository.defaultDisplay.isInFullscreenMode.value = true
            addOngoingCallState()

            clearInvocations(kosmos.swipeStatusBarAwayGestureHandler)

            underTest.onStatusBarSwiped()

            assertThat(swipeAwayState).isTrue()
            verify(kosmos.swipeStatusBarAwayGestureHandler).removeOnGestureDetectedCallback(any())
        }

    @Test
    fun chipSwipedAway_setsRequiresStatusBarVisibleFalse() =
        kosmos.runTest {
            val isStatusBarRequiredForOngoingCall by
                collectLastValue(underTest.isStatusBarRequiredForOngoingCall)
            val requiresStatusBarVisibleInRepository by
                collectLastValue(
                    kosmos.fakeStatusBarModeRepository.defaultDisplay
                        .ongoingProcessRequiresStatusBarVisible
                )
            val requiresStatusBarVisibleInWindowController by
                collectLastValue(
                    kosmos.fakeStatusBarWindowControllerStore.defaultDisplay
                        .ongoingProcessRequiresStatusBarVisible
                )

            // Start with an ongoing call (which should set status bar required)
            addOngoingCallState()

            assertThat(isStatusBarRequiredForOngoingCall).isTrue()
            assertThat(requiresStatusBarVisibleInRepository).isTrue()
            assertThat(requiresStatusBarVisibleInWindowController).isTrue()

            // Swipe away the chip
            underTest.onStatusBarSwiped()

            // Verify status bar is no longer required
            assertThat(requiresStatusBarVisibleInRepository).isFalse()
            assertThat(requiresStatusBarVisibleInWindowController).isFalse()
        }

    companion object {
        private const val UID = 885
    }
}
