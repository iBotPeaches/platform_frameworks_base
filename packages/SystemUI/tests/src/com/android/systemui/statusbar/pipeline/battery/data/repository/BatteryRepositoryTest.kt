/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.battery.data.repository

import android.content.testableContext
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.shared.settings.data.repository.fakeSystemSettingsRepository
import com.android.systemui.shared.settings.data.repository.systemSettingsRepository
import com.android.systemui.statusbar.policy.batteryController
import com.android.systemui.statusbar.policy.fake
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class BatteryRepositoryTest : SysuiTestCase() {
    val kosmos = testKosmos()

    val Kosmos.underTest by
        Kosmos.Fixture {
            BatteryRepository(
                testableContext,
                backgroundScope,
                testDispatcher,
                batteryController,
                systemSettingsRepository,
            )
        }

    @Test
    fun pluggedIn() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isPluggedIn)

            assertThat(latest).isFalse()

            batteryController.fake._isPluggedIn = true

            assertThat(latest).isTrue()
        }

    @Test
    fun powerSave() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isPowerSaveEnabled)

            assertThat(latest).isFalse()

            batteryController.fake._isPowerSave = true

            assertThat(latest).isTrue()
        }

    @Test
    fun defend() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isBatteryDefenderEnabled)

            assertThat(latest).isFalse()

            batteryController.fake._isDefender = true

            assertThat(latest).isTrue()
        }

    @Test
    fun level() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.level)

            batteryController.fake._level = 42

            assertThat(latest).isEqualTo(42)

            batteryController.fake._level = 84

            assertThat(latest).isEqualTo(84)
        }

    @Test
    fun isStateUnknown() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isStateUnknown)

            assertThat(latest).isFalse()

            batteryController.fake._isStateUnknown = true

            assertThat(latest).isTrue()
        }

    @Test
    fun showBatteryPercentSetting() =
        kosmos.runTest {
            // Set the default to true, so it's detectable in test
            testableContext.orCreateTestableResources.addOverride(
                com.android.internal.R.bool.config_defaultBatteryPercentageSetting,
                true,
            )

            val latest by collectLastValue(underTest.isShowBatteryPercentSettingEnabled)

            assertThat(latest).isTrue()

            fakeSystemSettingsRepository.setInt(Settings.System.SHOW_BATTERY_PERCENT, 0)

            assertThat(latest).isFalse()

            fakeSystemSettingsRepository.setInt(Settings.System.SHOW_BATTERY_PERCENT, 1)

            assertThat(latest).isTrue()
        }

    @Test
    fun batteryRemainingEstimateString_queriesEveryTwoMinutes() =
        kosmos.runTest {
            batteryController.fake._estimatedTimeRemainingString = null

            val latest by collectLastValue(underTest.batteryTimeRemainingEstimate)

            assertThat(latest).isNull()

            batteryController.fake._estimatedTimeRemainingString = "test time remaining"

            testScope.advanceTimeBy(2.minutes)

            assertThat(latest).isEqualTo("test time remaining")
        }
}
