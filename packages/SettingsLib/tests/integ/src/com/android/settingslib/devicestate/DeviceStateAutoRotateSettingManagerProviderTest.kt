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

package com.android.settingslib.devicestate

import android.content.Context
import android.os.Handler
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import java.util.concurrent.Executor
import org.mockito.Mockito.`when` as whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceStateAutoRotateSettingManagerProviderTest {

    @get:Rule
    val setFlagsRule: SetFlagsRule = SetFlagsRule()

    @get:Rule val rule = MockitoJUnit.rule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Mock
    private lateinit var mockExecutor: Executor

    @Mock
    private lateinit var mockSecureSettings: SecureSettings

    @Mock
    private lateinit var mockMainHandler: Handler

    @Mock
    private lateinit var mockPosturesHelper: PosturesHelper

    @Before
    fun setup() {
        whenever(mockSecureSettings.getStringForUser(any(), anyInt())).thenReturn("")
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_REFACTOR)
    fun createInstance_refactorFlagEnabled_returnsRefactoredManager() {
        val manager =
            DeviceStateAutoRotateSettingManagerProvider.createInstance(
                context, mockExecutor, mockSecureSettings, mockMainHandler, mockPosturesHelper
            )

        assertThat(manager).isInstanceOf(DeviceStateAutoRotateSettingManagerImpl::class.java)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_REFACTOR)
    fun createInstance_refactorFlagDisabled_returnsLegacyManager() {
        val manager =
            DeviceStateAutoRotateSettingManagerProvider.createInstance(
                context, mockExecutor, mockSecureSettings, mockMainHandler, mockPosturesHelper
            )

        assertThat(manager).isInstanceOf(DeviceStateRotationLockSettingsManager::class.java)
    }
}
