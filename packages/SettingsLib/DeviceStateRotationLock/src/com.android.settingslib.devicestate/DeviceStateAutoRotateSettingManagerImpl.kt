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
import android.database.ContentObserver
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_IGNORED
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_LOCKED
import android.util.IndentingPrintWriter
import android.util.Log
import android.util.SparseIntArray
import com.android.internal.R
import com.android.settingslib.devicestate.DeviceStateAutoRotateSettingManager.DeviceStateAutoRotateSettingListener
import com.android.window.flags.Flags
import java.io.PrintWriter
import java.util.concurrent.Executor

/**
 * Implementation of [DeviceStateAutoRotateSettingManager]. This implementation is a part of
 * refactoring, it should be used when [Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_REFACTOR]
 * is enabled.
 */
class DeviceStateAutoRotateSettingManagerImpl(
    context: Context,
    backgroundExecutor: Executor,
    private val secureSettings: SecureSettings,
    private val mainHandler: Handler,
    private val posturesHelper: PosturesHelper,
) : DeviceStateAutoRotateSettingManager {
    // TODO: b/397928958 rename the fields and apis from rotationLock to autoRotate.

    private val settingListeners: MutableList<DeviceStateAutoRotateSettingListener> =
        mutableListOf()
    private val fallbackPostureMap = SparseIntArray()
    private val settableDeviceState: MutableList<SettableDeviceState> = mutableListOf()

    private val autoRotateSettingValue: String
        get() = secureSettings.getStringForUser(DEVICE_STATE_ROTATION_LOCK, UserHandle.USER_CURRENT)

    init {
        loadAutoRotateDeviceStates(context)
        val contentObserver =
            object : ContentObserver(mainHandler) {
                override fun onChange(selfChange: Boolean) = notifyListeners()
            }
        backgroundExecutor.execute {
            secureSettings.registerContentObserver(
                DEVICE_STATE_ROTATION_LOCK, false, contentObserver, UserHandle.USER_CURRENT
            )
        }
    }

    override fun registerListener(settingListener: DeviceStateAutoRotateSettingListener) {
        settingListeners.add(settingListener)
    }

    override fun unregisterListener(settingListener: DeviceStateAutoRotateSettingListener) {
        if (!settingListeners.remove(settingListener)) {
            Log.w(TAG, "Attempting to unregister a listener hadn't been registered")
        }
    }

    override fun getRotationLockSetting(deviceState: Int): Int {
        val devicePosture = posturesHelper.deviceStateToPosture(deviceState)
        val serializedSetting = autoRotateSettingValue
        val autoRotateSetting = extractSettingForDevicePosture(devicePosture, serializedSetting)

        // If the setting is ignored for this posture, check the fallback posture.
        if (autoRotateSetting == DEVICE_STATE_ROTATION_LOCK_IGNORED) {
            val fallbackPosture =
                fallbackPostureMap.get(devicePosture, DEVICE_STATE_ROTATION_LOCK_IGNORED)
            return extractSettingForDevicePosture(fallbackPosture, serializedSetting)
        }

        return autoRotateSetting
    }

    override fun isRotationLocked(deviceState: Int) =
        getRotationLockSetting(deviceState) == DEVICE_STATE_ROTATION_LOCK_LOCKED

    override fun isRotationLockedForAllStates(): Boolean =
        convertSerializedSettingToMap(autoRotateSettingValue).all { (_, value) ->
            value == DEVICE_STATE_ROTATION_LOCK_LOCKED
        }

    override fun getSettableDeviceStates(): List<SettableDeviceState> = settableDeviceState

    override fun updateSetting(deviceState: Int, autoRotate: Boolean) {
        // TODO: b/350946537 - Create IPC to update the setting, and call it here.
        throw UnsupportedOperationException("API updateSetting is not implemented yet")
    }

    override fun dump(writer: PrintWriter, args: Array<out String>?) {
        val indentingWriter = IndentingPrintWriter(writer)
        indentingWriter.println("DeviceStateAutoRotateSettingManagerImpl")
        indentingWriter.increaseIndent()
        indentingWriter.println("fallbackPostureMap: $fallbackPostureMap")
        indentingWriter.println("settableDeviceState: $settableDeviceState")
        indentingWriter.decreaseIndent()
    }

    private fun notifyListeners() =
        settingListeners.forEach { listener -> listener.onSettingsChanged() }

    private fun loadAutoRotateDeviceStates(context: Context) {
        val perDeviceStateAutoRotateDefaults =
            context.resources.getStringArray(R.array.config_perDeviceStateRotationLockDefaults)
        for (entry in perDeviceStateAutoRotateDefaults) {
            entry.parsePostureEntry()?.let { (posture, autoRotate, fallbackPosture) ->
                if (autoRotate == DEVICE_STATE_ROTATION_LOCK_IGNORED && fallbackPosture != null) {
                    fallbackPostureMap.put(posture, fallbackPosture)
                }
                settableDeviceState.add(
                    SettableDeviceState(posture, autoRotate != DEVICE_STATE_ROTATION_LOCK_IGNORED)
                )
            }
        }
    }

    private fun convertSerializedSettingToMap(serializedSetting: String): Map<Int, Int> {
        if (serializedSetting.isEmpty()) return emptyMap()
        return try {
            serializedSetting
                .split(SEPARATOR_REGEX)
                .hasEvenSize()
                .chunked(2)
                .mapNotNull(::parsePostureSettingPair)
                .toMap()
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Invalid format in serializedSetting=$serializedSetting: ${e.message}"
            )
            return emptyMap()
        }
    }

    private fun List<String>.hasEvenSize(): List<String> {
        if (this.size % 2 != 0) {
            throw IllegalStateException("Odd number of elements in the list")
        }
        return this
    }

    private fun parsePostureSettingPair(settingPair: List<String>): Pair<Int, Int>? {
        return settingPair.let { (keyStr, valueStr) ->
            val key = keyStr.toIntOrNull()
            val value = valueStr.toIntOrNull()
            if (key != null && value != null && value in 0..2) {
                key to value
            } else {
                Log.w(TAG, "Invalid key or value in pair: $keyStr, $valueStr")
                null // Invalid pair, skip it
            }
        }
    }

    private fun extractSettingForDevicePosture(
        devicePosture: Int,
        serializedSetting: String
    ): Int =
        convertSerializedSettingToMap(serializedSetting)[devicePosture]
            ?: DEVICE_STATE_ROTATION_LOCK_IGNORED

    private fun String.parsePostureEntry(): Triple<Int, Int, Int?>? {
        val values = split(SEPARATOR_REGEX)
        if (values.size !in 2..3) { // It should contain 2 or 3 values.
            Log.w(TAG, "Invalid number of values in entry: '$this'")
            return null
        }
        return try {
            val posture = values[0].toInt()
            val rotationLockSetting = values[1].toInt()
            val fallbackPosture = if (values.size == 3) values[2].toIntOrNull() else null
            Triple(posture, rotationLockSetting, fallbackPosture)
        } catch (e: NumberFormatException) {
            Log.w(TAG, "Invalid number format in '$this': ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "DeviceStateAutoRotate"
        private const val SEPARATOR_REGEX = ":"
    }
}
