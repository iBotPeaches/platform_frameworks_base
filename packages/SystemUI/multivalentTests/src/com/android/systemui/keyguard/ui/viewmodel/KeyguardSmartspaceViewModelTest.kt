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

package com.android.systemui.keyguard.ui.viewmodel

import android.content.res.Configuration
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.keyguard.data.repository.fakeKeyguardClockRepository
import com.android.systemui.keyguard.data.repository.keyguardClockRepository
import com.android.systemui.keyguard.data.repository.keyguardSmartspaceRepository
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.clocks.ClockController
import com.android.systemui.shade.data.repository.shadeRepository
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class KeyguardSmartspaceViewModelTest : SysuiTestCase() {
    val kosmos = testKosmos()
    val testScope = kosmos.testScope
    val underTest = kosmos.keyguardSmartspaceViewModel
    @Mock private lateinit var mockConfiguration: Configuration

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private lateinit var clockController: ClockController

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        kosmos.fakeKeyguardClockRepository.setCurrentClock(clockController)
    }

    @Test
    fun testWhenWeatherEnabled_notCustomWeatherDataDisplay_isWeatherVisible_shouldBeTrue() =
        testScope.runTest {
            val isWeatherVisible by collectLastValue(underTest.isWeatherVisible)
            whenever(clockController.largeClock.config.hasCustomWeatherDataDisplay)
                .thenReturn(false)

            with(kosmos) {
                keyguardSmartspaceRepository.setIsWeatherEnabled(true)
                keyguardClockRepository.setClockSize(ClockSize.LARGE)
            }

            assertThat(isWeatherVisible).isEqualTo(true)
        }

    @Test
    fun testWhenWeatherEnabled_hasCustomWeatherDataDisplay_isWeatherVisible_shouldBeFalse() =
        testScope.runTest {
            val isWeatherVisible by collectLastValue(underTest.isWeatherVisible)
            whenever(clockController.largeClock.config.hasCustomWeatherDataDisplay).thenReturn(true)

            with(kosmos) {
                keyguardSmartspaceRepository.setIsWeatherEnabled(true)
                keyguardClockRepository.setClockSize(ClockSize.LARGE)
            }

            assertThat(isWeatherVisible).isEqualTo(false)
        }

    @Test
    fun testWhenWeatherEnabled_notCustomWeatherDataDisplay_notIsWeatherVisible_shouldBeFalse() =
        testScope.runTest {
            val isWeatherVisible by collectLastValue(underTest.isWeatherVisible)
            whenever(clockController.largeClock.config.hasCustomWeatherDataDisplay)
                .thenReturn(false)

            with(kosmos) {
                keyguardSmartspaceRepository.setIsWeatherEnabled(false)
                keyguardClockRepository.setClockSize(ClockSize.LARGE)
            }

            assertThat(isWeatherVisible).isEqualTo(false)
        }

    @Test
    fun isShadeLayoutWide_withConfigTrue_true() =
        with(kosmos) {
            testScope.runTest {
                val isShadeLayoutWide by collectLastValue(underTest.isShadeLayoutWide)
                shadeRepository.setShadeLayoutWide(true)

                assertThat(isShadeLayoutWide).isTrue()
            }
        }

    @Test
    fun isShadeLayoutWide_withConfigFalse_false() =
        with(kosmos) {
            testScope.runTest {
                val isShadeLayoutWide by collectLastValue(underTest.isShadeLayoutWide)
                shadeRepository.setShadeLayoutWide(false)

                assertThat(isShadeLayoutWide).isFalse()
            }
        }

    @Test
    @DisableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun dateWeatherBelowSmallClock_smartspacelayoutflag_off_true() {
        val result = KeyguardSmartspaceViewModel.dateWeatherBelowSmallClock(mockConfiguration)

        assertThat(result).isTrue()
    }

    @Test
    @EnableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun dateWeatherBelowSmallClock_defaultFontAndDisplaySize_false() {
        val fontScale = 1.0f
        val screenWidthDp = 347
        mockConfiguration.fontScale = fontScale
        mockConfiguration.screenWidthDp = screenWidthDp

        val result = KeyguardSmartspaceViewModel.dateWeatherBelowSmallClock(mockConfiguration)

        assertThat(result).isFalse()
    }

    @Test
    @EnableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun dateWeatherBelowSmallClock_variousFontAndDisplaySize_false() {
        mockConfiguration.fontScale = 1.0f
        mockConfiguration.screenWidthDp = 347
        val result1 = KeyguardSmartspaceViewModel.dateWeatherBelowSmallClock(mockConfiguration)
        assertThat(result1).isFalse()

        mockConfiguration.fontScale = 1.2f
        mockConfiguration.screenWidthDp = 347
        val result2 = KeyguardSmartspaceViewModel.dateWeatherBelowSmallClock(mockConfiguration)
        assertThat(result2).isFalse()

        mockConfiguration.fontScale = 1.7f
        mockConfiguration.screenWidthDp = 412
        val result3 = KeyguardSmartspaceViewModel.dateWeatherBelowSmallClock(mockConfiguration)
        assertThat(result3).isFalse()
    }

    @Test
    @EnableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun dateWeatherBelowSmallClock_variousFontAndDisplaySize_true() {
        mockConfiguration.fontScale = 1.0f
        mockConfiguration.screenWidthDp = 310
        val result1 = KeyguardSmartspaceViewModel.dateWeatherBelowSmallClock(mockConfiguration)
        assertThat(result1).isTrue()

        mockConfiguration.fontScale = 1.5f
        mockConfiguration.screenWidthDp = 347
        val result2 = KeyguardSmartspaceViewModel.dateWeatherBelowSmallClock(mockConfiguration)
        assertThat(result2).isTrue()

        mockConfiguration.fontScale = 2.0f
        mockConfiguration.screenWidthDp = 411
        val result3 = KeyguardSmartspaceViewModel.dateWeatherBelowSmallClock(mockConfiguration)
        assertThat(result3).isTrue()
    }
}
