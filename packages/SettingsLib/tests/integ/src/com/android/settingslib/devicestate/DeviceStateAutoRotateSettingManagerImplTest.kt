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

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.hardware.devicestate.DeviceStateManager
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_FOLDED
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_HALF_FOLDED
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_KEY_UNFOLDED
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_IGNORED
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_LOCKED
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_UNLOCKED
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.settingslib.devicestate.DeviceStateAutoRotateSettingManager.DeviceStateAutoRotateSettingListener
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import java.util.concurrent.Executor
import org.mockito.Mockito.`when` as whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceStateAutoRotateSettingManagerImplTest {
    @get:Rule
    val rule = MockitoJUnit.rule()

    private val fakeSecureSettings = FakeSecureSettings()
    private val executor: Executor = Executor { it.run() }
    private val configPerDeviceStateRotationLockDefaults = arrayOf(
        "$DEVICE_STATE_ROTATION_KEY_HALF_FOLDED:" +
                "$DEVICE_STATE_ROTATION_LOCK_IGNORED:" +
                "$DEVICE_STATE_ROTATION_KEY_UNFOLDED",
        "$DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY:" +
                "$DEVICE_STATE_ROTATION_LOCK_IGNORED:" +
                "$DEVICE_STATE_ROTATION_KEY_UNFOLDED",
        "$DEVICE_STATE_ROTATION_KEY_UNFOLDED:$DEVICE_STATE_ROTATION_LOCK_LOCKED",
        "$DEVICE_STATE_ROTATION_KEY_FOLDED:$DEVICE_STATE_ROTATION_LOCK_LOCKED",
    )

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockContentResolver: ContentResolver

    @Mock
    private lateinit var mockPosturesHelper: PosturesHelper

    @Mock
    private lateinit var mockHandler: Handler

    @Mock
    private lateinit var mockDeviceStateManager: DeviceStateManager

    @Mock
    private lateinit var mockResources: Resources
    private lateinit var settingManager: DeviceStateAutoRotateSettingManagerImpl

    @Before
    fun setUp() {
        whenever(mockContext.contentResolver).thenReturn(mockContentResolver)
        whenever(mockContext.resources).thenReturn(mockResources)
        whenever(mockResources.getStringArray(R.array.config_perDeviceStateRotationLockDefaults))
            .thenReturn(configPerDeviceStateRotationLockDefaults)
        whenever(mockHandler.post(any(Runnable::class.java))).thenAnswer { invocation ->
            val runnable = invocation.arguments[0] as Runnable
            runnable.run()
            null
        }
        whenever(mockContext.getSystemService(DeviceStateManager::class.java))
            .thenReturn(mockDeviceStateManager)
        whenever(mockPosturesHelper.deviceStateToPosture(DEVICE_STATE_UNFOLDED))
            .thenReturn(DEVICE_STATE_ROTATION_KEY_UNFOLDED)
        whenever(mockPosturesHelper.deviceStateToPosture(DEVICE_STATE_FOLDED))
            .thenReturn(DEVICE_STATE_ROTATION_KEY_FOLDED)
        whenever(mockPosturesHelper.deviceStateToPosture(DEVICE_STATE_HALF_FOLDED))
            .thenReturn(DEVICE_STATE_ROTATION_KEY_HALF_FOLDED)
        whenever(mockPosturesHelper.deviceStateToPosture(DEVICE_STATE_INVALID))
            .thenReturn(DEVICE_STATE_ROTATION_LOCK_IGNORED)
        whenever(mockPosturesHelper.deviceStateToPosture(DEVICE_STATE_REAR_DISPLAY))
            .thenReturn(DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY)

        settingManager =
            DeviceStateAutoRotateSettingManagerImpl(
                mockContext,
                executor,
                fakeSecureSettings,
                mockHandler,
                mockPosturesHelper,
            )
    }

    @Test
    fun registerListener_onSettingsChanged_listenerNotified() {
        val listener = mock(DeviceStateAutoRotateSettingListener::class.java)
        settingManager.registerListener(listener)

        persistSettings(DEVICE_STATE_ROTATION_KEY_UNFOLDED, DEVICE_STATE_ROTATION_LOCK_LOCKED)

        verify(listener).onSettingsChanged()
    }

    @Test
    fun registerMultipleListeners_onSettingsChanged_allListenersNotified() {
        val listener1 = mock(DeviceStateAutoRotateSettingListener::class.java)
        val listener2 = mock(DeviceStateAutoRotateSettingListener::class.java)
        settingManager.registerListener(listener1)
        settingManager.registerListener(listener2)

        persistSettings(DEVICE_STATE_ROTATION_KEY_UNFOLDED, DEVICE_STATE_ROTATION_LOCK_LOCKED)

        verify(listener1).onSettingsChanged()
        verify(listener2).onSettingsChanged()
    }

    @Test
    fun unregisterListener_onSettingsChanged_listenerNotNotified() {
        val listener = mock(DeviceStateAutoRotateSettingListener::class.java)
        settingManager.registerListener(listener)
        settingManager.unregisterListener(listener)

        persistSettings(DEVICE_STATE_ROTATION_KEY_UNFOLDED, DEVICE_STATE_ROTATION_LOCK_LOCKED)

        verify(listener, never()).onSettingsChanged()
    }

    @Test
    fun getAutoRotateSetting_offForUnfolded_returnsOff() {
        persistSettings(DEVICE_STATE_ROTATION_KEY_UNFOLDED, DEVICE_STATE_ROTATION_LOCK_LOCKED)

        val autoRotateSetting = settingManager.getRotationLockSetting(DEVICE_STATE_UNFOLDED)

        assertThat(autoRotateSetting).isEqualTo(DEVICE_STATE_ROTATION_LOCK_LOCKED)
    }

    @Test
    fun getAutoRotateSetting_onForFolded_returnsOn() {
        persistSettings(DEVICE_STATE_ROTATION_KEY_FOLDED, DEVICE_STATE_ROTATION_LOCK_UNLOCKED)

        val autoRotateSetting = settingManager.getRotationLockSetting(DEVICE_STATE_FOLDED)

        assertThat(autoRotateSetting).isEqualTo(DEVICE_STATE_ROTATION_LOCK_UNLOCKED)
    }

    @Test
    fun getAutoRotateSetting_forInvalidPostureWithNoFallback_returnsIgnored() {
        val autoRotateSetting = settingManager.getRotationLockSetting(DEVICE_STATE_INVALID)

        assertThat(autoRotateSetting).isEqualTo(DEVICE_STATE_ROTATION_LOCK_IGNORED)
    }

    @Test
    fun getAutoRotateSetting_forInvalidPosture_returnsSettingForFallbackPosture() {
        persistSettings(
            "$DEVICE_STATE_ROTATION_KEY_FOLDED:$DEVICE_STATE_ROTATION_LOCK_LOCKED:" +
                    "$DEVICE_STATE_ROTATION_KEY_UNFOLDED:$DEVICE_STATE_ROTATION_LOCK_UNLOCKED"
        )

        val autoRotateSetting = settingManager.getRotationLockSetting(DEVICE_STATE_HALF_FOLDED)

        assertThat(autoRotateSetting).isEqualTo(DEVICE_STATE_ROTATION_LOCK_UNLOCKED)
    }

    @Test
    fun getAutoRotateSetting_invalidFormat_returnsIgnored() {
        persistSettings("invalid_format")

        val autoRotateSetting = settingManager.getRotationLockSetting(DEVICE_STATE_FOLDED)

        assertThat(autoRotateSetting).isEqualTo(DEVICE_STATE_ROTATION_LOCK_IGNORED)
    }

    @Test
    fun getAutoRotateSetting_invalidNumberFormat_returnsIgnored() {
        persistSettings("$DEVICE_STATE_ROTATION_KEY_FOLDED:4")

        val autoRotateSetting = settingManager.getRotationLockSetting(DEVICE_STATE_FOLDED)

        assertThat(autoRotateSetting).isEqualTo(DEVICE_STATE_ROTATION_LOCK_IGNORED)
    }

    @Test
    fun getAutoRotateSetting_multipleSettings_returnsCorrectSetting() {
        persistSettings(
            "$DEVICE_STATE_ROTATION_KEY_FOLDED:$DEVICE_STATE_ROTATION_LOCK_LOCKED:" +
                    "$DEVICE_STATE_ROTATION_KEY_UNFOLDED:$DEVICE_STATE_ROTATION_LOCK_UNLOCKED"
        )

        val foldedSetting = settingManager.getRotationLockSetting(DEVICE_STATE_FOLDED)
        val unfoldedSetting = settingManager.getRotationLockSetting(DEVICE_STATE_UNFOLDED)

        assertThat(foldedSetting).isEqualTo(DEVICE_STATE_ROTATION_LOCK_LOCKED)
        assertThat(unfoldedSetting).isEqualTo(DEVICE_STATE_ROTATION_LOCK_UNLOCKED)
    }

    @Test
    fun isAutoRotateOff_offForUnfolded_returnsTrue() {
        persistSettings(DEVICE_STATE_ROTATION_KEY_UNFOLDED, DEVICE_STATE_ROTATION_LOCK_LOCKED)

        val isAutoRotateOff = settingManager.isRotationLocked(DEVICE_STATE_UNFOLDED)

        assertThat(isAutoRotateOff).isTrue()
    }

    @Test
    fun isRotationLockedForAllStates_allStatesLocked_returnsTrue() {
        persistSettings(
            "$DEVICE_STATE_ROTATION_KEY_FOLDED:$DEVICE_STATE_ROTATION_LOCK_LOCKED:" +
                    "$DEVICE_STATE_ROTATION_KEY_UNFOLDED:$DEVICE_STATE_ROTATION_LOCK_LOCKED"
        )

        val isRotationLockedForAllStates = settingManager.isRotationLockedForAllStates()

        assertThat(isRotationLockedForAllStates).isTrue()
    }

    @Test
    fun isRotationLockedForAllStates_someStatesLocked_returnsFalse() {
        persistSettings(
            "$DEVICE_STATE_ROTATION_KEY_FOLDED:$DEVICE_STATE_ROTATION_LOCK_UNLOCKED:" +
                    "$DEVICE_STATE_ROTATION_KEY_UNFOLDED:$DEVICE_STATE_ROTATION_LOCK_LOCKED"
        )

        val isRotationLockedForAllStates = settingManager.isRotationLockedForAllStates()

        assertThat(isRotationLockedForAllStates).isFalse()
    }

    @Test
    fun isRotationLockedForAllStates_noStatesLocked_returnsFalse() {
        persistSettings(
            "$DEVICE_STATE_ROTATION_KEY_FOLDED:$DEVICE_STATE_ROTATION_LOCK_UNLOCKED:" +
                    "$DEVICE_STATE_ROTATION_KEY_UNFOLDED:$DEVICE_STATE_ROTATION_LOCK_UNLOCKED"
        )

        val isRotationLockedForAllStates = settingManager.isRotationLockedForAllStates()

        assertThat(isRotationLockedForAllStates).isFalse()
    }

    @Test
    fun getSettableDeviceStates_returnsExpectedValuesInOriginalOrder() {
        val settableDeviceStates = settingManager.getSettableDeviceStates()

        assertThat(settableDeviceStates)
            .containsExactly(
                SettableDeviceState(DEVICE_STATE_ROTATION_KEY_UNFOLDED, isSettable = true),
                SettableDeviceState(DEVICE_STATE_ROTATION_KEY_FOLDED, isSettable = true),
                SettableDeviceState(DEVICE_STATE_ROTATION_KEY_HALF_FOLDED, isSettable = false),
                SettableDeviceState(DEVICE_STATE_ROTATION_KEY_REAR_DISPLAY, isSettable = false),
            )
    }

    private fun persistSettings(devicePosture: Int, autoRotateSetting: Int) {
        persistSettings("$devicePosture:$autoRotateSetting")
    }

    private fun persistSettings(value: String) {
        fakeSecureSettings.putStringForUser(
            Settings.Secure.DEVICE_STATE_ROTATION_LOCK, value, UserHandle.USER_CURRENT
        )
    }

    private companion object {
        const val DEVICE_STATE_FOLDED = 0
        const val DEVICE_STATE_HALF_FOLDED = 1
        const val DEVICE_STATE_UNFOLDED = 2
        const val DEVICE_STATE_REAR_DISPLAY = 3
        const val DEVICE_STATE_INVALID = 4
    }
}
