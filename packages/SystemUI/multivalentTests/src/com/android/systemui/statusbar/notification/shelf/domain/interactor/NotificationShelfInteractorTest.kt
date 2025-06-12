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

package com.android.systemui.statusbar.notification.shelf.domain.interactor

import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFaceAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.kosmos.testCase
import com.android.systemui.plugins.statusbar.statusBarStateController
import com.android.systemui.power.data.repository.fakePowerRepository
import com.android.systemui.statusbar.lockscreenShadeTransitionController
import com.android.systemui.statusbar.phone.screenOffAnimationController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.isNull
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@SmallTest
class NotificationShelfInteractorTest : SysuiTestCase() {

    private val kosmos =
        testKosmos().apply {
            testCase = this@NotificationShelfInteractorTest
            lockscreenShadeTransitionController = mock()
            screenOffAnimationController = mock()
            statusBarStateController = mock()
            whenever(screenOffAnimationController.allowWakeUpIfDozing()).thenReturn(true)
        }
    private val underTest = kosmos.notificationShelfInteractor

    private val keyguardRepository = kosmos.fakeKeyguardRepository
    private val deviceEntryFaceAuthRepository = kosmos.fakeDeviceEntryFaceAuthRepository

    private val statusBarStateController = kosmos.statusBarStateController
    private val powerRepository = kosmos.fakePowerRepository
    private val keyguardTransitionController = kosmos.lockscreenShadeTransitionController

    @Test
    fun shelfIsNotStatic_whenKeyguardNotShowing() = runTest {
        val shelfStatic by collectLastValue(underTest.isShelfStatic)

        keyguardRepository.setKeyguardShowing(false)

        assertThat(shelfStatic).isFalse()
    }

    @Test
    fun shelfIsNotStatic_whenKeyguardShowingAndNotBypass() = runTest {
        val shelfStatic by collectLastValue(underTest.isShelfStatic)

        keyguardRepository.setKeyguardShowing(true)
        deviceEntryFaceAuthRepository.isBypassEnabled.value = false

        assertThat(shelfStatic).isFalse()
    }

    @Test
    fun shelfIsStatic_whenBypass() = runTest {
        val shelfStatic by collectLastValue(underTest.isShelfStatic)

        keyguardRepository.setKeyguardShowing(true)
        deviceEntryFaceAuthRepository.isBypassEnabled.value = true

        assertThat(shelfStatic).isTrue()
    }

    @Test
    fun shelfOnKeyguard_whenKeyguardShowing() = runTest {
        val onKeyguard by collectLastValue(underTest.isShowingOnKeyguard)

        keyguardRepository.setKeyguardShowing(true)

        assertThat(onKeyguard).isTrue()
    }

    @Test
    fun shelfNotOnKeyguard_whenKeyguardNotShowing() = runTest {
        val onKeyguard by collectLastValue(underTest.isShowingOnKeyguard)

        keyguardRepository.setKeyguardShowing(false)

        assertThat(onKeyguard).isFalse()
    }

    @Test
    fun goToLockedShadeFromShelf_wakesUpFromDoze() {
        whenever(statusBarStateController.isDozing).thenReturn(true)

        underTest.goToLockedShadeFromShelf()

        assertThat(powerRepository.lastWakeReason).isNotNull()
        assertThat(powerRepository.lastWakeReason).isEqualTo(PowerManager.WAKE_REASON_GESTURE)
    }

    @Test
    fun goToLockedShadeFromShelf_invokesKeyguardTransitionController() {
        underTest.goToLockedShadeFromShelf()

        verify(keyguardTransitionController).goToLockedShade(isNull(), eq(true))
    }
}
