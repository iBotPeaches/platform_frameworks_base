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

package com.android.systemui.inputdevice.tutorial.domain.interactor

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.StatusBarNotification
import androidx.annotation.StringRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.inputdevice.tutorial.data.repository.TutorialSchedulerRepository
import com.android.systemui.inputdevice.tutorial.inputDeviceTutorialLogger
import com.android.systemui.inputdevice.tutorial.ui.TutorialNotificationCoordinator
import com.android.systemui.keyboard.data.repository.FakeKeyboardRepository
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.res.R
import com.android.systemui.settings.userTracker
import com.android.systemui.statusbar.commandline.commandRegistry
import com.android.systemui.testKosmos
import com.android.systemui.touchpad.data.repository.FakeTouchpadRepository
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.secondValue
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class TutorialNotificationCoordinatorTest : SysuiTestCase() {

    private lateinit var underTest: TutorialNotificationCoordinator
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope
    private val keyboardRepository = FakeKeyboardRepository()
    private val touchpadRepository = FakeTouchpadRepository()
    private lateinit var repository: TutorialSchedulerRepository
    @Mock private lateinit var notificationManager: NotificationManager
    @Mock private lateinit var notification: StatusBarNotification
    @Captor private lateinit var notificationCaptor: ArgumentCaptor<Notification>
    @get:Rule val rule = MockitoJUnit.rule()

    @Before
    fun setup() {
        repository =
            TutorialSchedulerRepository(
                context,
                kosmos.backgroundScope,
                dataStoreName = "TutorialNotificationCoordinatorTest",
            )
        val interactor =
            TutorialSchedulerInteractor(
                keyboardRepository,
                touchpadRepository,
                repository,
                kosmos.inputDeviceTutorialLogger,
                kosmos.commandRegistry,
                testScope.backgroundScope,
            )
        underTest =
            TutorialNotificationCoordinator(
                testScope.backgroundScope,
                context,
                interactor,
                notificationManager,
                kosmos.userTracker,
            )
        notificationCaptor = ArgumentCaptor.forClass(Notification::class.java)
        underTest.start()
    }

    @Test
    fun showKeyboardNotification() = runTestAndClear {
        keyboardRepository.setIsAnyKeyboardConnected(true)
        testScope.advanceTimeBy(LAUNCH_DELAY)
        verifyNotification(
            R.string.launch_keyboard_tutorial_notification_title,
            R.string.launch_keyboard_tutorial_notification_content,
        )
    }

    @Test
    fun showTouchpadNotification() = runTestAndClear {
        touchpadRepository.setIsAnyTouchpadConnected(true)
        testScope.advanceTimeBy(LAUNCH_DELAY)
        verifyNotification(
            R.string.launch_touchpad_tutorial_notification_title,
            R.string.launch_touchpad_tutorial_notification_content,
        )
    }

    @Test
    fun showKeyboardTouchpadNotification() = runTestAndClear {
        keyboardRepository.setIsAnyKeyboardConnected(true)
        touchpadRepository.setIsAnyTouchpadConnected(true)
        testScope.advanceTimeBy(LAUNCH_DELAY)
        verifyNotification(
            R.string.launch_keyboard_touchpad_tutorial_notification_title,
            R.string.launch_keyboard_touchpad_tutorial_notification_content,
        )
    }

    @Test
    fun doNotShowNotification() = runTestAndClear {
        testScope.advanceTimeBy(LAUNCH_DELAY)
        verify(notificationManager, never())
            .notifyAsUser(eq(TAG), eq(NOTIFICATION_ID), any(), any())
    }

    @Test
    fun cancelKeyboardNotificationWhenKeyboardDisconnects() = runTestAndClear {
        keyboardRepository.setIsAnyKeyboardConnected(true)
        testScope.advanceTimeBy(LAUNCH_DELAY)
        mockNotifications(hasTutorialNotification = true)

        // After the keyboard is disconnected, i.e. there is nothing connected, the notification
        // should be cancelled
        keyboardRepository.setIsAnyKeyboardConnected(false)
        verify(notificationManager).cancelAsUser(eq(TAG), eq(NOTIFICATION_ID), any())
    }

    @Test
    fun updateNotificationToTouchpadOnlyWhenKeyboardDisconnects() = runTestAndClear {
        keyboardRepository.setIsAnyKeyboardConnected(true)
        touchpadRepository.setIsAnyTouchpadConnected(true)
        testScope.advanceTimeBy(LAUNCH_DELAY)
        mockNotifications(hasTutorialNotification = true)

        keyboardRepository.setIsAnyKeyboardConnected(false)

        verify(notificationManager, times(2))
            .notifyAsUser(eq(TAG), eq(NOTIFICATION_ID), notificationCaptor.capture(), any())
        // Connect both device and the first notification is for both. After the keyboard is
        // disconnected, i.e. with only the touchpad left, the notification should be update to
        // touchpad only
        notificationCaptor.secondValue.verify(
            R.string.launch_touchpad_tutorial_notification_title,
            R.string.launch_touchpad_tutorial_notification_content,
        )
    }

    @Test
    fun updateNotificationToBothDevicesWhenTouchpadConnects() = runTestAndClear {
        keyboardRepository.setIsAnyKeyboardConnected(true)
        testScope.advanceTimeBy(LAUNCH_DELAY)
        mockNotifications(hasTutorialNotification = true)

        touchpadRepository.setIsAnyTouchpadConnected(true)

        verify(notificationManager, times(2))
            .notifyAsUser(eq(TAG), eq(NOTIFICATION_ID), notificationCaptor.capture(), any())
        // Update the notification from keyboard to both devices
        notificationCaptor.secondValue.verify(
            R.string.launch_keyboard_touchpad_tutorial_notification_title,
            R.string.launch_keyboard_touchpad_tutorial_notification_content,
        )
    }

    @Test
    fun doNotShowUpdateNotificationWhenInitialNotificationIsDismissed() = runTestAndClear {
        keyboardRepository.setIsAnyKeyboardConnected(true)
        testScope.advanceTimeBy(LAUNCH_DELAY)
        mockNotifications(hasTutorialNotification = false)

        touchpadRepository.setIsAnyTouchpadConnected(true)

        // There's only one notification being shown throughout this scenario. We don't update the
        // notification because it has been dismissed when the touchpad connects
        verifyNotification(
            R.string.launch_keyboard_tutorial_notification_title,
            R.string.launch_keyboard_tutorial_notification_content,
        )
    }

    @Test
    fun showTouchpadNotificationAfterDelayAndKeyboardNotificationIsDismissed() = runTestAndClear {
        keyboardRepository.setIsAnyKeyboardConnected(true)
        testScope.advanceTimeBy(LAUNCH_DELAY)
        mockNotifications(hasTutorialNotification = false)

        touchpadRepository.setIsAnyTouchpadConnected(true)
        testScope.advanceTimeBy(LAUNCH_DELAY)

        verify(notificationManager, times(2))
            .notifyAsUser(eq(TAG), eq(NOTIFICATION_ID), notificationCaptor.capture(), any())
        // The keyboard notification was shown and dismissed; the touchpad notification is scheduled
        // independently
        notificationCaptor.secondValue.verify(
            R.string.launch_touchpad_tutorial_notification_title,
            R.string.launch_touchpad_tutorial_notification_content,
        )
    }

    private fun runTestAndClear(block: suspend () -> Unit) =
        testScope.runTest {
            try {
                block()
            } finally {
                repository.clear()
            }
        }

    // Mock an active notification, so when the updater checks activeNotifications, it returns one
    // with the given id. Otherwise, return an empty array (i.e. no active notifications)
    private fun mockNotifications(hasTutorialNotification: Boolean) {
        whenever(notification.id).thenReturn(NOTIFICATION_ID)
        val notifications = if (hasTutorialNotification) arrayOf(notification) else emptyArray()
        whenever(notificationManager.activeNotifications).thenReturn(notifications)
    }

    private fun verifyNotification(@StringRes titleResId: Int, @StringRes contentResId: Int) {
        verify(notificationManager)
            .notifyAsUser(eq(TAG), eq(NOTIFICATION_ID), notificationCaptor.capture(), any())
        notificationCaptor.value.verify(titleResId, contentResId)
    }

    private fun Notification.verify(@StringRes titleResId: Int, @StringRes contentResId: Int) {
        val actualTitle = getString(Notification.EXTRA_TITLE)
        val actualContent = getString(Notification.EXTRA_TEXT)
        assertThat(actualTitle).isEqualTo(context.getString(titleResId))
        assertThat(actualContent).isEqualTo(context.getString(contentResId))
    }

    private fun Notification.getString(key: String): String =
        this.extras?.getCharSequence(key).toString()

    companion object {
        private const val TAG = "TutorialSchedulerInteractor"
        private const val NOTIFICATION_ID = 5566
        private val LAUNCH_DELAY = 72.hours
    }
}
