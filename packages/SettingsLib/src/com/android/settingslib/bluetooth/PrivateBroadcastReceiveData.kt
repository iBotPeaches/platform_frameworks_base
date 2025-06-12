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

package com.android.settingslib.bluetooth

import android.bluetooth.BluetoothDevice
import android.os.Parcel
import android.os.Parcelable
import com.android.settingslib.bluetooth.LocalBluetoothLeBroadcastAssistant.LocalBluetoothLeBroadcastSourceState
import com.android.settingslib.bluetooth.LocalBluetoothLeBroadcastAssistant.LocalBluetoothLeBroadcastSourceState.PAUSED
import com.android.settingslib.bluetooth.LocalBluetoothLeBroadcastAssistant.LocalBluetoothLeBroadcastSourceState.STREAMING
import com.android.settingslib.bluetooth.LocalBluetoothLeBroadcastAssistant.LocalBluetoothLeBroadcastSourceState.DECRYPTION_FAILED

/**
 * Data class representing information received in a private broadcast.
 * This class encapsulates details about the sink device, source ID, broadcast ID, and the
 * broadcast source state.
 *
 * @param sink The [BluetoothDevice] acting as the sink.
 * @param sourceId The ID of the audio source.
 * @param broadcastId The ID of the broadcast source.
 * @param programInfo The program info string of the broadcast source.
 * @param state The current state of the broadcast source.
 */
data class PrivateBroadcastReceiveData(
    val sink: BluetoothDevice?,
    val sourceId: Int = -1,
    val broadcastId: Int = -1,
    val programInfo: String = "",
    val state: LocalBluetoothLeBroadcastSourceState?,
) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(sink, flags)
        parcel.writeInt(sourceId)
        parcel.writeInt(broadcastId)
        parcel.writeString(programInfo)
        parcel.writeSerializable(state)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<PrivateBroadcastReceiveData> =
            object : Parcelable.Creator<PrivateBroadcastReceiveData> {
                override fun createFromParcel(parcel: Parcel) =
                    parcel.run {
                        PrivateBroadcastReceiveData(
                            sink = readParcelable(
                                BluetoothDevice::class.java.classLoader,
                                BluetoothDevice::class.java
                            ),
                            sourceId = readInt(),
                            broadcastId = readInt(),
                            programInfo = readString() ?: "",
                            state = readSerializable(
                                LocalBluetoothLeBroadcastSourceState::class.java.classLoader,
                                LocalBluetoothLeBroadcastSourceState::class.java
                            )
                        )
                    }
                override fun newArray(size: Int): Array<PrivateBroadcastReceiveData?> {
                    return arrayOfNulls(size)
                }
            }

        fun PrivateBroadcastReceiveData.isValid(): Boolean {
            return sink != null
                    && sourceId != -1
                    && broadcastId != -1
                    && (state == STREAMING
                    || state == PAUSED
                    || state == DECRYPTION_FAILED)
        }
    }
}
