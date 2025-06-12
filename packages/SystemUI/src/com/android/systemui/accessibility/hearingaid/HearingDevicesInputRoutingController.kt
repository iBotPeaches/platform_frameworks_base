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

package com.android.systemui.accessibility.hearingaid

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.collection.ArraySet
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.bluetooth.HapClientProfile
import com.android.settingslib.bluetooth.HearingAidAudioRoutingConstants
import com.android.settingslib.bluetooth.HearingAidAudioRoutingHelper
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.res.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The controller of the hearing device input routing.
 *
 * <p> It manages and update the input routing according to the value.
 */
open class HearingDevicesInputRoutingController
@AssistedInject
constructor(
    @Assisted context: Context,
    private val audioManager: AudioManager,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) {
    private val audioRoutingHelper = HearingAidAudioRoutingHelper(context)
    private var cachedDevice: CachedBluetoothDevice? = null
    private val bgCoroutineScope = CoroutineScope(backgroundDispatcher)

    /** Factory to create a [HearingDevicesInputRoutingController] instance. */
    @AssistedFactory
    interface Factory {
        fun create(context: Context): HearingDevicesInputRoutingController
    }

    /** Possible input routing UI. Need to align with [getInputRoutingOptions] */
    enum class InputRoutingValue {
        HEARING_DEVICE,
        BUILTIN_MIC,
    }

    companion object {
        private const val TAG = "HearingDevicesInputRoutingController"

        /** Gets input routing options as strings. */
        @JvmStatic
        fun getInputRoutingOptions(context: Context): Array<String> {
            return context.resources.getStringArray(R.array.hearing_device_input_routing_options)
        }
    }

    fun interface InputRoutingControlAvailableCallback {
        fun onResult(available: Boolean)
    }

    /**
     * Sets the device for this controller to control the input routing.
     *
     * @param device the [CachedBluetoothDevice] set to the controller
     */
    fun setDevice(device: CachedBluetoothDevice?) {
        this@HearingDevicesInputRoutingController.cachedDevice = device
    }

    fun isInputRoutingControlAvailable(callback: InputRoutingControlAvailableCallback) {
        bgCoroutineScope.launch {
            val result = isInputRoutingControlAvailableInternal()
            callback.onResult(result)
        }
    }

    /**
     * Checks if input routing control is available for the currently set device.
     *
     * @return `true` if input routing control is available.
     */
    private suspend fun isInputRoutingControlAvailableInternal(): Boolean {
        val device = cachedDevice ?: return false

        val memberDevices = device.memberDevice

        val inputInfos =
            withContext(backgroundDispatcher) {
                audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            }
        val supportedInputDeviceAddresses = ArraySet<String>()
        supportedInputDeviceAddresses.add(device.address)
        if (memberDevices.isNotEmpty()) {
            memberDevices.forEach { supportedInputDeviceAddresses.add(it.address) }
        }

        val isValidInputDevice =
            inputInfos.any { supportedInputDeviceAddresses.contains(it.address) }
        // Not support ASHA hearing device for input routing feature
        val isHapHearingDevice = device.profiles.any { profile -> profile is HapClientProfile }

        if (isHapHearingDevice && !isValidInputDevice) {
            Log.d(TAG, "Not supported input type hearing device.")
        }
        return isHapHearingDevice && isValidInputDevice
    }

    /** Gets the user's preferred [InputRoutingValue]. */
    fun getUserPreferredInputRoutingValue(): Int {
        val device = cachedDevice ?: return InputRoutingValue.HEARING_DEVICE.ordinal

        return if (device.device.isMicrophonePreferredForCalls) {
            InputRoutingValue.HEARING_DEVICE.ordinal
        } else {
            InputRoutingValue.BUILTIN_MIC.ordinal
        }
    }

    /**
     * Sets the input routing to [android.bluetooth.BluetoothDevice.setMicrophonePreferredForCalls]
     * based on the input routing index.
     *
     * @param inputRoutingIndex The desired input routing index.
     */
    fun selectInputRouting(inputRoutingIndex: Int) {
        val device = cachedDevice ?: return

        val useBuiltinMic = (inputRoutingIndex == InputRoutingValue.BUILTIN_MIC.ordinal)
        val status =
            audioRoutingHelper.setPreferredInputDeviceForCalls(
                device,
                if (useBuiltinMic) HearingAidAudioRoutingConstants.RoutingValue.BUILTIN_DEVICE
                else HearingAidAudioRoutingConstants.RoutingValue.AUTO,
            )
        if (!status) {
            Log.d(TAG, "Fail to configure setPreferredInputDeviceForCalls")
        }
        setMicrophonePreferredForCallsForDeviceSet(device, !useBuiltinMic)
    }

    private fun setMicrophonePreferredForCallsForDeviceSet(
        device: CachedBluetoothDevice?,
        enabled: Boolean,
    ) {
        device ?: return
        device.device.isMicrophonePreferredForCalls = enabled
        val memberDevices = device.memberDevice
        if (memberDevices.isNotEmpty()) {
            memberDevices.forEach { d -> d.device.isMicrophonePreferredForCalls = enabled }
        }
    }
}
