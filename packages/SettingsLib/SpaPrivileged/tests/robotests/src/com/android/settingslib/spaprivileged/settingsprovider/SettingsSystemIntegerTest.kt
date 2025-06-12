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
package com.android.settingslib.spaprivileged.settingsprovider

import android.content.Context
import android.provider.Settings

import androidx.test.core.app.ApplicationProvider

import com.android.settingslib.spa.testutils.firstWithTimeoutOrNull
import com.android.settingslib.spa.testutils.toListWithTimeout
import com.google.common.truth.Truth.assertThat

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsSystemIntegerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Settings.System.putString(context.contentResolver, TEST_NAME, null)
    }

    @Test
    fun setIntValue_returnSameValueByDelegate() {
        val settingValue = 250

        Settings.System.putInt(context.contentResolver, TEST_NAME, settingValue)

        val value by context.settingsSystemInteger(TEST_NAME, TEST_SETTING_DEFAULT_VALUE)

        assertThat(value).isEqualTo(settingValue)
    }

    @Test
    fun setZero_returnZeroByDelegate() {
        val settingValue = 0
        Settings.System.putInt(context.contentResolver, TEST_NAME, settingValue)

        val value by context.settingsSystemInteger(TEST_NAME, TEST_SETTING_DEFAULT_VALUE)

        assertThat(value).isEqualTo(settingValue)
    }

    @Test
    fun setValueByDelegate_getValueFromSettings() {
        val settingsValue = 5
        var value by context.settingsSystemInteger(TEST_NAME, TEST_SETTING_DEFAULT_VALUE)

        value = settingsValue

        assertThat(Settings.System.getInt(context.contentResolver, TEST_NAME, TEST_SETTING_DEFAULT_VALUE)).isEqualTo(settingsValue)
    }

    @Test
    fun setZeroByDelegate_getZeroFromSettings() {
        val settingValue = 0
        var value by context.settingsSystemInteger(TEST_NAME, TEST_SETTING_DEFAULT_VALUE)

        value = settingValue

        assertThat(Settings.System.getInt(context.contentResolver, TEST_NAME, TEST_SETTING_DEFAULT_VALUE)).isEqualTo(settingValue)
    }

    @Test
    fun setValueByDelegate_returnValueFromsettingsSystemIntegerFlow() = runBlocking<Unit> {
        val settingValue = 7
        var value by context.settingsSystemInteger(TEST_NAME, TEST_SETTING_DEFAULT_VALUE)
        value = settingValue

        val flow = context.settingsSystemIntegerFlow(TEST_NAME, TEST_SETTING_DEFAULT_VALUE)

        assertThat(flow.firstWithTimeoutOrNull()).isEqualTo(settingValue)
    }

    @Test
    fun setValueByDelegateTwice_collectAfterValueChanged_onlyKeepLatest() = runBlocking<Unit> {
        val firstSettingValue = 5
        val secondSettingValue = 10

        var value by context.settingsSystemInteger(TEST_NAME, TEST_SETTING_DEFAULT_VALUE)
        value = firstSettingValue

        val flow = context.settingsSystemIntegerFlow(TEST_NAME, TEST_SETTING_DEFAULT_VALUE)
        value = secondSettingValue

        assertThat(flow.firstWithTimeoutOrNull()).isEqualTo(value)
    }

    @Test
    fun settingsSystemIntegerFlow_collectBeforeValueChanged_getBoth() = runBlocking<Unit> {
        val firstSettingValue = 12
        val secondSettingValue = 17
        val delay_ms = 100L

        var value by context.settingsSystemInteger(TEST_NAME, TEST_SETTING_DEFAULT_VALUE)
        value = firstSettingValue


        val listDeferred = async {
            context.settingsSystemIntegerFlow(TEST_NAME, TEST_SETTING_DEFAULT_VALUE).toListWithTimeout()
        }

        delay(delay_ms)
        value = secondSettingValue

        assertThat(listDeferred.await())
            .containsAtLeast(firstSettingValue, secondSettingValue).inOrder()
    }

    private companion object {
        const val TEST_NAME = "test_system_integer_delegate"
        const val TEST_SETTING_DEFAULT_VALUE = -1
    }
}
