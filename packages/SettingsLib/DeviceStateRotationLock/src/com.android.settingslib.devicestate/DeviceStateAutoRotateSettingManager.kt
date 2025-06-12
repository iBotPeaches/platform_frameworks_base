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

import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK
import android.util.Dumpable

/**
 * Interface for managing [DEVICE_STATE_ROTATION_LOCK] setting.
 *
 * It provides methods to register/unregister listeners for setting changes, update the setting for
 * specific device states, retrieve the setting value, and check if rotation is locked for specific
 * or all device states.
 */
interface DeviceStateAutoRotateSettingManager : Dumpable {
    // TODO: b/397928958 - Rename all terms from rotationLock to autoRotate in all apis.

    /** Listener for changes in device-state based auto rotate setting. */
    interface DeviceStateAutoRotateSettingListener {
        /** Called whenever the setting has changed. */
        fun onSettingsChanged()
    }

    /** Register listener for changes to [DEVICE_STATE_ROTATION_LOCK] setting. */
    fun registerListener(settingListener: DeviceStateAutoRotateSettingListener)

    /** Unregister listener for changes to [DEVICE_STATE_ROTATION_LOCK] setting. */
    fun unregisterListener(settingListener: DeviceStateAutoRotateSettingListener)

    /**
     * Write [deviceState]'s setting value as [autoRotate], for [DEVICE_STATE_ROTATION_LOCK] setting.
     */
    fun updateSetting(deviceState: Int, autoRotate: Boolean)

    /** Get [DEVICE_STATE_ROTATION_LOCK] setting value for [deviceState]. */
    fun getRotationLockSetting(deviceState: Int): Int

    /** Returns true if auto-rotate setting is OFF for [deviceState]. */
    fun isRotationLocked(deviceState: Int): Boolean

    /** Returns true if the auto-rotate setting value for all device states is OFF. */
    fun isRotationLockedForAllStates(): Boolean

    /** Returns a list of device states and their respective auto rotate setting availability. */
    fun getSettableDeviceStates(): List<SettableDeviceState>
}

/** Represents a device state and whether it has an auto-rotation setting. */
data class SettableDeviceState(
    /** Returns the device state associated with this object. */
    val deviceState: Int,
    /** Returns whether there is an auto-rotation setting for this device state. */
    val isSettable: Boolean
)
