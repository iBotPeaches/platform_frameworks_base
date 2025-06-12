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

package com.android.systemui.statusbar.notification.shelf.ui.viewmodel

import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFaceAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testCase
import com.android.systemui.plugins.statusbar.statusBarStateController
import com.android.systemui.power.data.repository.fakePowerRepository
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.statusbar.lockscreenShadeTransitionController
import com.android.systemui.statusbar.phone.screenOffAnimationController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@SmallTest
class NotificationShelfViewModelTest : SysuiTestCase() {

    private val kosmos =
        testKosmos().apply {
            testCase = this@NotificationShelfViewModelTest
            lockscreenShadeTransitionController = mock()
            screenOffAnimationController = mock()
            statusBarStateController = mock()
            whenever(screenOffAnimationController.allowWakeUpIfDozing()).thenReturn(true)
        }
    private val deviceEntryFaceAuthRepository = kosmos.fakeDeviceEntryFaceAuthRepository
    private val keyguardRepository = kosmos.fakeKeyguardRepository
    private val powerRepository = kosmos.fakePowerRepository
    private val keyguardTransitionController by lazy { kosmos.lockscreenShadeTransitionController }

    private val underTest by lazy { kosmos.notificationShelfViewModel }

    @Test
    fun canModifyColorOfNotifications_whenKeyguardNotShowing() =
        kosmos.runTest {
            val canModifyNotifColor by collectLastValue(underTest.canModifyColorOfNotifications)

            keyguardRepository.setKeyguardShowing(false)

            assertThat(canModifyNotifColor).isTrue()
        }

    @Test
    fun canModifyColorOfNotifications_whenKeyguardShowingAndNotBypass() =
        kosmos.runTest {
            val canModifyNotifColor by collectLastValue(underTest.canModifyColorOfNotifications)

            keyguardRepository.setKeyguardShowing(true)
            deviceEntryFaceAuthRepository.isBypassEnabled.value = false

            assertThat(canModifyNotifColor).isTrue()
        }

    @Test
    fun cannotModifyColorOfNotifications_whenBypass() =
        kosmos.runTest {
            val canModifyNotifColor by collectLastValue(underTest.canModifyColorOfNotifications)

            keyguardRepository.setKeyguardShowing(true)
            deviceEntryFaceAuthRepository.isBypassEnabled.value = true

            assertThat(canModifyNotifColor).isFalse()
        }

    @Test
    fun isClickable_whenKeyguardShowing() =
        kosmos.runTest {
            val isClickable by collectLastValue(underTest.isClickable)

            keyguardRepository.setKeyguardShowing(true)

            assertThat(isClickable).isTrue()
        }

    @Test
    fun isNotClickable_whenKeyguardNotShowing() =
        kosmos.runTest {
            val isClickable by collectLastValue(underTest.isClickable)

            keyguardRepository.setKeyguardShowing(false)

            assertThat(isClickable).isFalse()
        }

    @Test
    fun onClicked_goesToLockedShade() =
        kosmos.runTest {
            whenever(statusBarStateController.isDozing).thenReturn(true)

            underTest.onShelfClicked()

            assertThat(powerRepository.lastWakeReason).isNotNull()
            assertThat(powerRepository.lastWakeReason).isEqualTo(PowerManager.WAKE_REASON_GESTURE)
            verify(keyguardTransitionController).goToLockedShade(Mockito.isNull(), eq(true))
        }

    @Test
    @EnableSceneContainer
    fun isAlignedToEnd_splitShade_true() =
        kosmos.runTest {
            val isShelfAlignedToEnd by collectLastValue(underTest.isAlignedToEnd)

            kosmos.enableSplitShade()

            assertThat(isShelfAlignedToEnd).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun isAlignedToEnd_singleShade_false() =
        kosmos.runTest {
            val isShelfAlignedToEnd by collectLastValue(underTest.isAlignedToEnd)

            kosmos.enableSingleShade()

            assertThat(isShelfAlignedToEnd).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isAlignedToEnd_dualShade_wideScreen_false() =
        kosmos.runTest {
            val isShelfAlignedToEnd by collectLastValue(underTest.isAlignedToEnd)

            kosmos.enableDualShade(wideLayout = true)

            assertThat(isShelfAlignedToEnd).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isAlignedToEnd_dualShade_narrowScreen_false() =
        kosmos.runTest {
            val isShelfAlignedToEnd by collectLastValue(underTest.isAlignedToEnd)

            kosmos.enableDualShade(wideLayout = false)

            assertThat(isShelfAlignedToEnd).isFalse()
        }
}
