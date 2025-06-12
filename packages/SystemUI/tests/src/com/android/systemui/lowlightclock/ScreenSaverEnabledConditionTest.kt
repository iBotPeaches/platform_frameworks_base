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
package com.android.systemui.lowlightclock

import android.content.res.Resources
import android.database.ContentObserver
import android.os.UserHandle
import android.provider.Settings
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.systemui.SysuiTestCase
import com.android.systemui.condition.testStart
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.android.systemui.util.settings.SecureSettings
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidTestingRunner::class)
class ScreenSaverEnabledConditionTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    @Mock private lateinit var resources: Resources

    @Mock private lateinit var secureSettings: SecureSettings

    private val settingsObserverCaptor = argumentCaptor<ContentObserver>()
    private lateinit var condition: ScreenSaverEnabledCondition

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        // Default dreams to enabled by default
        whenever(resources.getBoolean(R.bool.config_dreamsEnabledByDefault)).thenReturn(true)

        condition = ScreenSaverEnabledCondition(kosmos.testScope, resources, secureSettings)
    }

    @Test
    fun testScreenSaverInitiallyEnabled() =
        kosmos.runTest {
            setScreenSaverEnabled(true)
            testStart(condition)
            Truth.assertThat(condition.isConditionMet).isTrue()
        }

    @Test
    fun testScreenSaverInitiallyDisabled() =
        kosmos.runTest {
            setScreenSaverEnabled(false)
            testStart(condition)
            Truth.assertThat(condition.isConditionMet).isFalse()
        }

    @Test
    fun testScreenSaverStateChanges() =
        kosmos.runTest {
            setScreenSaverEnabled(false)
            testStart(condition)
            Truth.assertThat(condition.isConditionMet).isFalse()

            setScreenSaverEnabled(true)
            runCurrent()
            val observer = captureSettingsObserver()
            observer.onChange(/* selfChange= */ false)
            Truth.assertThat(condition.isConditionMet).isTrue()
        }

    private fun setScreenSaverEnabled(enabled: Boolean) {
        whenever(
                secureSettings.getIntForUser(
                    eq(Settings.Secure.SCREENSAVER_ENABLED),
                    any(),
                    eq(UserHandle.USER_CURRENT),
                )
            )
            .thenReturn(if (enabled) 1 else 0)
    }

    private fun captureSettingsObserver(): ContentObserver {
        Mockito.verify(secureSettings)
            .registerContentObserverForUserSync(
                eq(Settings.Secure.SCREENSAVER_ENABLED),
                settingsObserverCaptor.capture(),
                eq(UserHandle.USER_CURRENT),
            )
        return settingsObserverCaptor.lastValue
    }
}
