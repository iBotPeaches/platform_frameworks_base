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

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Parcel
import com.android.settingslib.bluetooth.LocalBluetoothLeBroadcastAssistant.LocalBluetoothLeBroadcastSourceState
import com.android.settingslib.bluetooth.PrivateBroadcastReceiveData.Companion.isValid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrivateBroadcastReceiveDataTest {

    @Test
    fun parcelable() {
        val original = PrivateBroadcastReceiveData(
            sink = sink,
            sourceId = 1,
            broadcastId = 2,
            programInfo = "Test Program",
            state = LocalBluetoothLeBroadcastSourceState.STREAMING
        )

        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val recreated = PrivateBroadcastReceiveData.CREATOR.createFromParcel(parcel)

        assertEquals(original, recreated)
    }

    @Test
    fun isValid_validData() {
        val data = PrivateBroadcastReceiveData(
            sink = sink,
            sourceId = 1,
            broadcastId = 2,
            state = LocalBluetoothLeBroadcastSourceState.STREAMING
        )
        assertTrue(data.isValid())
    }

    @Test
    fun isValid_nullSink() {
        val data = PrivateBroadcastReceiveData(
            sink = null,
            sourceId = 1,
            broadcastId = 2,
            state = LocalBluetoothLeBroadcastSourceState.STREAMING
        )
        assertFalse(data.isValid())
    }

    @Test
    fun isValid_invalidSourceId() {
        val data = PrivateBroadcastReceiveData(
            sink = sink,
            sourceId = -1,
            broadcastId = 2,
            state = LocalBluetoothLeBroadcastSourceState.STREAMING
        )
        assertFalse(data.isValid())
    }

    @Test
    fun isValid_invalidBroadcastId() {
        val data = PrivateBroadcastReceiveData(
            sink = sink,
            sourceId = 1,
            broadcastId = -1,
            state = LocalBluetoothLeBroadcastSourceState.STREAMING
        )
        assertFalse(data.isValid())
    }

    @Test
    fun isValid_nullState() {
        val data = PrivateBroadcastReceiveData(
            sink = sink,
            sourceId = 1,
            broadcastId = 2,
            state = null
        )
        assertFalse(data.isValid())
    }

    @Test
    fun isValid_correctStates() {
        assertTrue(PrivateBroadcastReceiveData(sink, 1, 1, state = LocalBluetoothLeBroadcastSourceState.STREAMING).isValid())
        assertTrue(PrivateBroadcastReceiveData(sink, 1, 1, state = LocalBluetoothLeBroadcastSourceState.PAUSED).isValid())
        assertTrue(PrivateBroadcastReceiveData(sink, 1, 1, state = LocalBluetoothLeBroadcastSourceState.DECRYPTION_FAILED).isValid())
    }

    private companion object {
        const val TEST_DEVICE_ADDRESS = "00:A1:A1:A1:A1:A1"

        val sink: BluetoothDevice =
            BluetoothAdapter.getDefaultAdapter().getRemoteLeDevice(
                TEST_DEVICE_ADDRESS,
                BluetoothDevice.ADDRESS_TYPE_RANDOM
            )
    }
}
